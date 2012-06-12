/**
 * Copyright 2010 JBoss Inc
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

package org.jbpm.process.audit;

import java.util.Date;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.NotSupportedException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.drools.WorkingMemory;
import org.drools.audit.WorkingMemoryLogger;
import org.drools.audit.event.LogEvent;
import org.drools.audit.event.RuleFlowLogEvent;
import org.drools.audit.event.RuleFlowNodeLogEvent;
import org.drools.audit.event.RuleFlowVariableLogEvent;
import org.drools.event.KnowledgeRuntimeEventManager;
import org.drools.event.process.ProcessCompletedEvent;
import org.drools.event.process.ProcessStartedEvent;
import org.drools.impl.StatelessKnowledgeSessionImpl;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.KnowledgeRuntime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jbpm.process.audit.event.ExtendedRuleFlowLogEvent;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;


/**
 * Enables history log via JPA.
 * 
 */
public class JPAWorkingMemoryDbLogger extends WorkingMemoryLogger {

    private static Logger logger = LoggerFactory.getLogger(JPAWorkingMemoryDbLogger.class);
    
    protected Environment env;

    public JPAWorkingMemoryDbLogger(WorkingMemory workingMemory) {
        super(workingMemory);
        env = workingMemory.getEnvironment();
    }
    
    public JPAWorkingMemoryDbLogger(KnowledgeRuntimeEventManager session) {
    	super(session);
        if (session instanceof KnowledgeRuntime) {
            env = ((KnowledgeRuntime) session).getEnvironment();
        } else if (session instanceof StatelessKnowledgeSessionImpl) {
        	env = ((StatelessKnowledgeSessionImpl) session).getEnvironment();
        } else {
            throw new IllegalArgumentException(
                "Not supported session in logger: " + session.getClass());
        }
    }

    public void logEventCreated(LogEvent logEvent) {
        switch (logEvent.getType()) {
            case LogEvent.BEFORE_RULEFLOW_CREATED:
                RuleFlowLogEvent processEvent = (RuleFlowLogEvent) logEvent;
                addProcessLog(processEvent);
                break;
            case LogEvent.AFTER_RULEFLOW_COMPLETED:
            	processEvent = (RuleFlowLogEvent) logEvent;
                updateProcessLog(processEvent);
                break;
            case LogEvent.BEFORE_RULEFLOW_NODE_TRIGGERED:
            	RuleFlowNodeLogEvent nodeEvent = (RuleFlowNodeLogEvent) logEvent;
            	addNodeEnterLog(nodeEvent.getProcessInstanceId(), nodeEvent.getProcessId(), nodeEvent.getNodeInstanceId(), nodeEvent.getNodeId(), nodeEvent.getNodeName());
                break;
            case LogEvent.BEFORE_RULEFLOW_NODE_EXITED:
            	nodeEvent = (RuleFlowNodeLogEvent) logEvent;
            	addNodeExitLog(nodeEvent.getProcessInstanceId(), nodeEvent.getProcessId(), nodeEvent.getNodeInstanceId(), nodeEvent.getNodeId(), nodeEvent.getNodeName());
                break;
            case LogEvent.AFTER_VARIABLE_INSTANCE_CHANGED:
            	RuleFlowVariableLogEvent variableEvent = (RuleFlowVariableLogEvent) logEvent;
            	addVariableLog(variableEvent.getProcessInstanceId(), variableEvent.getProcessId(), variableEvent.getVariableInstanceId(), variableEvent.getVariableId(), variableEvent.getObjectToString());
                break;
            default:
                // ignore all other events
        }
    }

    private void addProcessLog(RuleFlowLogEvent processEvent) {
        ProcessInstanceLog log = new ProcessInstanceLog(processEvent.getProcessInstanceId(), processEvent.getProcessId());
        if (processEvent instanceof ExtendedRuleFlowLogEvent) {
            log.setParentProcessInstanceId(((ExtendedRuleFlowLogEvent) processEvent).getParentProcessInstanceId());
        }
        persist(log);
    }

    @SuppressWarnings("unchecked")
    private void updateProcessLog(RuleFlowLogEvent processEvent) {
    	 EntityManager em = getEntityManager();
         UserTransaction ut = joinTransaction(em);
         List<ProcessInstanceLog> result = em.createQuery(
         "from ProcessInstanceLog as log where log.processInstanceId = ? and log.end is null")
             .setParameter(1, processEvent.getProcessInstanceId()).getResultList();
         
         if (result != null && result.size() != 0) {
            ProcessInstanceLog log = result.get(result.size() - 1);
            log.setEnd(new Date());
            if (processEvent instanceof ExtendedRuleFlowLogEvent) {
                log.setStatus(((ExtendedRuleFlowLogEvent) processEvent).getProcessInstanceState());
                log.setOutcome(((ExtendedRuleFlowLogEvent) processEvent).getOutcome());
            }
            
            em.merge(log);   
        }
        flush(em, ut);
    }

    private void addNodeEnterLog(long processInstanceId, String processId, String nodeInstanceId, String nodeId, String nodeName) {
        NodeInstanceLog log = new NodeInstanceLog(
    		NodeInstanceLog.TYPE_ENTER, processInstanceId, processId, nodeInstanceId, nodeId, nodeName);
    	persist(log);
    }

