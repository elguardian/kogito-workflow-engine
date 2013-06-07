/*
Copyright 2013 JBoss Inc

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package org.jbpm.bpmn2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.Status;
import javax.transaction.Transaction;

import junit.framework.Assert;

import org.drools.compiler.compiler.PackageBuilderConfiguration;
import org.drools.core.SessionConfiguration;
import org.drools.core.audit.WorkingMemoryInMemoryLogger;
import org.drools.core.audit.event.LogEvent;
import org.drools.core.audit.event.RuleFlowLogEvent;
import org.drools.core.audit.event.RuleFlowNodeLogEvent;
import org.drools.core.impl.EnvironmentFactory;
import org.drools.core.util.DroolsStreamUtils;
import org.h2.tools.DeleteDbFiles;
import org.h2.tools.Server;
import org.jbpm.bpmn2.xml.BPMNDISemanticModule;
import org.jbpm.bpmn2.xml.BPMNSemanticModule;
import org.jbpm.bpmn2.xml.XmlBPMNProcessDumper;
import org.jbpm.compiler.xml.XmlProcessReader;
import org.jbpm.process.audit.AuditLoggerFactory;
import org.jbpm.process.audit.AuditLoggerFactory.Type;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.process.instance.event.DefaultSignalManagerFactory;
import org.jbpm.process.instance.impl.DefaultProcessInstanceManagerFactory;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message.Level;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.Process;
import org.kie.api.io.Resource;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.NodeInstanceContainer;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

/**
 * Base test case for the jbpm-bpmn2 module.
 */
public abstract class JbpmBpmn2TestCase extends Assert {

    public static String[] txStateName = { "ACTIVE", "MARKED_ROLLBACK",
            "PREPARED", "COMMITTED", "ROLLEDBACK", "UNKNOWN", "NO_TRANSACTION",
            "PREPARING", "COMMITTING", "ROLLING_BACK" };

    public static final boolean PERSISTENCE = Boolean.valueOf(System
            .getProperty("org.jbpm.test.persistence", "true"));

    private static boolean setupDataSource = false;
    private boolean sessionPersistence = false;
    private static H2Server server = new H2Server();

    private WorkingMemoryInMemoryLogger logger;
    private Logger testLogger = null;

    private static EntityManagerFactory emf;
    private static PoolingDataSource ds;

    private RequirePersistence testReqPersistence;
    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println(" >>> " + description.getMethodName() + " <<< ");

            try {
                String method = description.getMethodName();
                int i = method.indexOf("[");
                if (i > 0) {
                    method = method.substring(0, i);
                }
                testReqPersistence = description.getTestClass()
                        .getMethod(method)
                        .getAnnotation(RequirePersistence.class);
            } catch (Exception ex) {
                // ignore
            }

