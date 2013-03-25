package org.jbpm.runtime.manager;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Properties;

import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.runtime.manager.impl.DefaultRuntimeEnvironment;
import org.jbpm.runtime.manager.impl.RuntimeEnvironmentBuilder;
import org.jbpm.runtime.manager.impl.SimpleRuntimeEnvironment;
import org.jbpm.runtime.manager.util.TestUtil;
import org.jbpm.services.task.identity.JBossUserGroupCallbackImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.KieInternalServices;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.process.CorrelationKeyFactory;
import org.kie.internal.runtime.manager.Runtime;
import org.kie.internal.runtime.manager.RuntimeEnvironment;
import org.kie.internal.runtime.manager.RuntimeManager;
import org.kie.internal.runtime.manager.RuntimeManagerFactory;
import org.kie.internal.runtime.manager.context.CorrelationKeyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.kie.internal.task.api.UserGroupCallback;

import bitronix.tm.resource.jdbc.PoolingDataSource;

public class PerProcessInstanceRuntimeManagerTest {
    private PoolingDataSource pds;
    private UserGroupCallback userGroupCallback;
    private RuntimeManager manager; 
    @Before
    public void setup() {
        Properties properties= new Properties();
        properties.setProperty("mary", "HR");
        properties.setProperty("john", "HR");
        userGroupCallback = new JBossUserGroupCallbackImpl(properties);

        pds = TestUtil.setupPoolingDataSource();
    }
    
    @After
    public void teardown() {
        manager.close();
        pds.close();
    }
    
    @Test
    public void testCreationOfSession() {
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.getDefaultInMemory()
                .userGroupCallback(userGroupCallback)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2)
                .get();
        
        manager = RuntimeManagerFactory.Factory.get().newPerProcessInstanceRuntimeManager(environment);
        assertNotNull(manager);
       
        // ksession for process instance #1
        // since there is no process instance yet we need to get new session
        Runtime runtime = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession = runtime.getKieSession();

        assertNotNull(ksession);       
        int ksession1Id = ksession.getId();
        assertTrue(ksession1Id == 0);
        
        // FIXME quick hack to overcome problems with same pi ids when not using persistence
        ksession.startProcess("ScriptTask");
        
        // ksession for process instance #2
        // since there is no process instance yet we need to get new session
        Runtime runtime2 = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession2 = runtime2.getKieSession();

        assertNotNull(ksession2);       
        int ksession2Id = ksession2.getId();
        assertTrue(ksession2Id == 1);
        
        ProcessInstance pi1 = ksession.startProcess("UserTask");
        
        ProcessInstance pi2 = ksession2.startProcess("UserTask");
        
        // both processes started 
        assertEquals(ProcessInstance.STATE_ACTIVE, pi1.getState());
        assertEquals(ProcessInstance.STATE_ACTIVE, pi2.getState());
        runtime = manager.getRuntime(ProcessInstanceIdContext.get(pi1.getId()));
        ksession = runtime.getKieSession();
        assertEquals(ksession1Id, ksession.getId());
        
