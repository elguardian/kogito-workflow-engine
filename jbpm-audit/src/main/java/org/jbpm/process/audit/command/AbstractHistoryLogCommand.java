/*
 * Copyright 2012 JBoss by Red Hat.
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
package org.jbpm.process.audit.command;

import java.util.List;

import org.drools.core.command.impl.FixedKnowledgeCommandContext;
import org.drools.core.command.impl.GenericCommand;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.kie.api.runtime.KieSession;
import org.kie.internal.command.Context;

public abstract class AbstractHistoryLogCommand<T> implements GenericCommand<T> {

    public AbstractHistoryLogCommand() {
	}
    
    protected void setLogEnvironment(Context cntxt) { 
        if( ! (cntxt instanceof FixedKnowledgeCommandContext) ) { 
            throw new UnsupportedOperationException("This command must be executed by a " + KieSession.class.getSimpleName() + " instance!");
        }
        FixedKnowledgeCommandContext realContext = (FixedKnowledgeCommandContext) cntxt;
        JPAProcessInstanceDbLog.setEnvironment(realContext.getKieSession().getEnvironment());
    }
    
}
