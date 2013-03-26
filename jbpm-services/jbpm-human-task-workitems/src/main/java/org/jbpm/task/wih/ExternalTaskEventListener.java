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
package org.jbpm.task.wih;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.event.Reception;
import org.jboss.seam.transaction.Transactional;

import org.jbpm.shared.services.impl.events.JbpmServicesEventListener;
import org.jbpm.task.annotations.External;
import org.jbpm.task.events.AfterTaskCompletedEvent;
import org.jbpm.task.events.AfterTaskFailedEvent;
import org.jbpm.task.events.AfterTaskSkippedEvent;
import org.jbpm.task.lifecycle.listeners.TaskLifeCycleEventListener;
import org.jbpm.task.utils.ContentMarshallerHelper;
import org.kie.api.runtime.KieSession;
import org.kie.internal.runtime.manager.Runtime;
import org.kie.internal.runtime.manager.RuntimeManager;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.kie.internal.task.api.model.Content;
import org.kie.internal.task.api.model.Status;
import org.kie.internal.task.api.model.Task;

/**
 *
 * @author salaboy
 */
@ApplicationScoped
@External
@Transactional
public class ExternalTaskEventListener extends JbpmServicesEventListener<Task>  implements TaskLifeCycleEventListener {

    private RuntimeManager runtimeManager;
    private Map<Integer, ClassLoader> classLoaders = new HashMap<Integer,ClassLoader>();
 
    public ExternalTaskEventListener() {
    }
    
    public void addClassLoader(Integer sessionId, ClassLoader cl) {
        this.classLoaders.put(sessionId, cl);
    }

    public RuntimeManager getRuntimeManager() {
        return runtimeManager;
    }

    public void setRuntimeManager(RuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    public void processTaskState(Task task) {

        long workItemId = task.getTaskData().getWorkItemId();
        long processInstanceId = task.getTaskData().getProcessInstanceId();
        Runtime runtime = runtimeManager.getRuntime(ProcessInstanceIdContext.get(processInstanceId));
        KieSession session = runtime.getKieSession();
        
        if (task.getTaskData().getStatus() == Status.Completed) {
            String userId = task.getTaskData().getActualOwner().getId();
            Map<String, Object> results = new HashMap<String, Object>();
            results.put("ActorId", userId);
            long contentId = task.getTaskData().getOutputContentId();
            if (contentId != -1) {
                Content content = runtime.getTaskService().getContentById(contentId);
                Object result = ContentMarshallerHelper.unmarshall(content.getContent(), session.getEnvironment(), classLoaders.get(session.getId()));
                results.put("Result", result);
                if (result instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) result;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() instanceof String) {
                            results.put((String) entry.getKey(), entry.getValue());
                        }
                    }
                }

                session.getWorkItemManager().completeWorkItem(task.getTaskData().getWorkItemId(), results);
            } else {
                session.getWorkItemManager().completeWorkItem(workItemId, results);
            }
        } else {
            session.getWorkItemManager().abortWorkItem(workItemId);
        }
    }

    public void afterTaskActivatedEvent(Task ti) {
        // DO NOTHING
    }

    public void afterTaskClaimedEvent(Task ti) {
        // DO NOTHING
    }

    public void afterTaskSkippedEvent(@Observes(notifyObserver = Reception.IF_EXISTS) @AfterTaskSkippedEvent Task task) {
        processTaskState(task);
    }

    public void afterTaskStartedEvent(Task ti) {
        // DO NOTHING
    }

    public void afterTaskStoppedEvent(Task ti) {
        // DO NOTHING
    }

    public void afterTaskCompletedEvent(@Observes(notifyObserver = Reception.IF_EXISTS) @AfterTaskCompletedEvent Task task) {
        long processInstanceId = task.getTaskData().getProcessInstanceId();
        Runtime runtime = runtimeManager.getRuntime(ProcessInstanceIdContext.get(processInstanceId));
        KieSession session = runtime.getKieSession();
        if (session != null) {
            System.out.println(">> I've recieved an event for a known session (" + task.getTaskData().getProcessSessionId()+")");
            processTaskState(task);
        } else {
            System.out.println("EE: I've recieved an event but the session is not known by this handler ( "+task.getTaskData().getProcessSessionId()+")");
        }
    }

    public void afterTaskFailedEvent(@Observes(notifyObserver = Reception.IF_EXISTS) @AfterTaskFailedEvent Task task) {
        processTaskState(task);
    }

    public void afterTaskAddedEvent(Task ti) {
        
        // DO NOTHING
    }

    public void afterTaskExitedEvent(Task ti) {
        // DO NOTHING
    }
}
