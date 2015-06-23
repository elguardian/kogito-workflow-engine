/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.jbpm.process.core.event;

import java.io.Serializable;
import java.util.Collections;

import org.jbpm.process.core.impl.DataTransformerRegistry;
import org.jbpm.workflow.core.node.Transformation;
import org.kie.api.runtime.process.DataTransformer;

public class EventTransformerImpl implements EventTransformer, Serializable {
	
	private static final long serialVersionUID = 5861307291725051774L;
	
	private Transformation transformation;
	private String name;
	
	public EventTransformerImpl(Transformation transformation) {
		if (transformation != null) {
			this.transformation = transformation;
			this.name = transformation.getSource();
			
			if (this.name == null) {
				this.name = "event";
			}
		}
	}

	@Override
	public Object transformEvent(Object event) {
		if (event == null || transformation == null) {
			return event;
		}
		DataTransformer transformer = DataTransformerRegistry.get().find(transformation.getLanguage());
    	if (transformer != null) {
    		Object parameterValue = transformer.transform(transformation.getCompiledExpression(), Collections.singletonMap(name, event));
    		return parameterValue;
    	}
		return event;
	}

}
