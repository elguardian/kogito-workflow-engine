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

package org.jbpm.bpmn2;

import java.io.ByteArrayInputStream;
import java.util.*;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jbpm.bpmn2.handler.*;
import org.jbpm.bpmn2.handler.ReceiveTaskHandler;
import org.jbpm.bpmn2.handler.SendTaskHandler;
import org.jbpm.bpmn2.handler.ServiceTaskHandler;
import org.jbpm.bpmn2.objects.*;
import org.jbpm.process.instance.impl.demo.DoNothingWorkItemHandler;
import org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler;
import org.junit.*;
import org.kie.api.KieBase;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderError;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.io.ResourceType;
import org.kie.runtime.KieSession;
import org.kie.runtime.process.*;
import org.w3c.dom.Document;

public class StandaloneBPMNProcessTest extends NewJbpmBpmn2TestBase {

    @After
    public void tearDown() {
        KnowledgeBaseFactory.setKnowledgeBaseServiceFactory(null);
    }

    /**
     * Tests
     */
    
    @Test
    public void testMinimalProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-MinimalProcess.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("Minimal");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithGraphical() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-MinimalProcessWithGraphical.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("Minimal");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithDIGraphical() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-MinimalProcessWithDIGraphical.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("Minimal");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCompositeProcessWithDIGraphical() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-CompositeProcessWithDIGraphical.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("Composite");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testScriptTask() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ScriptTask.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("ScriptTask");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testDataObject() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-DataObject.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "UserId-12345");
        ProcessInstance processInstance = ksession.startProcess("Evaluation", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEvaluationProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EvaluationProcess.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("RegisterRequest", new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "UserId-12345");
        ProcessInstance processInstance = ksession.startProcess("Evaluation", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEvaluationProcess2() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EvaluationProcess2.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "UserId-12345");
        ProcessInstance processInstance = ksession.startProcess("com.sample.evaluation", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @Ignore
    public void testEvaluationProcess3() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EvaluationProcess3.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("RegisterRequest", new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "john2");
        ProcessInstance processInstance = ksession.startProcess("Evaluation", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTask() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-UserTask.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ProcessInstance processInstance = ksession.startProcess("UserTask");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        assertEquals("john", workItem.getParameter("ActorId"));
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testLane() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-Lane.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ProcessInstance processInstance = ksession.startProcess("UserTask");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        assertEquals("john", workItem.getParameter("ActorId"));
        Map<String, Object> results = new HashMap<String, Object>();
        results.put("ActorId", "mary");
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), results);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        assertEquals("mary", workItem.getParameter("ActorId"));
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testExclusiveSplit() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ExclusiveSplit.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email", new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "First");
        params.put("y", "Second");
        ProcessInstance processInstance = ksession.startProcess("com.sample.test", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testExclusiveSplitDefault() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ExclusiveSplitDefault.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email", new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "NotFirst");
        params.put("y", "Second");
        ProcessInstance processInstance = ksession.startProcess("com.sample.test", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplit() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-InclusiveSplit.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 15);
        ProcessInstance processInstance = ksession.startProcess("com.sample.test", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitDefault() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-InclusiveSplitDefault.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", -5);
        ProcessInstance processInstance = ksession.startProcess("com.sample.test", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @Ignore
    public void testExclusiveSplitXPath() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ExclusiveSplitXPath.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        
        ksession.getWorkItemManager().registerWorkItemHandler("Email", new SystemOutWorkItemHandler());
        Document document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(
                        "<myDocument><chapter1>BlaBla</chapter1><chapter2>MoreBlaBla</chapter2></myDocument>".getBytes()));
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", document);
        params.put("y", "SomeString");
        ProcessInstance processInstance = ksession.startProcess("com.sample.test", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEventBasedSplit() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EventBasedSplit.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        // No
        processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ksession.signalEvent("No", "NoValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventBasedSplitBefore() throws Exception {
        // signaling before the split is reached should have no effect
        KieBase kbase = createKnowledgeBase("BPMN2-EventBasedSplit.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new DoNothingWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new DoNothingWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new DoNothingWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new DoNothingWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // No
        processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new DoNothingWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new DoNothingWorkItemHandler());
        ksession.signalEvent("No", "NoValue", processInstance.getId());
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testEventBasedSplitAfter() throws Exception {
        // signaling the other alternative after one has been selected should
        // have no effect
        KieBase kbase = createKnowledgeBase("BPMN2-EventBasedSplit.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new DoNothingWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new DoNothingWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new DoNothingWorkItemHandler());
        // No
        ksession.signalEvent("No", "NoValue", processInstance.getId());
    }

    @Test
    public void testEventBasedSplit2() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EventBasedSplit2.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        Thread.sleep(800);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        // Timer
        processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(800);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @Ignore
    public void testEventBasedSplit3() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EventBasedSplit3.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        Person jack = new Person();
        jack.setName("Jack");
        // Yes
        ProcessInstance processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        // Condition
        processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ksession.insert(jack);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventBasedSplit4() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EventBasedSplit4.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ksession.signalEvent("Message-YesMessage", "YesValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        // No
        processInstance = ksession.startProcess("com.sample.test");
        ksession.signalEvent("Message-NoMessage", "NoValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventBasedSplit5() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EventBasedSplit5.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        ReceiveTaskHandler receiveTaskHandler = new ReceiveTaskHandler(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task", receiveTaskHandler);
        // Yes
        ProcessInstance processInstance = ksession.startProcess("com.sample.test");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        receiveTaskHandler.setKnowledgeRuntime(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task", receiveTaskHandler);
        receiveTaskHandler.messageReceived("YesMessage", "YesValue");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        receiveTaskHandler.messageReceived("NoMessage", "NoValue");
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        receiveTaskHandler.setKnowledgeRuntime(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task", receiveTaskHandler);
        // No
        processInstance = ksession.startProcess("com.sample.test");
        receiveTaskHandler.messageReceived("NoMessage", "NoValue");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        receiveTaskHandler.messageReceived("YesMessage", "YesValue");
    }

    @Test
    public void testCallActivity() throws Exception {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newClassPathResource("BPMN2-CallActivity.bpmn2"), ResourceType.BPMN2);
        kbuilder.add(ResourceFactory.newClassPathResource("BPMN2-CallActivitySubProcess.bpmn2"), ResourceType.BPMN2);
        if (!kbuilder.getErrors().isEmpty()) {
            for (KnowledgeBuilderError error : kbuilder.getErrors()) {
                System.err.println(error);
            }
            throw new IllegalArgumentException("Errors while parsing knowledge base");
        }
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages(kbuilder.getKnowledgePackages());
        KieSession ksession = createKnowledgeSession(kbase);
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "oldValue");
        ProcessInstance processInstance = ksession.startProcess("ParentProcess", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals("new value", ((WorkflowProcessInstance) processInstance).getVariable("y"));
    }

    @Test
    public void testSubProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-SubProcess.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("SubProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-MultiInstanceLoopCharacteristicsProcess.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        Map<String, Object> params = new HashMap<String, Object>();
        List<String> myList = new ArrayList<String>();
        myList.add("First Item");
        myList.add("Second Item");
        params.put("list", myList);
        ProcessInstance processInstance = ksession.startProcess("MultiInstanceLoopCharacteristicsProcess", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEscalationBoundaryEvent() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EscalationBoundaryEvent.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("EscalationBoundaryEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEscalationBoundaryEventInterrupting() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EscalationBoundaryEventInterrupting.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask", new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession.startProcess("EscalationBoundaryEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        // TODO: check for cancellation of task
    }

    @Test
    public void testErrorBoundaryEvent() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ErrorBoundaryEventInterrupting.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask", new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession.startProcess("ErrorBoundaryEvent");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @Ignore
    public void testTimerBoundaryEvent() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-TimerBoundaryEventDuration.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask", new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession.startProcess("TimerBoundaryEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @Ignore
    public void testTimerBoundaryEventInterrupting() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-TimerBoundaryEventInterrupting.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask", new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession.startProcess("TimerBoundaryEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @Ignore
    public void testAdHocSubProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-AdHocSubProcess.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ProcessInstance processInstance = ksession.startProcess("AdHocSubProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNull(workItem);
        ksession = restoreSession(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ksession.fireAllRules();
        System.out.println("Signaling Hello2");
        ksession.signalEvent("Hello2", null, processInstance.getId());
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession = restoreSession(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
    }

    @Test
    @Ignore
    public void testAdHocSubProcessAutoComplete() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-AdHocSubProcessAutoComplete.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ProcessInstance processInstance = ksession.startProcess("AdHocSubProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNull(workItem);
        ksession = restoreSession(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ksession.fireAllRules();
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession = restoreSession(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testIntermediateCatchEventSignal() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateCatchEventSignal.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession.startProcess("IntermediateCatchEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        // now signal process instance
        ksession.signalEvent("MyMessage", "SomeValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testIntermediateCatchEventMessage() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateCatchEventMessage.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession.startProcess("IntermediateCatchEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        // now signal process instance
        ksession.signalEvent("Message-HelloMessage", "SomeValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testIntermediateCatchEventTimer() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateCatchEventTimerDuration.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession.startProcess("IntermediateCatchEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1 second for timer to trigger
        Thread.sleep(1000);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", new DoNothingWorkItemHandler());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @Ignore
    public void testIntermediateCatchEventCondition() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateCatchEventCondition.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("IntermediateCatchEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession);
        // now activate condition
        Person person = new Person();
        person.setName("Jack");
        ksession.insert(person);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testErrorEndEventProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ErrorEndEvent.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("ErrorEndEvent");
        assertProcessInstanceAborted(processInstance.getId(), ksession);
    }

    @Test
    public void testEscalationEndEventProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-EscalationEndEvent.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("EscalationEndEvent");
        assertProcessInstanceAborted(processInstance.getId(), ksession);
    }

    @Test
    public void testEscalationIntermediateThrowEventProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateThrowEventEscalation.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("EscalationIntermediateThrowEvent");
        assertProcessInstanceAborted(processInstance.getId(), ksession);
    }

    @Test
    public void testCompensateIntermediateThrowEventProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateThrowEventCompensate.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("CompensateIntermediateThrowEvent");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testCompensateEndEventProcess() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-CompensateEndEvent.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("CompensateEndEvent");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testServiceTask() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ServiceProcess.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Service Task", new ServiceTaskHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("s", "john");
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("ServiceProcess", params);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertEquals("Hello john!", processInstance.getVariable("s"));
    }

    @Test
    public void testSendTask() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-SendTask.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Send Task", new SendTaskHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("s", "john");
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("SendTask", params);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testReceiveTask() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ReceiveTask.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ReceiveTaskHandler receiveTaskHandler = new ReceiveTaskHandler(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task", receiveTaskHandler);
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("ReceiveTask");
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstance.getState());
        ksession = restoreSession(ksession, true);
        receiveTaskHandler.messageReceived("HelloMessage", "Hello john!");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @Ignore
    public void testConditionalStart() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-ConditionalStart.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        Person person = new Person();
        person.setName("jack");
        ksession.insert(person);
        ksession.fireAllRules();
        person = new Person();
        person.setName("john");
        ksession.insert(person);
        ksession.fireAllRules();
    }

    @Test
    @Ignore
    public void testTimerStart() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-TimerStart.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        final List<Long> list = new ArrayList<Long>();
        ksession.addEventListener(new DefaultProcessEventListener() { 
            
            public void afterProcessStarted(ProcessStartedEvent event) {
                list.add(event.getProcessInstance().getId());
            }
            
        });
        Thread.sleep(250);
        assertEquals(0, list.size());
        for (int i = 0; i < 5; i++) {
            Thread.sleep(500);
        }
        assertEquals(5, list.size());
    }

    @Test
    public void testSignalStart() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-SignalStart.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        final List<Long> list = new ArrayList<Long>();
        ksession.addEventListener(new DefaultProcessEventListener() {
            public void afterProcessStarted(ProcessStartedEvent event) {
                list.add(event.getProcessInstance().getId());
            }
        });
        ksession.signalEvent("MySignal", "NewValue");
        assertEquals(1, list.size());
    }

    @Test
    public void testSignalEnd() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-SignalEndEvent.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "MyValue");
        ksession.startProcess("SignalEndEvent", params);
    }

    @Test
    public void testMessageStart() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-MessageStart.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        final List<Long> list = new ArrayList<Long>();
        ksession.addEventListener(new DefaultProcessEventListener() {
            public void afterProcessStarted(ProcessStartedEvent event) {
                list.add(event.getProcessInstance().getId());
            }
        });
        ksession.signalEvent("Message-HelloMessage", "NewValue");
        assertEquals(1, list.size());
    }

    @Test
    public void testMessageEnd() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-MessageEndEvent.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Send Task", new SendTaskHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "MyValue");
        ProcessInstance processInstance = ksession.startProcess("MessageEndEvent", params);
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
    }

    @Test
    public void testMessageIntermediateThrow() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateThrowEventMessage.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ksession.getWorkItemManager().registerWorkItemHandler("Send Task", new SendTaskHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "MyValue");
        ProcessInstance processInstance = ksession.startProcess("MessageIntermediateEvent", params);
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
    }

    @Test
    public void testSignalIntermediateThrow() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateThrowEventSignal.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "MyValue");
        ProcessInstance processInstance = ksession.startProcess("SignalIntermediateEvent", params);
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
    }

    @Test
    public void testNoneIntermediateThrow() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-IntermediateThrowEventNone.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("NoneIntermediateEvent", null);
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
    }
    
    // dump/reread functionality not (yet) available for this test (mriet, 2013-03-06)
    @Test
    public void testErrorSignallingExceptionServiceTask() throws Exception {
        KieBase kbase = createKnowledgeBaseWithoutDumper("BPMN2-ExceptionServiceProcess-ErrorSignalling.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        
        runTestErrorSignallingExceptionServiceTask(ksession);
    }
    
    public static void runTestErrorSignallingExceptionServiceTask(KieSession ksession) throws Exception {
        
        // Setup
        String eventType = "Error-code";
        SignallingTaskHandlerWrapper signallingTaskWrapper = new SignallingTaskHandlerWrapper(ServiceTaskHandler.class, eventType, ksession);
        signallingTaskWrapper.setWorkItemExceptionParameterName(ExceptionService.exceptionParameterName);
        ksession.getWorkItemManager().registerWorkItemHandler("Service Task", signallingTaskWrapper);
       
        Object [] caughtEventObjectHolder = new Object[1];
        caughtEventObjectHolder[0] = null;
        ExceptionService.setCaughtEventObjectHolder(caughtEventObjectHolder);
        
        // Start process
        Map<String, Object> params = new HashMap<String, Object>();
        String input = "this is my service input";
        params.put("serviceInputItem", input );
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("ServiceProcess", params);

        // Check that event was passed to Event SubProcess (and grabbed by WorkItemHandler);
        assertTrue( "Event was not passed to Event Subprocess", caughtEventObjectHolder[0] != null && caughtEventObjectHolder[0] instanceof WorkItem );
        WorkItem workItem = (WorkItem) caughtEventObjectHolder[0];
        Object throwObj = workItem.getParameter(ExceptionService.exceptionParameterName);
        assertTrue( "WorkItem doesn't contain Throwable", throwObj instanceof Throwable );
        assertTrue("Exception message does not match service input.", ((Throwable) throwObj).getMessage().endsWith(input) );

        // Complete process
        assertTrue( "Process instance has not been aborted.", processInstance.getState() == ProcessInstance.STATE_ABORTED );
    }
    
    @Test
    public void testSignallingExceptionServiceTask() throws Exception {
        // dump/reread functionality seems to work for this test 
        // .. but I'm pretty sure that's more coincidence than design (mriet, 2013-03-06)
        KieBase kbase = createKnowledgeBase("BPMN2-ExceptionServiceProcess-Signalling.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        
        runTestSignallingExceptionServiceTask(ksession);
    }
    
    public static void runTestSignallingExceptionServiceTask(KieSession ksession) throws Exception {
        
        // Setup
        String eventType = "exception-signal";
        SignallingTaskHandlerWrapper signallingTaskWrapper = new SignallingTaskHandlerWrapper(ServiceTaskHandler.class, eventType, ksession);
        signallingTaskWrapper.setWorkItemExceptionParameterName(ExceptionService.exceptionParameterName);
        ksession.getWorkItemManager().registerWorkItemHandler("Service Task", signallingTaskWrapper);
       
        Object [] caughtEventObjectHolder = new Object[1];
        caughtEventObjectHolder[0] = null;
        ExceptionService.setCaughtEventObjectHolder(caughtEventObjectHolder);
        
        // Start process
        Map<String, Object> params = new HashMap<String, Object>();
        String input = "this is my service input";
        params.put("serviceInputItem", input );
        ProcessInstance processInstance = ksession.startProcess("ServiceProcess", params);

        // Check that event was passed to Event SubProcess (and grabbed by WorkItemHandler);
        assertTrue( "Event was not passed to Event Subprocess", caughtEventObjectHolder[0] != null && caughtEventObjectHolder[0] instanceof WorkItem );
        WorkItem workItem = (WorkItem) caughtEventObjectHolder[0];
        Object throwObj = workItem.getParameter(ExceptionService.exceptionParameterName);
        assertTrue( "WorkItem doesn't contain Throwable", throwObj instanceof Throwable );
        assertTrue("Exception message does not match service input.", ((Throwable) throwObj).getMessage().endsWith(input) );

        // Complete process
        assertEquals( "Process instance is not active.", ProcessInstance.STATE_ACTIVE, processInstance.getState() );
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        
        processInstance = ksession.getProcessInstance(processInstance.getId());
        if( processInstance != null ) { 
            assertEquals( "Process instance is not completed.", ProcessInstance.STATE_COMPLETED, processInstance.getState() );
        } // otherwise, persistence use => processInstance == null => process is completed
    }
}
