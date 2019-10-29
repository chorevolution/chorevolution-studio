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
package eu.chorevolution.studio.eclipse.wizard;

import org.eclipse.osgi.util.NLS;

public final class ChorevolutionWizardMessages extends NLS {

	private static final String BUNDLE_NAME = ChorevolutionWizardPlugin.PLUGIN_ID + ".ChorevolutionWizardMessages";

	private ChorevolutionWizardMessages() {
		// Do not instantiate
	}

	public static String Project_errorMessage;
	public static String Project_createError;
	public static String ProjectNature_errorMessage;
	public static String ProjectNature_addError;
	public static String ProjectStructure_errorMessage;
	public static String ProjectStructure_createError;
	
	public static String BPMN2DiagramWizard_errorMessage;
	public static String BPMN2DiagramWizard_createError;
	

	static {
		NLS.initializeMessages(BUNDLE_NAME, ChorevolutionWizardMessages.class);
	}
}
