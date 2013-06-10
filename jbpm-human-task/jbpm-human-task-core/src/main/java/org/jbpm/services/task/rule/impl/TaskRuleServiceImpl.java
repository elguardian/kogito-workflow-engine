/*
 * Copyright 2013 JBoss by Red Hat.
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
package org.jbpm.services.task.rule.impl;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jbpm.services.task.exception.TaskException;
import org.jbpm.services.task.impl.model.UserImpl;
import org.jbpm.services.task.rule.RuleContextProvider;
import org.jbpm.services.task.rule.TaskRuleService;
import org.jbpm.services.task.rule.TaskServiceRequest;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.task.model.Task;
import org.kie.internal.task.api.model.ContentData;

@ApplicationScoped
public class TaskRuleServiceImpl implements TaskRuleService {

    @Inject
    private RuleContextProvider ruleContextProvider;
    
    @Override
    public void executeRules(Task task, String userId, ContentData contentData, String scope) throws TaskException {
        Object params = ContentMarshallerHelper.unmarshall(contentData.getContent(), null);
        executeRules(task, userId, params, scope);

    }
    
    @Override
    public void executeRules(Task task, String userId, Object params, String scope) throws TaskException {
        KieBase ruleBase = ruleContextProvider.getKieBase(scope);
        if (ruleBase != null) {
            KieSession session = ruleBase.newKieSession();
            Map<String, Object> globals = ruleContextProvider.getGlobals(scope);
            if (globals != null) {
                for (Map.Entry<String, Object> entry : globals.entrySet()) {
                    session.setGlobal(entry.getKey(), entry.getValue());
                }
            }
            TaskServiceRequest request = new TaskServiceRequest(scope, new UserImpl(userId), null);
            session.setGlobal("request", request);
            session.insert(task);
            if (params != null) {
                session.insert(params);
            }
            session.fireAllRules();

            if (!request.isAllowed()) {
                StringBuilder error = new StringBuilder("Cannot perform operation " + scope + " :\n");
                if (request.getReasons() != null) {
                    for (String reason : request.getReasons()) {
                        error.append( reason).append('\n');
                    }
                }

                throw request.getException(error.toString());
            }
        }
    }

    public RuleContextProvider getRuleContextProvider() {
        return ruleContextProvider;
    }

    public void setRuleContextProvider(RuleContextProvider ruleContextProvider) {
        this.ruleContextProvider = ruleContextProvider;
    }

}