        runtime2 = manager.getRuntime(ProcessInstanceIdContext.get(pi2.getId()));
        ksession2 = runtime2.getKieSession();
        assertEquals(ksession2Id, ksession2.getId());
        manager.close();
    }

    
    @Test
    public void testCreationOfSessionWithPersistence() {
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.getDefault()
                .userGroupCallback(userGroupCallback)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2)
                .get();
        
        manager = RuntimeManagerFactory.Factory.get().newPerProcessInstanceRuntimeManager(environment);        
        assertNotNull(manager);
        // ksession for process instance #1
        // since there is no process instance yet we need to get new session
        Runtime runtime = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession = runtime.getKieSession();

        assertNotNull(ksession);       
        int ksession1Id = ksession.getId();
        assertTrue(ksession1Id == 1);

        // ksession for process instance #2
        // since there is no process instance yet we need to get new session
        Runtime runtime2 = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession2 = runtime2.getKieSession();

        assertNotNull(ksession2);       
        int ksession2Id = ksession2.getId();
        assertTrue(ksession2Id == 2);
        
        ProcessInstance pi1 = ksession.startProcess("UserTask");
        
        ProcessInstance pi2 = ksession2.startProcess("UserTask");
        
        // both processes started 
        assertEquals(ProcessInstance.STATE_ACTIVE, pi1.getState());
        assertEquals(ProcessInstance.STATE_ACTIVE, pi2.getState());
        
        runtime = manager.getRuntime(ProcessInstanceIdContext.get(pi1.getId()));
        ksession = runtime.getKieSession();
        assertEquals(ksession1Id, ksession.getId());
        
        ksession.getWorkItemManager().completeWorkItem(1, null);
        // since process is completed now session should not be there any more
        try {
            manager.getRuntime(ProcessInstanceIdContext.get(pi1.getId()));
            fail("Session for this (" + pi1.getId() + ") process instance is no more accessible");
        } catch (RuntimeException e) {
            
        }
        
        runtime2 = manager.getRuntime(ProcessInstanceIdContext.get(pi2.getId()));;
        ksession2 = runtime2.getKieSession();
        assertEquals(ksession2Id, ksession2.getId());
        
        ksession2.getWorkItemManager().completeWorkItem(2, null);
        // since process is completed now session should not be there any more
        try {
            manager.getRuntime(ProcessInstanceIdContext.get(pi2.getId()));
            fail("Session for this (" + pi2.getId() + ") process instance is no more accessible");
        } catch (RuntimeException e) {
            
        }
        manager.close();
    }
    
    @Test
    public void testCreationOfSessionWithPersistenceByCorrelationKey() {
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.getDefault()
                .userGroupCallback(userGroupCallback)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2)
                .get();
        
        CorrelationKeyFactory keyFactory = KieInternalServices.Factory.get().newCorrelationKeyFactory();
        
        manager = RuntimeManagerFactory.Factory.get().newPerProcessInstanceRuntimeManager(environment);        
        assertNotNull(manager);
        // ksession for process instance #1
        // since there is no process instance yet we need to get new session
        CorrelationKey key = keyFactory.newCorrelationKey("first");
        Runtime runtime = manager.getRuntime(CorrelationKeyContext.get());
        KieSession ksession = runtime.getKieSession();

        assertNotNull(ksession);       
        int ksession1Id = ksession.getId();
        assertTrue(ksession1Id == 1);

        // ksession for process instance #2
        // since there is no process instance yet we need to get new session
        CorrelationKey key2 = keyFactory.newCorrelationKey("second");
        Runtime runtime2 = manager.getRuntime(CorrelationKeyContext.get());
        KieSession ksession2 = runtime2.getKieSession();

        assertNotNull(ksession2);       
        int ksession2Id = ksession2.getId();
        assertTrue(ksession2Id == 2);
        
        ProcessInstance pi1 = ((CorrelationAwareProcessRuntime)ksession).startProcess("UserTask", key, null);
        
        ProcessInstance pi2 = ((CorrelationAwareProcessRuntime)ksession2).startProcess("UserTask", key2, null);
        
        // both processes started 
        assertEquals(ProcessInstance.STATE_ACTIVE, pi1.getState());
        assertEquals(ProcessInstance.STATE_ACTIVE, pi2.getState());
        
        runtime = manager.getRuntime(CorrelationKeyContext.get(key));
        ksession = runtime.getKieSession();
        assertEquals(ksession1Id, ksession.getId());
        
        ksession.getWorkItemManager().completeWorkItem(1, null);
        // since process is completed now session should not be there any more
        try {
            manager.getRuntime(CorrelationKeyContext.get(key));
            fail("Session for this (" + pi1.getId() + ") process instance is no more accessible");
        } catch (RuntimeException e) {
            
        }
        
        runtime2 = manager.getRuntime(CorrelationKeyContext.get(key2));
        ksession2 = runtime2.getKieSession();
        assertEquals(ksession2Id, ksession2.getId());
        
        ksession2.getWorkItemManager().completeWorkItem(2, null);
        // since process is completed now session should not be there any more
        try {
            manager.getRuntime(CorrelationKeyContext.get(key2));
            fail("Session for this (" + pi2.getId() + ") process instance is no more accessible");
        } catch (RuntimeException e) {
            
        }
        manager.close();
    }
    
    @Test
    public void testExecuteCompleteWorkItemOnInvalidSessionWithPersistence() {
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.getDefault()
                .userGroupCallback(userGroupCallback)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2)
                .get();
        
        manager = RuntimeManagerFactory.Factory.get().newPerProcessInstanceRuntimeManager(environment);        
        assertNotNull(manager);
        // ksession for process instance #1
        // since there is no process instance yet we need to get new session
        Runtime runtime = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession = runtime.getKieSession();

        assertNotNull(ksession);       
        int ksession1Id = ksession.getId();
        assertTrue(ksession1Id == 1);

        // ksession for process instance #2
        // since there is no process instance yet we need to get new session
        Runtime runtime2 = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession2 = runtime2.getKieSession();

        assertNotNull(ksession2);       
        int ksession2Id = ksession2.getId();
        assertTrue(ksession2Id == 2);
        
        ProcessInstance pi1 = ksession.startProcess("UserTask");
        
        ProcessInstance pi2 = ksession2.startProcess("UserTask");
        
        // both processes started 
        assertEquals(ProcessInstance.STATE_ACTIVE, pi1.getState());
        assertEquals(ProcessInstance.STATE_ACTIVE, pi2.getState());
        
        runtime = manager.getRuntime(ProcessInstanceIdContext.get(pi1.getId()));
        ksession = runtime.getKieSession();
        assertEquals(ksession1Id, ksession.getId());
        
        ksession.getWorkItemManager().completeWorkItem(1, null);
        // since process is completed now session should not be there any more
        try {
            manager.getRuntime(ProcessInstanceIdContext.get(pi1.getId()));
            fail("Session for this (" + pi1.getId() + ") process instance is no more accessible");
        } catch (RuntimeException e) {
            
        }       
        try {
            ksession.getWorkItemManager().completeWorkItem(2, null);
        
            fail("Invalid session was used for (" + pi2.getId() + ") process instance");
        } catch (RuntimeException e) {
            
        }
        manager.close();
    }
    
    @Test
    public void testExecuteReusableSubprocess() {
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.getDefault()
                .userGroupCallback(userGroupCallback)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-CallActivity.bpmn2"), ResourceType.BPMN2)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-CallActivitySubProcess.bpmn2"), ResourceType.BPMN2)
                .get();
        
        manager = RuntimeManagerFactory.Factory.get().newPerProcessInstanceRuntimeManager(environment);        
        assertNotNull(manager);
        // since there is no process instance yet we need to get new session
        Runtime runtime = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession = runtime.getKieSession();

        assertNotNull(ksession);       
        int ksession1Id = ksession.getId();
        assertTrue(ksession1Id == 1);

        ProcessInstance pi1 = ksession.startProcess("ParentProcess");
        
        assertEquals(ProcessInstance.STATE_ACTIVE, pi1.getState());
        
        try {
            ksession.getWorkItemManager().completeWorkItem(1, null);
        
            fail("Invalid session was used for subprocess of (" + pi1.getId() + ") process instance");
        } catch (RuntimeException e) {
            
        }
        manager.disposeRuntime(runtime);
        runtime = manager.getRuntime(ProcessInstanceIdContext.get(2l));
        ksession = runtime.getKieSession();
        ksession.getWorkItemManager().completeWorkItem(1, null);
        manager.close();
        
        JPAProcessInstanceDbLog.setEnvironment(environment.getEnvironment());
        
        List<ProcessInstanceLog> logs = JPAProcessInstanceDbLog.findActiveProcessInstances("ParentProcess");
        assertNotNull(logs);
        assertEquals(0, logs.size());
        
        logs = JPAProcessInstanceDbLog.findActiveProcessInstances("SubProcess");
        assertNotNull(logs);
        assertEquals(0, logs.size());
        
        logs = JPAProcessInstanceDbLog.findProcessInstances("ParentProcess");
        assertNotNull(logs);
        assertEquals(1, logs.size());
        
        logs = JPAProcessInstanceDbLog.findProcessInstances("SubProcess");
        assertNotNull(logs);
        assertEquals(1, logs.size());
    }
    
    @Test
    public void testStartTwoProcessIntancesOnSameSession() {
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.getDefault()
                .userGroupCallback(userGroupCallback)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2)
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2)
                .get();
        
        manager = RuntimeManagerFactory.Factory.get().newPerProcessInstanceRuntimeManager(environment);        
        assertNotNull(manager);
        // since there is no process instance yet we need to get new session
        Runtime runtime = manager.getRuntime(ProcessInstanceIdContext.get());
        KieSession ksession = runtime.getKieSession();

        assertNotNull(ksession);       
        int ksession1Id = ksession.getId();
        assertTrue(ksession1Id == 1);

        ProcessInstance pi1 = ksession.startProcess("UserTask");
  
   
        assertEquals(ProcessInstance.STATE_ACTIVE, pi1.getState());
        
        try {
            ProcessInstance pi2 = ksession.startProcess("UserTask");
            fail("Invalid session was used for (" + pi2.getId() + ") process instance");
        } catch (RuntimeException e) {
            
        }
        manager.close();
    }
}
