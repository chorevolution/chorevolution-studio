/*
  * Copyright 2015 The CHOReVOLUTION project
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package eu.chorevolution.studio.eclipse.ui.handlers.synthesisprocessor.rest;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import eu.chorevolution.modelingnotations.chorarch.ChorArchModel;
import eu.chorevolution.modelingnotations.chorarch.ConsumerCoordinationDelegate;
import eu.chorevolution.studio.eclipse.core.ChorevolutionCoreUtils;
import eu.chorevolution.studio.eclipse.core.preferences.ChorevolutionServicesURIPrefs;
import eu.chorevolution.studio.eclipse.core.preferences.ChorevolutionSynthesisSourceModelPrefs;
import eu.chorevolution.studio.eclipse.ui.ChorevolutionUIMessages;
import eu.chorevolution.studio.eclipse.ui.ChorevolutionUIPlugin;
import eu.chorevolution.studio.eclipse.ui.handlers.synthesisprocessor.ChoreographyDeploymentDescriptorGenerator;
import eu.chorevolution.synthesisprocessor.rest.api.ChoreographyDeploymentDescriptorGeneratorRequest;
import eu.chorevolution.synthesisprocessor.rest.api.ChoreographyDeploymentDescriptorGeneratorResponse;
import eu.chorevolution.synthesisprocessor.rest.api.client.ChoreographyDeploymentDescriptorGeneratorClient;
import eu.chorevolution.synthesisprocessor.rest.api.client.SynthesisProcessorClient;

public class ChoreographyDeploymentDescriptorGeneratorRest extends ChoreographyDeploymentDescriptorGenerator {
	public static final String WAR_EXTENSION = "war";
	
	private String choreographyname;
	private IFile choreographyArchitectureFile;
	private ChorArchModel choreographyArchitectureModel;
	private List<ConsumerCoordinationDelegate> consumerCoordinationDelegates;
	private IProgressMonitor monitor;
	private ChoreographyDeploymentDescriptorGeneratorClient client;
	private SynthesisProcessorClient synthesisProcessorClient;

	public ChoreographyDeploymentDescriptorGeneratorRest(String choreographyname, IProject project, IFile choreographyArchitectureFile,
			IProgressMonitor monitor) {
		super(project);
		this.choreographyname = choreographyname;
		this.choreographyArchitectureFile = choreographyArchitectureFile;
		this.monitor = monitor;
		this.client = new ChoreographyDeploymentDescriptorGeneratorClient(super.getPropertyValues()
				.get(ChorevolutionServicesURIPrefs.PREF_SYNTHESIS_PROCESSOR_CHOREOGRAPHY_DEPLOYMENT_DESCRIPTION_GENERATOR_URI)
				.getValue());

		
		
		//executes login
		SynthesisProcessorAccessControlRest spacr= new SynthesisProcessorAccessControlRest(project);

		try {
			if(!spacr.executeLogin()) {
				MessageDialog.openError(ChorevolutionUIPlugin.getActiveWorkbenchShell(),
						"Login incorrect",
						"The username or the password of the Synthesis Processor are incorrect");
				return;			
			}
		} catch (Exception e) {
			MessageDialog.openError(ChorevolutionUIPlugin.getActiveWorkbenchShell(),
					"Unable to do Login",
					e.toString());
		}
		
		
		// TODO improve this lines
		String uriCdGenerator = super.getPropertyValues()
				.get(ChorevolutionServicesURIPrefs.PREF_SYNTHESIS_PROCESSOR_CHOREOGRAPHY_DEPLOYMENT_DESCRIPTION_GENERATOR_URI).getValue();
		this.synthesisProcessorClient = new SynthesisProcessorClient(
				uriCdGenerator.replace("choreographydeploymentdescriptorgenerator/", ""));

		// load choreography architecture
		URI choreographyArchitectureURI = URI
				.createPlatformResourceURI(choreographyArchitectureFile.getFullPath().toString(), true);
		this.choreographyArchitectureModel = ChorevolutionCoreUtils
				.loadChoreographyArchitectureModel(choreographyArchitectureURI);

		// find consumer component and for each consumer compile project
		this.consumerCoordinationDelegates = new ArrayList<ConsumerCoordinationDelegate>();
		this.choreographyArchitectureModel.getComponents().forEach(item -> {
			if (item instanceof ConsumerCoordinationDelegate) {
				this.consumerCoordinationDelegates.add((ConsumerCoordinationDelegate) item);
			}

		});

	}

	@Override
	public void storeChoreographyDeploymentDescriptor() throws Exception {
		
		monitor.beginTask("Choreography Deployment Descriptor Generator", consumerCoordinationDelegates.size() + 1);
		for (ConsumerCoordinationDelegate consumerCoordinationDelegate : consumerCoordinationDelegates) {
			if (monitor.isCanceled()) {
				return;
			}

			monitor.subTask("Compile Prosumer Project: " + consumerCoordinationDelegate.getName());
			
			//if is already an http path, i don't call the compileconsumercreatedproject
			if(!ChorevolutionCoreUtils.isValidURL(consumerCoordinationDelegate.getLocation()))
				compileConsumerCreatedProject(consumerCoordinationDelegate, monitor);
			monitor.worked(1);
		}

		monitor.subTask("Generate the Choreography Deployment Descriptor");
		generateChoreographyDeploymentDescriptor();
		monitor.worked(1);
	}

	private void compileConsumerCreatedProject(ConsumerCoordinationDelegate consumerCoordinationDelegate,
			IProgressMonitor monitor) throws Exception {
		// compile the project
		IFile warIFile = ChorevolutionCoreUtils.executeMavenGoal(
				ChorevolutionUIPlugin.getWorkspace().getRoot().getProject(consumerCoordinationDelegate.getName()),
				monitor);
		File warFile = warIFile.getRawLocation().makeAbsolute().toFile();
		// upload war to synthesis processor rest
		if(!warFile.exists()){
			throw new Exception(NLS.bind(ChorevolutionUIMessages.Transformator_chorarch2choreospecErro_compile_project,consumerCoordinationDelegate.getName()));
		}
		
		String location = synthesisProcessorClient.upload(choreographyname, "prosumer",
				consumerCoordinationDelegate.getName() + "." + WAR_EXTENSION,
				new ByteArrayInputStream(FileUtils.readFileToByteArray(warFile)));

		// save the new location
		consumerCoordinationDelegate.setLocation(location);
	}

	private void generateChoreographyDeploymentDescriptor() throws Exception {
		
		//save the changed choreography architecture
		ChorevolutionCoreUtils.saveEObject(choreographyArchitectureModel, choreographyArchitectureFile);
		
		// generate choreography deployment descriptor
		IFolder choreographyDeploymentDescriptorFolder = super.getProject().getFolder(super.getPropertyValues()
				.get(ChorevolutionSynthesisSourceModelPrefs.PREF_CHOREOGRAPHYDEPLOYMENT).getValue());

		ChoreographyDeploymentDescriptorGeneratorRequest choreographySpecificationGeneratorRequest = new ChoreographyDeploymentDescriptorGeneratorRequest(
				FileUtils.readFileToByteArray(choreographyArchitectureFile.getRawLocation().makeAbsolute().toFile()), super.getPropertyValues()
				.get(ChorevolutionServicesURIPrefs.PREF_SYNTHESIS_PROCESSOR_TOKEN)
				.getValue());

		ChoreographyDeploymentDescriptorGeneratorResponse choreographySpecificationGeneratorResponse = client
				.generateChoreographyDeploymentDescriptor(choreographySpecificationGeneratorRequest);

		IFile chorDeployFile = ChorevolutionCoreUtils.createFile(
				ChorevolutionCoreUtils.changeExtension(choreographyArchitectureFile.getName(),
						CHOREOGRAPHY_DEPLOYMENT_DESCRIPTION_FILE_EXTENSION),
				choreographyDeploymentDescriptorFolder,
				choreographySpecificationGeneratorResponse.getChoreographyDeploymentDescriptorContent());
		choreographyDeploymentDescriptorFolder.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());

		//now remove the surplus slashes from the url

		try {
			File fileToRead = new File(chorDeployFile.getRawLocationURI().getPath());					

			Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new FileInputStream(fileToRead));
			
			NodeList packageURL = ((org.w3c.dom.Document) d).getElementsByTagName("packageUrl");
			for(int i=0; i<packageURL.getLength(); i++) {
				String url = packageURL.item(i).getTextContent();
				url = removeSlashOnHttp(url);
				packageURL.item(i).setTextContent(url);
			}

			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			Result output = new StreamResult(os);
			Source input = new DOMSource(d);
			
			transformer.transform(input, output);
			
			Files.write(fileToRead.toPath(), os.toByteArray());
			
		}
		catch(Exception e) {
			
		}

		
		super.getProject().refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());

	}
	
	public String removeSlashOnHttp(String initialString) {
		if(initialString.startsWith("http")) {
			if(initialString.charAt(initialString.length()-1) == '/') {
				return initialString.substring(0, initialString.length()-1);
			}
		}
	
		return initialString;
	}
	

}