    private void addNodeExitLog(long processInstanceId,
            String processId, String nodeInstanceId, String nodeId, String nodeName) {
        NodeInstanceLog log = new NodeInstanceLog(
            NodeInstanceLog.TYPE_EXIT, processInstanceId, processId, nodeInstanceId, nodeId, nodeName);
    	persist(log);
    }

    private void addVariableLog(long processInstanceId, String processId, String variableInstanceId, String variableId, String objectToString) {
    	VariableInstanceLog log = new VariableInstanceLog(
    		processInstanceId, processId, variableInstanceId, variableId, objectToString);
    	persist(log);
    }

    public void dispose() {
    }

    /**
     * This method creates a entity manager. 
     */
    private EntityManager getEntityManager() {
        EntityManagerFactory emf = (EntityManagerFactory) env.get(EnvironmentName.ENTITY_MANAGER_FACTORY);
        return emf.createEntityManager(); 
    }

    /**
     * This method persists the entity given to it. 
     * </p>
     * This method also makes sure that the entity manager used for persisting the entity, joins the existing JTA transaction. 
     * @param entity An entity to be persisted.
     */
    private void persist(Object entity) { 
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        em.persist(entity);
        flush(em, ut);
    }
    
    /**
     * This method opens a new transaction, if none is currently running, and joins the entity manager/persistence context
     * to that transaction. 
     * @param em The entity manager we're using. 
     * @return {@link UserTransaction} If we've started a new transaction, then we return it so that it can be closed. 
     * @throws NotSupportedException 
     * @throws SystemException 
     * @throws Exception if something goes wrong. 
     */
    private static UserTransaction joinTransaction(EntityManager em) {
        boolean newTx = false;
        UserTransaction ut = null;
        try { 
        	InitialContext ctx = new InitialContext();
        	try {
        		ut = (UserTransaction) ctx.lookup( "java:comp/UserTransaction" );
        	} catch (NameNotFoundException e) {
        		// java:comp/UserTransaction is not available on JbossAS 7 for threads that are not managed by server
        		// for instance when task is completed (as that creates a application thread on response)
        		// so try to use the one that is globally accessible
        		ut = (UserTransaction) ctx.lookup( "java:jboss/UserTransaction" );
			}
            if( ut.getStatus() == Status.STATUS_NO_TRANSACTION ) { 
                ut.begin();
                newTx = true;
            }
        } catch(Exception e) { 
            logger.error("Unable to find or open a transaction: " + e.getMessage());
            e.printStackTrace();
        }
        
        em.joinTransaction(); 
       
        if( newTx ) { 
            return ut;
        }
        return null;
    }

    /**
     * This method closes the entity manager and transaction. It also makes sure that any objects associated 
     * with the entity manager/persistence context are detached. 
     * </p>
     * Obviously, if the transaction returned by the {@link #joinTransaction(EntityManager)} method is null, 
     * nothing is done with the transaction parameter.
     * @param em The entity manager.
     * @param ut The (user) transaction.
     */
    private static void flush(EntityManager em, UserTransaction ut) {
        em.flush(); // This saves any changes made
        em.clear(); // This makes sure that any returned entities are no longer attached to this entity manager/persistence context
        em.close(); // and this closes the entity manager
        try { 
            if( ut != null ) { 
                // There's a tx running, close it.
                ut.commit();
            }
        } catch(Exception e) { 
            logger.error("Unable to commit transaction: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void beforeProcessStarted(ProcessStartedEvent event) {
        long parentProcessInstanceId = -1;
        try {
            ProcessInstanceImpl processInstance = (ProcessInstanceImpl) event.getProcessInstance();
            parentProcessInstanceId = (Long) processInstance.getMetaData().get("ParentProcessInstanceId");
        } catch (Exception e) {
            //in case of problems with getting hold of parentProcessInstanceId don't break the operation
        }
        LogEvent logEvent =  new ExtendedRuleFlowLogEvent( LogEvent.BEFORE_RULEFLOW_CREATED,
                event.getProcessInstance().getProcessId(),
                event.getProcessInstance().getProcessName(),
                event.getProcessInstance().getId(), parentProcessInstanceId) ;
        
        // filters are not available from super class, TODO make fireLogEvent protected instead of private in WorkinMemoryLogger
        logEventCreated( logEvent );
    }

    public void afterProcessCompleted(ProcessCompletedEvent event) {
        String outcome = null;
        try {
            ProcessInstanceImpl processInstance = (ProcessInstanceImpl) event.getProcessInstance();
            outcome = processInstance.getOutcome();
        } catch (Exception e) {
            //in case of problems with getting hold of parentProcessInstanceId don't break the operation
        }
        LogEvent logEvent =  new ExtendedRuleFlowLogEvent(LogEvent.AFTER_RULEFLOW_COMPLETED,
                event.getProcessInstance().getProcessId(),
                event.getProcessInstance().getProcessName(),
                event.getProcessInstance().getId(), event.getProcessInstance().getState(), outcome) ;
        
        // filters are not available from super class, TODO make fireLogEvent protected instead of private in WorkinMemoryLogger
        logEventCreated( logEvent );
    }

}
