package org.jbpm.runtime.manager.impl;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.drools.persistence.jta.JtaTransactionManager;
import org.jbpm.runtime.manager.impl.tx.DisposeSessionTransactionSynchronization;
import org.kie.event.process.ProcessEventListener;
import org.kie.event.rule.AgendaEventListener;
import org.kie.event.rule.WorkingMemoryEventListener;
import org.kie.runtime.manager.RegisterableItemsFactory;
import org.kie.runtime.manager.Runtime;
import org.kie.runtime.manager.RuntimeEnvironment;
import org.kie.runtime.manager.RuntimeManager;
import org.kie.runtime.process.WorkItemHandler;

public abstract class AbstractRuntimeManager implements RuntimeManager {

    protected RuntimeEnvironment environment;
    
    public AbstractRuntimeManager(RuntimeEnvironment environment) {
        this.environment = environment;
    }
    
    protected void registerItems(Runtime runtime) {
        RegisterableItemsFactory factory = environment.getRegisterableItemsFactory();
        // process handlers
        Map<String, WorkItemHandler> handlers = factory.getWorkItemHandlers(runtime);
        for (Entry<String, WorkItemHandler> entry : handlers.entrySet()) {
            runtime.getKieSession().getWorkItemManager().registerWorkItemHandler(entry.getKey(), entry.getValue());
        }
        
        // process listeners
        List<ProcessEventListener> processListeners = factory.getProcessEventListeners(runtime);
        for (ProcessEventListener listener : processListeners) {
            runtime.getKieSession().addEventListener(listener);
        }
        
        List<AgendaEventListener> agendaListeners = factory.getAgendaEventListeners(runtime);
        for (AgendaEventListener listener : agendaListeners) {
            runtime.getKieSession().addEventListener(listener);
        }
        
        List<WorkingMemoryEventListener> wmListeners = factory.getWorkingMemoryEventListeners(runtime);
        for (WorkingMemoryEventListener listener : wmListeners) {
            runtime.getKieSession().addEventListener(listener);
        }
    }
    
    protected void registerDisposeCallback(Runtime runtime) {
        // register it if there is an active transaction as we assume then to be running in a managed environment e.g CMT
        // TODO is there better way to register transaction synchronization?
        JtaTransactionManager tm = new JtaTransactionManager(null, null, null);
        if (tm.getStatus() != JtaTransactionManager.STATUS_NO_TRANSACTION
                && tm.getStatus() != JtaTransactionManager.STATUS_ROLLEDBACK
                && tm.getStatus() != JtaTransactionManager.STATUS_COMMITTED) {
            tm.registerTransactionSynchronization(new DisposeSessionTransactionSynchronization(this, runtime));
        }
    }

    @Override
    public void close() {
        environment.close();
    }

    public RuntimeEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(RuntimeEnvironment environment) {
        this.environment = environment;
    }
    
    

}
