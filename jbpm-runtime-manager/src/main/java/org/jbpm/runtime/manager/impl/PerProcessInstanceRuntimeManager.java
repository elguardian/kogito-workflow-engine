/*
 * Copyright 2013 JBoss Inc
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
package org.jbpm.runtime.manager.impl;

import java.util.HashMap;
import java.util.Map;

import org.jbpm.runtime.manager.impl.tx.DestroySessionTransactionSynchronization;
import org.jbpm.runtime.manager.impl.tx.DisposeSessionTransactionSynchronization;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.Context;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.internal.runtime.manager.Disposable;
import org.kie.internal.runtime.manager.Mapper;
import org.kie.internal.runtime.manager.RuntimeEnvironment;
import org.kie.internal.runtime.manager.SessionFactory;
import org.kie.internal.runtime.manager.SessionNotFoundException;
import org.kie.internal.runtime.manager.TaskServiceFactory;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;

/**
 * RuntimeManager that is backed by "Per Process Instance" strategy - that means that every process instance will
 * be bound to a ksession for it's entire life time - once started whenever other operations will be invoked 
 * this manager will ensure that correct ksession will be provided.
 * <br/>
 * That applies to sub processes (reusable sub processes) that creates new process instance - sub process instance
 * will have its own ksession independent of the parent one.
 * <br/>
 * This manager will ensure that as soon as process instance completes the ksession will be disposed and destroyed.
 * <br/>
 * This implementation supports following <code>Context</code> implementations:
 * <ul>
 *  <li>ProcessInstanceIdContext</li>
 *  <li>CorrelationKeyContext</li>
 *  <li>EmptyContext - for initial RuntimeEngine to start process only</li>
 * </ul>
 */
public class PerProcessInstanceRuntimeManager extends AbstractRuntimeManager {

    private SessionFactory factory;
    private TaskServiceFactory taskServiceFactory;
    
    private static ThreadLocal<Map<Object, RuntimeEngine>> local = new ThreadLocal<Map<Object, RuntimeEngine>>();
    
    private Mapper mapper;
    
    public PerProcessInstanceRuntimeManager(RuntimeEnvironment environment, SessionFactory factory, TaskServiceFactory taskServiceFactory, String identifier) {
        super(environment, identifier);
        this.factory = factory;
        this.taskServiceFactory = taskServiceFactory;
        this.mapper = environment.getMapper();
        activeManagers.add(identifier);
    }
    
    @Override
    public RuntimeEngine getRuntimeEngine(Context<?> context) {
  
        Object contextId = context.getContextId();
        KieSession ksession = null;
        Integer ksessionId = null;
        if (contextId == null || context instanceof EmptyContext ) { 
            ksession = factory.newKieSession();
            ksessionId = ksession.getId();                 
        } else {
            RuntimeEngine localRuntime = findLocalRuntime(contextId);
            if (localRuntime != null) {
                return localRuntime;
            }
            ksessionId = mapper.findMapping(context);
            if (ksessionId == null) {
                throw new SessionNotFoundException("No session found for context " + context.getContextId());
            }
            ksession = factory.findKieSessionById(ksessionId);
        }
        
        RuntimeEngine runtime = new RuntimeEngineImpl(ksession, taskServiceFactory.newTaskService());
        ((RuntimeEngineImpl) runtime).setManager(this);
        registerDisposeCallback(runtime, new DisposeSessionTransactionSynchronization(this, runtime));
        registerItems(runtime);
        attachManager(runtime);
        
        saveLocalRuntime(contextId, runtime);
        
        ksession.addEventListener(new MaintainMappingListener(ksessionId, runtime));
        return runtime;
    }
    

    @Override
    public void validate(KieSession ksession, Context<?> context) throws IllegalStateException {
        if (context == null || context.getContextId() == null) {
            return;
        }
        Integer ksessionId = mapper.findMapping(context);
                
        if (ksessionId == null) {
            // make sure ksession is not use by any other context
            Object contextId = mapper.findContextId(ksession.getId());
            if (contextId != null) {
                throw new IllegalStateException("KieSession with id " + ksession.getId() + " is already used by another context");
            }
            return;
        }
        if (ksession.getId() != ksessionId) {
            throw new IllegalStateException("Invalid session was used for this context " + context);
        }
        
    }

    @Override
    public void disposeRuntimeEngine(RuntimeEngine runtime) {
        removeLocalRuntime(runtime);
        if (runtime instanceof Disposable) {
            ((Disposable) runtime).dispose();
        }
    }

    @Override
    public void close() {
        super.close();
        factory.close();
    }

    
    public boolean validate(Integer ksessionId, Long processInstanceId) {
        Integer mapped = this.mapper.findMapping(ProcessInstanceIdContext.get(processInstanceId));
        if (mapped == ksessionId) {
            return true;
        }
        
        return false;
    }


    private class MaintainMappingListener extends DefaultProcessEventListener {

        private Integer ksessionId;
        private RuntimeEngine runtime;
        
        MaintainMappingListener(Integer ksessionId, RuntimeEngine runtime) {
            this.ksessionId = ksessionId;
            this.runtime = runtime;
        }
        @Override
        public void afterProcessCompleted(ProcessCompletedEvent event) {
            mapper.removeMapping(ProcessInstanceIdContext.get(event.getProcessInstance().getId()));
            removeLocalRuntime(runtime);
            
            registerDisposeCallback(runtime, 
                        new DestroySessionTransactionSynchronization(runtime.getKieSession()));            
        }

        @Override
        public void beforeProcessStarted(ProcessStartedEvent event) {
            mapper.saveMapping(ProcessInstanceIdContext.get(event.getProcessInstance().getId()), ksessionId);  
            saveLocalRuntime(event.getProcessInstance().getId(), runtime);
        }
        
    }


    public SessionFactory getFactory() {
        return factory;
    }

    public void setFactory(SessionFactory factory) {
        this.factory = factory;
    }

    public TaskServiceFactory getTaskServiceFactory() {
        return taskServiceFactory;
    }

    public void setTaskServiceFactory(TaskServiceFactory taskServiceFactory) {
        this.taskServiceFactory = taskServiceFactory;
    }

    public Mapper getMapper() {
        return mapper;
    }

    public void setMapper(Mapper mapper) {
        this.mapper = mapper;
    }
    
    protected RuntimeEngine findLocalRuntime(Object processInstanceId) {
        if (processInstanceId == null) {
            return null;
        }
        Map<Object, RuntimeEngine> map = local.get();
        if (map == null) {
            return null;
        } else {
            return map.get(processInstanceId);
        }
    }
    
    protected void saveLocalRuntime(Object processInstanceId, RuntimeEngine runtime) {
        if (processInstanceId == null) {
            return;
        }
        Map<Object, RuntimeEngine> map = local.get();
        if (map == null) {
            map = new HashMap<Object, RuntimeEngine>();
            local.set(map);
        } 
        
        map.put(processInstanceId, runtime);
        
    }
    
    protected void removeLocalRuntime(RuntimeEngine runtime) {
        Map<Object, RuntimeEngine> map = local.get();
        Object keyToRemove = -1l;
        if (map != null) {
            for (Map.Entry<Object, RuntimeEngine> entry : map.entrySet()) {
                if (runtime.equals(entry.getValue())) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            
            map.remove(keyToRemove);
        }
    }
    
    @Override
    public void init() {
        // need to init one session to bootstrap all case - such as start timers
        RuntimeEngine engine = getRuntimeEngine(EmptyContext.get());
        try {
            engine.getKieSession().destroy();
        } finally {
            disposeRuntimeEngine(engine);
        }
        
    }

}
