/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.droolsjbpm.services.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.droolsjbpm.services.api.Domain;
import org.jbpm.shared.services.api.FileException;
import org.jbpm.shared.services.api.FileService;
import org.droolsjbpm.services.api.KnowledgeAdminDataService;
import org.droolsjbpm.services.api.KnowledgeDataService;
import org.droolsjbpm.services.api.KnowledgeDomainService;
import org.droolsjbpm.services.api.SessionManager;
import org.droolsjbpm.services.api.bpmn2.BPMN2DataService;
import org.droolsjbpm.services.impl.KnowledgeDomainServiceImpl;
import org.droolsjbpm.services.impl.SimpleDomainImpl;
import org.droolsjbpm.services.impl.example.NotificationWorkItemHandler;
import org.droolsjbpm.services.impl.example.TriggerTestsWorkItemHandler;
import org.jbpm.task.api.TaskServiceEntryPoint;
import org.junit.Test;

import static org.junit.Assert.*;
import org.kie.commons.java.nio.file.Path;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkItem;
import org.kie.runtime.process.WorkItemHandler;
import org.kie.runtime.process.WorkItemManager;
import org.kie.runtime.rule.QueryResults;

public abstract class DomainKnowledgeServiceWithRulesBaseTest {

    @Inject
    protected TaskServiceEntryPoint taskService;
    @Inject
    private BPMN2DataService bpmn2Service;
    @Inject
    protected KnowledgeDataService dataService;
    @Inject
    protected KnowledgeAdminDataService adminDataService;
    @Inject
    private FileService fs;
    @Inject
    private SessionManager sessionManager;
    @Inject
    private KnowledgeDomainService domainService;
    
    @Inject
    private TriggerTestsWorkItemHandler triggerTestsWorkItemHandler;
    
    @Inject
    private NotificationWorkItemHandler notificationWorkItemHandler;

    @Test
    public void testReleaseProcessWithRules() throws FileException, InterruptedException {
        Domain myDomain = new SimpleDomainImpl("myDomain");
        sessionManager.setDomain(myDomain);

        Iterable<Path> processFiles = null;
        Iterable<Path> rulesFiles = null;
        try {
            processFiles = fs.loadFilesByType("examples/release/", "bpmn");
            rulesFiles = fs.loadFilesByType("examples/release/", "drl");
        } catch (FileException ex) {
            Logger.getLogger(KnowledgeDomainServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        String kSessionName = "myKsession";
        for (Path p : processFiles) {
            
            System.out.println(" >>> Loading Path -> " + p.toString());
            myDomain.addProcessDefinitionToKsession(kSessionName, p);
            String processString = new String(fs.loadFile(p));
            myDomain.addProcessBPMN2ContentToKsession(kSessionName, bpmn2Service.findProcessId(processString), processString);
        }
        for (Path p : rulesFiles) {
            System.out.println(" >>> Loading Path -> " + p.toString());
            myDomain.addRulesDefinitionToKsession(kSessionName, p);
        }

        sessionManager.buildSessions(true);

        sessionManager.addKsessionHandler("myKsession", "MoveToStagingArea", new DoNothingWorkItemHandler());
        sessionManager.addKsessionHandler("myKsession", "MoveToTest", new DoNothingWorkItemHandler());
        sessionManager.addKsessionHandler("myKsession", "TriggerTests", triggerTestsWorkItemHandler);
        sessionManager.addKsessionHandler("myKsession", "MoveBackToStaging", new DoNothingWorkItemHandler());
        sessionManager.addKsessionHandler("myKsession", "MoveToProduction", new DoNothingWorkItemHandler());
        
        sessionManager.addKsessionHandler("myKsession", "Email", notificationWorkItemHandler);

        sessionManager.registerHandlersForSession("myKsession");

        sessionManager.registerRuleListenerForSession("myKsession");
        
        sessionManager.getKsessionByName("myKsession").setGlobal("rulesFired", new ArrayList<String>());
        
        sessionManager.getKsessionByName("myKsession").setGlobal("taskService", taskService);
        
        // Let's start a couple of processes
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("release_name", "first release");
        params.put("release_path", "/releasePath/");

        ProcessInstance firstPI = sessionManager.getKsessionByName("myKsession").startProcess("org.jbpm.release.process", params);

        params = new HashMap<String, Object>();
        params.put("release_name", "second release");
        params.put("release_path", "/releasePath2/");

        
        
        ProcessInstance secondPI = sessionManager.getKsessionByName("myKsession").startProcess("org.jbpm.release.process", params);

        QueryResults queryResults = sessionManager.getKsessionByName("myKsession").getQueryResults("getProcessInstances", new Object[]{});
        
        assertEquals(2, queryResults.size());

        params = new HashMap<String, Object>();
        params.put("release_name", "third release");
        params.put("release_path", "/releasePath/");

        
        // This process must be automatically aborted because it's using the same release path than the first process.
        ProcessInstance thirdPI = sessionManager.getKsessionByName("myKsession").startProcess("org.jbpm.release.process", params);
        
        assertEquals(ProcessInstance.STATE_ABORTED, thirdPI.getState());
        
        //LET'S SLEEP FOR 20 SECONDS AND FIRE ALL THE RULES EACH SECOND
        
        for(int i = 0; i < 20; i ++){
            Thread.sleep(1000);
            System.out.println("Waiting...");
          
        }
        List<String> rulesFired = (List<String>) sessionManager.getKsessionByName("myKsession").getGlobal("rulesFired");
        assertEquals(3, rulesFired.size());
        


    }

    private class DoNothingWorkItemHandler implements WorkItemHandler {

        @Override
        public void executeWorkItem(WorkItem wi, WorkItemManager wim) {
            for (String k : wi.getParameters().keySet()) {
                System.out.println("Key = " + k + " - value = " + wi.getParameter(k));
            }

            wim.completeWorkItem(wi.getId(), null);
        }

        @Override
        public void abortWorkItem(WorkItem wi, WorkItemManager wim) {
        }
    }

    private class MockTestWorkItemHandler implements WorkItemHandler {

        @Override
        public void executeWorkItem(WorkItem wi, WorkItemManager wim) {
            for (String k : wi.getParameters().keySet()) {
                System.out.println("Key = " + k + " - value = " + wi.getParameter(k));
            }
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("out_test_successful", "true");
            params.put("out_test_report", "All Test were SUCCESSFULY executed!");
            wim.completeWorkItem(wi.getId(), params);
        }

        @Override
        public void abortWorkItem(WorkItem wi, WorkItemManager wim) {
        }
    }
}