            if (testLogger == null) {
                testLogger = LoggerFactory.getLogger(getClass());
            }
        };

        protected void finished(Description description) {
            System.out.println("");
        };
    };

    public JbpmBpmn2TestCase() {
        this(PERSISTENCE);
    }

    public JbpmBpmn2TestCase(boolean sessionPersistance) {
        System.setProperty("jbpm.user.group.mapping",
                "classpath:/usergroups.properties");
        System.setProperty("jbpm.usergroup.callback",
                "org.jbpm.task.identity.DefaultUserGroupCallbackImpl");
        this.sessionPersistence = sessionPersistance;
    }

    public static PoolingDataSource setupPoolingDataSource() {
        PoolingDataSource pds = new PoolingDataSource();
        pds.setUniqueName("jdbc/testDS1");
        pds.setClassName("bitronix.tm.resource.jdbc.lrc.LrcXADataSource");
        pds.setMaxPoolSize(5);
        pds.setAllowLocalTransactions(true);
        pds.getDriverProperties().put("user", "sa");
        pds.getDriverProperties().put("password", "");
        pds.getDriverProperties().put("url",
                "jdbc:h2:tcp://localhost/~/jbpm-db");
        pds.getDriverProperties().put("driverClassName", "org.h2.Driver");
        pds.init();
        return pds;
    }

    public void setPersistence(boolean sessionPersistence) {
        this.sessionPersistence = sessionPersistence;
    }

    public boolean isPersistence() {
        return sessionPersistence;
    }

    public void setEntityManagerFactory(EntityManagerFactory emf) {
        JbpmBpmn2TestCase.emf = emf;
    }

    public void setPoolingDataSource(PoolingDataSource ds) {
        JbpmBpmn2TestCase.ds = ds;
    }

    /**
     * Can be called manually in method annotated with @BeforeClass.
     * 
     * @throws Exception
     */
    public static void setUpDataSource() throws Exception {
        setupDataSource = true;
        server.start();
        ds = setupPoolingDataSource();
        emf = Persistence
                .createEntityManagerFactory("org.jbpm.persistence.jpa");
    }

    @Before
    public void checkTest() {
        if (testReqPersistence != null
                && testReqPersistence.value() != sessionPersistence) {
            System.out.println("skipped - test is run only "
                    + (testReqPersistence.value() ? "with" : "without")
                    + " persistence");
            System.out.println(testReqPersistence.comment());
            Assume.assumeTrue(false);
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (setupDataSource) {
            if (ds != null) {
                try {
                    ds.close();
                } catch (Exception ex) {
                    // ignore
                }
                ds = null;
            }
            server.stop();
            DeleteDbFiles.execute("~", "jbpm-db", true);

            // Clean up possible transactions
            Transaction tx = TransactionManagerServices.getTransactionManager()
                    .getCurrentTransaction();
            if (tx != null) {
                int testTxState = tx.getStatus();
                if (testTxState != Status.STATUS_NO_TRANSACTION
                        && testTxState != Status.STATUS_ROLLEDBACK
                        && testTxState != Status.STATUS_COMMITTED) {
                    try {
                        tx.rollback();
                    } catch (Throwable t) {
                        // do nothing..
                    }
                    Assert.fail("Transaction had status "
                            + txStateName[testTxState]
                            + " at the end of the test.");
                }
            }
            
            if (emf != null) {
                try {
                    emf.close();
                } catch (Exception ex) {
                    // ignore
                }
                emf = null;
            }
        }
    }

    protected KieBase createKnowledgeBase(String... process) throws Exception {
        List<Resource> resources = new ArrayList<Resource>();
        for (int i = 0; i < process.length; ++i) {
            String p = process[i];
            resources.addAll(buildAndDumpBPMN2Process(process[i]));
        }
        return createKnowledgeBaseFromResources(resources.toArray(new Resource[resources.size()]));
    }

    protected KieBase createKnowledgeBaseWithoutDumper(String... process) throws Exception {
        Resource[] resources = new Resource[process.length];
        for (int i = 0; i < process.length; ++i) {
            String p = process[i];
            resources[i] = (ResourceFactory.newClassPathResource(p));
        }
        return createKnowledgeBaseFromResources(resources);
    }
    
    // Important to test this since persistence relies on this
    protected List<Resource> buildAndDumpBPMN2Process(String process) throws SAXException, IOException { 
        KnowledgeBuilderConfiguration conf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration();
        ((PackageBuilderConfiguration) conf).initSemanticModules();
        ((PackageBuilderConfiguration) conf).addSemanticModule(new BPMNSemanticModule());
        ((PackageBuilderConfiguration) conf).addSemanticModule(new BPMNDISemanticModule());
        
        Resource classpathResource = ResourceFactory.newClassPathResource(process);
        // Dump and reread
        XmlProcessReader processReader 
            = new XmlProcessReader(((PackageBuilderConfiguration) conf).getSemanticModules(), getClass().getClassLoader());
        List<Process> processes = processReader.read(this.getClass().getResourceAsStream("/" + process));
        List<Resource> resources = new ArrayList<Resource>();
        for (Process p : processes) {
            RuleFlowProcess ruleFlowProcess = (RuleFlowProcess) p;
            String dumpedString = XmlBPMNProcessDumper.INSTANCE.dump(ruleFlowProcess);
            Resource resource = ResourceFactory.newReaderResource(new StringReader(dumpedString));
            resource.setSourcePath(classpathResource.getSourcePath());
            resource.setTargetPath(classpathResource.getTargetPath());
            resources.add(resource);
        }
        return resources;
    }
    
    protected KieBase createKnowledgeBaseFromResources(Resource... process)
            throws Exception {

        KieServices ks = KieServices.Factory.get();
        KieRepository kr = ks.getRepository();
        if (process.length > 0) {
            KieFileSystem kfs = ks.newKieFileSystem();

            for (Resource p : process) {
                kfs.write(p);
            }

            KieBuilder kb = ks.newKieBuilder(kfs);

            kb.buildAll(); // kieModule is automatically deployed to KieRepository
                           // if successfully built.

            if (kb.getResults().hasMessages(Level.ERROR)) {
                throw new RuntimeException("Build Errors:\n"
                        + kb.getResults().toString());
            }
        }

        KieContainer kContainer = ks.newKieContainer(kr.getDefaultReleaseId());
        return kContainer.getKieBase();
    }
    
    protected KieBase createKnowledgeBaseFromDisc(String process) throws Exception {
        KieServices ks = KieServices.Factory.get();
        KieRepository kr = ks.getRepository();
        KieFileSystem kfs = ks.newKieFileSystem();
            
        Resource res = ResourceFactory.newClassPathResource(process);
        kfs.write(res);

        KieBuilder kb = ks.newKieBuilder(kfs);

        kb.buildAll(); // kieModule is automatically deployed to KieRepository
                       // if successfully built.

        if (kb.getResults().hasMessages(Level.ERROR)) {
            throw new RuntimeException("Build Errors:\n"
                    + kb.getResults().toString());
        }

        KieContainer kContainer = ks.newKieContainer(kr.getDefaultReleaseId());
        KieBase kbase =  kContainer.getKieBase();
        
        File packageFile = null;
        for (KiePackage pkg : kbase.getKiePackages() ) {
            packageFile = new File(System.getProperty("java.io.tmpdir") + File.separator + pkg.getName()+".pkg");
            packageFile.deleteOnExit();
            FileOutputStream out = new FileOutputStream(packageFile);
            try {
                DroolsStreamUtils.streamOut(out, pkg);
            } finally {
                out.close();
            }
            
            // store first package only
            break;
        }
        
        kfs.delete(res.getSourcePath());
        kfs.write(ResourceFactory.newFileResource(packageFile));

        kb = ks.newKieBuilder(kfs);
        kb.buildAll(); // kieModule is automatically deployed to KieRepository
                       // if successfully built.

        if (kb.getResults().hasMessages(Level.ERROR)) {
            throw new RuntimeException("Build Errors:\n"
                    + kb.getResults().toString());
        }
        
        kContainer = ks.newKieContainer(kr.getDefaultReleaseId());
        kbase =  kContainer.getKieBase();
        
        return kbase;
        
    }

    protected StatefulKnowledgeSession createKnowledgeSession(KieBase kbase)
            throws Exception {
        return createKnowledgeSession(kbase, null, null);
    }

    protected StatefulKnowledgeSession createKnowledgeSession(KieBase kbase,
            Environment env) throws Exception {
        return createKnowledgeSession(kbase, null, env);
    }

    protected StatefulKnowledgeSession createKnowledgeSession(KieBase kbase,
            KieSessionConfiguration conf, Environment env) throws Exception {
        StatefulKnowledgeSession result;
        if (conf == null) {
            conf = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();
        }
        // Do NOT use the Pseudo clock yet..
        // conf.setOption( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() )
        // );

        if (sessionPersistence) {
            if (env == null) {
                env = createEnvironment(emf);
            }
            result = JPAKnowledgeService.newStatefulKnowledgeSession(kbase,
                    conf, env);
            AuditLoggerFactory.newInstance(Type.JPA, result, null);
            JPAProcessInstanceDbLog.setEnvironment(result.getEnvironment());
        } else {
            if (env == null) {
                env = EnvironmentFactory.newEnvironment();
            }

            Properties defaultProps = new Properties();
            defaultProps.setProperty("drools.processSignalManagerFactory",
                    DefaultSignalManagerFactory.class.getName());
            defaultProps.setProperty("drools.processInstanceManagerFactory",
                    DefaultProcessInstanceManagerFactory.class.getName());
            conf = new SessionConfiguration(defaultProps);

            result = (StatefulKnowledgeSession) kbase.newKieSession(conf, env);
            logger = new WorkingMemoryInMemoryLogger(result);
        }
        return result;
    }

    protected StatefulKnowledgeSession createKnowledgeSession(String... process)
            throws Exception {
        KieBase kbase = createKnowledgeBase(process);
        return createKnowledgeSession(kbase);
    }
    
    protected KieSession restoreSession(KieSession ksession, boolean noCache) {
        if (sessionPersistence) {
            int id = ksession.getId();
            KieBase kbase = ksession.getKieBase();
            Environment env = null;
            if (noCache) {
                env = createEnvironment(emf);
            } else {
                env = ksession.getEnvironment();
            }
            KieSessionConfiguration config = ksession.getSessionConfiguration();
            StatefulKnowledgeSession result = JPAKnowledgeService.loadStatefulKnowledgeSession(id, kbase, config, env);
            AuditLoggerFactory.newInstance(Type.JPA, result, null);
            ksession.dispose();
            return result;
        } else {
            return ksession;
        }
    }

    protected KieSession restoreSession(KieSession ksession) {
        return ksession;
    }

    protected StatefulKnowledgeSession restoreSession(StatefulKnowledgeSession ksession) {
        return ksession;
    }

    protected Environment createEnvironment(EntityManagerFactory emf) {
        Environment env = EnvironmentFactory.newEnvironment();
        env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
        env.set(EnvironmentName.TRANSACTION_MANAGER,
                TransactionManagerServices.getTransactionManager());
        return env;
    }

    public void assertProcessInstanceCompleted(ProcessInstance processInstance) {
        assertTrue("Process instance has not been completed.", assertProcessInstanceState(ProcessInstance.STATE_COMPLETED, processInstance));
    }

    public void assertProcessInstanceAborted(ProcessInstance processInstance) {
        assertTrue("Process instance has not been aborted.", assertProcessInstanceState(ProcessInstance.STATE_ABORTED, processInstance));
    }

    public void assertProcessInstanceActive(ProcessInstance processInstance) {
        assertTrue("Process instance is not active.", assertProcessInstanceState(ProcessInstance.STATE_ACTIVE, processInstance)
                || assertProcessInstanceState(ProcessInstance.STATE_PENDING, processInstance));
    }

    public void assertProcessInstanceFinished(ProcessInstance processInstance,
            KieSession ksession) {
        assertNull("Process instance has not been finished.", ksession.getProcessInstance(processInstance.getId()));
    }

    public void assertNodeActive(long processInstanceId, KieSession ksession,
            String... name) {
        List<String> names = new ArrayList<String>();
        for (String n : name) {
            names.add(n);
        }
        ProcessInstance processInstance = ksession
                .getProcessInstance(processInstanceId);
        if (processInstance instanceof WorkflowProcessInstance) {
            assertNodeActive((WorkflowProcessInstance) processInstance, names);
        }
        if (!names.isEmpty()) {
            String s = names.get(0);
            for (int i = 1; i < names.size(); i++) {
                s += ", " + names.get(i);
            }
            fail("Node(s) not active: " + s);
        }
    }

    private void assertNodeActive(NodeInstanceContainer container,
            List<String> names) {
        for (NodeInstance nodeInstance : container.getNodeInstances()) {
            String nodeName = nodeInstance.getNodeName();
            if (names.contains(nodeName)) {
                names.remove(nodeName);
            }
            if (nodeInstance instanceof NodeInstanceContainer) {
                assertNodeActive((NodeInstanceContainer) nodeInstance, names);
            }
        }
    }

    public void assertNodeTriggered(long processInstanceId, String... nodeNames) {
        List<String> names = getNotTriggeredNodes(processInstanceId, nodeNames);
        if (!names.isEmpty()) {
            String s = names.get(0);
            for(int i = 1; i < names.size(); i++) {
                s += ", " + names.get(i);
            }
            fail("Node(s) not executed: " + s);
        }
    }

    public void assertNotNodeTriggered(long processInstanceId,
            String... nodeNames) {
        List<String> names = getNotTriggeredNodes(processInstanceId, nodeNames);
        assertTrue(Arrays.equals(names.toArray(), nodeNames));
    }
    
    public int getNumberOfNodeTriggered(long processInstanceId,
            String node) {
        int counter = 0;
        if (sessionPersistence) {
            List<NodeInstanceLog> logs = JPAProcessInstanceDbLog.findNodeInstances(processInstanceId);
            if (logs != null) {
                for (NodeInstanceLog l : logs) {
                    String nodeName = l.getNodeName();
                    if ((l.getType() == NodeInstanceLog.TYPE_ENTER 
                            || l.getType() == NodeInstanceLog.TYPE_EXIT)
                            && node.equals(nodeName)) {
                        counter++;
                    }
                }
            }
        } else {
            for (LogEvent event : logger.getLogEvents()) {
                if (event instanceof RuleFlowNodeLogEvent) {
                    String nodeName = ((RuleFlowNodeLogEvent) event).getNodeName();
                    if (node.equals(nodeName)) {
                        counter++;
                    }
                }
            }
        }
        return counter;
    }
    
    public int getNumberOfProcessInstances(String processId) {
        int counter = 0;
        if (sessionPersistence) {
            List<ProcessInstanceLog> logs = JPAProcessInstanceDbLog.findProcessInstances(processId);
            if (logs != null) {
                return logs.size();
            }
        } else {
            LogEvent [] events = logger.getLogEvents().toArray(new LogEvent[0]);
            for (LogEvent event : events ) { 
                if (event.getType() == LogEvent.BEFORE_RULEFLOW_CREATED) {
                    if(((RuleFlowLogEvent) event).getProcessId().equals(processId)) {
                        counter++;                    
                    }
                }
            }
        }
        return counter;
    }
    
    protected boolean assertProcessInstanceState(int state, ProcessInstance processInstance) {
        if (sessionPersistence) {
            ProcessInstanceLog log = JPAProcessInstanceDbLog.findProcessInstance(processInstance.getId());
            if (log != null) {
                return log.getStatus() == state;
            }
        } else {
            return processInstance.getState() == state;
        } 
        
        return false;
    }

    private List<String> getNotTriggeredNodes(long processInstanceId,
            String... nodeNames) {
        List<String> names = new ArrayList<String>();
        for (String nodeName : nodeNames) {
            names.add(nodeName);
        }
        if (sessionPersistence) {
            List<NodeInstanceLog> logs = JPAProcessInstanceDbLog
                    .findNodeInstances(processInstanceId);
            if (logs != null) {
                for (NodeInstanceLog l : logs) {
                    String nodeName = l.getNodeName();
                    if ((l.getType() == NodeInstanceLog.TYPE_ENTER || l
                            .getType() == NodeInstanceLog.TYPE_EXIT)
                            && names.contains(nodeName)) {
                        names.remove(nodeName);
                    }
                }
            }
        } else {
            for (LogEvent event : logger.getLogEvents()) {
                if (event instanceof RuleFlowNodeLogEvent) {
                    String nodeName = ((RuleFlowNodeLogEvent) event)
                            .getNodeName();
                    if (names.contains(nodeName)) {
                        names.remove(nodeName);
                    }
                }
            }
        }
        return names;
    }

    protected void clearHistory() {
        if (sessionPersistence) {
            try {
                JPAProcessInstanceDbLog.clear();
            } catch(Exception e) {
                
            }
        } else {
            if (logger != null) {
                logger.clear();
            }
        }
    }

    public void assertProcessVarExists(ProcessInstance process,
            String... processVarNames) {
        WorkflowProcessInstanceImpl instance = (WorkflowProcessInstanceImpl) process;
        List<String> names = new ArrayList<String>();
        for (String nodeName : processVarNames) {
            names.add(nodeName);
        }

        for (String pvar : instance.getVariables().keySet()) {
            if (names.contains(pvar)) {
                names.remove(pvar);
            }
        }

        if (!names.isEmpty()) {
            String s = names.get(0);
            for (int i = 1; i < names.size(); i++) {
                s += ", " + names.get(i);
            }
            fail("Process Variable(s) do not exist: " + s);
        }

    }
    
    public void assertProcessVarValue(ProcessInstance processInstance, String varName, String varValue) {
        boolean result = false;
        String actualValue = null;
        if (sessionPersistence) {
            List<VariableInstanceLog> log = JPAProcessInstanceDbLog.findVariableInstances(processInstance.getId(), varName);
            if (log != null && !log.isEmpty()) {
                actualValue = log.get(log.size()-1).getValue();
                
            }
        } else {
            Object value = ((WorkflowProcessInstanceImpl) processInstance).getVariable(varName);
            if (value != null) {
                actualValue = value.toString();
            }
        } 
        result = varName.equals(actualValue);
        if(!result) {
            fail("Variable " + varName + " value missmatch - expected " + varValue + " actual " + actualValue);
        }
    }

    public void assertNodeExists(ProcessInstance process, String... nodeNames) {
        WorkflowProcessInstanceImpl instance = (WorkflowProcessInstanceImpl) process;
        List<String> names = new ArrayList<String>();
        for (String nodeName : nodeNames) {
            names.add(nodeName);
        }

        for (Node node : instance.getNodeContainer().getNodes()) {
            if (names.contains(node.getName())) {
                names.remove(node.getName());
            }
        }

        if (!names.isEmpty()) {
            String s = names.get(0);
            for (int i = 1; i < names.size(); i++) {
                s += ", " + names.get(i);
            }
            fail("Node(s) do not exist: " + s);
        }
    }

    public void assertNumOfIncommingConnections(ProcessInstance process,
            String nodeName, int num) {
        assertNodeExists(process, nodeName);
        WorkflowProcessInstanceImpl instance = (WorkflowProcessInstanceImpl) process;
        for (Node node : instance.getNodeContainer().getNodes()) {
            if (node.getName().equals(nodeName)) {
                if (node.getIncomingConnections().size() != num) {
                    fail("Expected incomming connections: " + num + " - found "
                            + node.getIncomingConnections().size());
                } else {
                    break;
                }
            }
        }
    }

    public void assertNumOfOutgoingConnections(ProcessInstance process,
            String nodeName, int num) {
        assertNodeExists(process, nodeName);
        WorkflowProcessInstanceImpl instance = (WorkflowProcessInstanceImpl) process;
        for (Node node : instance.getNodeContainer().getNodes()) {
            if (node.getName().equals(nodeName)) {
                if (node.getOutgoingConnections().size() != num) {
                    fail("Expected outgoing connections: " + num + " - found "
                            + node.getOutgoingConnections().size());
                } else {
                    break;
                }
            }
        }
    }

    public void assertVersionEquals(ProcessInstance process, String version) {
        WorkflowProcessInstanceImpl instance = (WorkflowProcessInstanceImpl) process;
        if (!instance.getWorkflowProcess().getVersion().equals(version)) {
            fail("Expected version: " + version + " - found "
                    + instance.getWorkflowProcess().getVersion());
        }
    }

    public void assertProcessNameEquals(ProcessInstance process, String name) {
        WorkflowProcessInstanceImpl instance = (WorkflowProcessInstanceImpl) process;
        if (!instance.getWorkflowProcess().getName().equals(name)) {
            fail("Expected name: " + name + " - found "
                    + instance.getWorkflowProcess().getName());
        }
    }

    public void assertPackageNameEquals(ProcessInstance process,
            String packageName) {
        WorkflowProcessInstanceImpl instance = (WorkflowProcessInstanceImpl) process;
        if (!instance.getWorkflowProcess().getPackageName().equals(packageName)) {
            fail("Expected package name: " + packageName + " - found "
                    + instance.getWorkflowProcess().getPackageName());
        }
    }

    public Object eval(Reader reader, Map vars) {
        try {
            return eval(toString(reader), vars);
        } catch (IOException e) {
            throw new RuntimeException("Exception Thrown", e);
        }
    }

    private String toString(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        int charValue;

        while ((charValue = reader.read()) != -1) {
            sb.append((char) charValue);
        }
        return sb.toString();
    }

    public Object eval(String str, Map vars) {

        ParserContext context = new ParserContext();
        context.addPackageImport("org.jbpm.task");
        context.addPackageImport("org.jbpm.task.service");
        context.addPackageImport("org.jbpm.task.query");
        context.addPackageImport("java.util");

        vars.put("now", new Date());
        return MVEL.executeExpression(MVEL.compileExpression(str, context),
                vars);
    }
    
    protected void assertProcessInstanceCompleted(long processInstanceId, KieSession ksession) {
        assertNull(ksession.getProcessInstance(processInstanceId));
    }

    protected void assertProcessInstanceAborted(long processInstanceId, KieSession ksession) {
        assertNull(ksession.getProcessInstance(processInstanceId));
    }

    protected void assertProcessInstanceActive(long processInstanceId, KieSession ksession) {
        assertNotNull(ksession.getProcessInstance(processInstanceId));
    }

    private static class H2Server {
        private Server server;

        public synchronized void start() {
            if (server == null || !server.isRunning(false)) {
                try {
                    DeleteDbFiles.execute("~", "jbpm-db", true);
                    server = Server.createTcpServer(new String[0]);
                    server.start();
                } catch (SQLException e) {
                    throw new RuntimeException(
                            "Cannot start h2 server database", e);
                }
            }
        }

        public synchronized void finalize() throws Throwable {
            stop();
            super.finalize();
        }

        public void stop() {
            if (server != null) {
                server.stop();
                server.shutdown();
                DeleteDbFiles.execute("~", "jbpm-db", true);
                server = null;
            }
        }
    }

}
