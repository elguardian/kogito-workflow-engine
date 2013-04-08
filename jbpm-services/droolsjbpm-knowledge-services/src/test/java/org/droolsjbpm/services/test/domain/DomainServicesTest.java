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
package org.droolsjbpm.services.test.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.droolsjbpm.services.api.DomainManagerService;
import org.droolsjbpm.services.domain.entities.Domain;
import org.droolsjbpm.services.domain.entities.Organization;
import org.droolsjbpm.services.domain.entities.RuntimeId;
import org.droolsjbpm.services.impl.model.ProcessDesc;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jbpm.runtime.manager.impl.DefaultRuntimeEnvironment;
import org.jbpm.runtime.manager.impl.RuntimeEnvironmentBuilder;
import org.jbpm.runtime.manager.impl.SimpleRuntimeEnvironment;
import org.jbpm.runtime.manager.impl.cdi.InjectableRegisterableItemsFactory;
import org.jbpm.runtime.manager.util.TestUtil;
import org.jbpm.services.task.identity.JBossUserGroupCallbackImpl;
import org.jbpm.shared.services.api.FileException;
import org.jbpm.shared.services.api.FileService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.commons.java.nio.file.Path;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.manager.Context;
import org.kie.internal.runtime.manager.RuntimeEngine;
import org.kie.internal.runtime.manager.RuntimeEnvironment;
import org.kie.internal.runtime.manager.RuntimeManager;
import org.kie.internal.runtime.manager.RuntimeManagerFactory;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.kie.internal.task.api.TaskService;
import org.kie.internal.task.api.model.Status;
import org.kie.internal.task.api.model.TaskSummary;

import bitronix.tm.resource.jdbc.PoolingDataSource;

/**
 *
 * @author salaboy
 */
@RunWith(Arquillian.class)
public class DomainServicesTest {

    @Deployment()
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "domain-services.jar")
                .addPackage("org.jboss.seam.persistence") //seam-persistence
                .addPackage("org.jboss.seam.transaction") //seam-persistence
                .addPackage("org.jbpm.services.task")
                .addPackage("org.jbpm.services.task.wih") // work items org.jbpm.services.task.wih
                .addPackage("org.jbpm.services.task.annotations")
                .addPackage("org.jbpm.services.task.api")
                .addPackage("org.jbpm.services.task.impl")
                .addPackage("org.jbpm.services.task.events")
                .addPackage("org.jbpm.services.task.exception")
                .addPackage("org.jbpm.services.task.identity")
                .addPackage("org.jbpm.services.task.factories")
                .addPackage("org.jbpm.services.task.internals")
                .addPackage("org.jbpm.services.task.internals.lifecycle")
                .addPackage("org.jbpm.services.task.lifecycle.listeners")
                .addPackage("org.jbpm.services.task.query")
                .addPackage("org.jbpm.services.task.util")
                .addPackage("org.jbpm.services.task.commands") // This should not be required here
                .addPackage("org.jbpm.services.task.deadlines") // deadlines
                .addPackage("org.jbpm.services.task.deadlines.notifications.impl")
                .addPackage("org.jbpm.services.task.subtask")

                .addPackage("org.kie.internal.runtime")
                .addPackage("org.kie.internal.runtime.manager")
                .addPackage("org.kie.internal.runtime.manager.cdi.qualifier")
                
                .addPackage("org.jbpm.runtime.manager")
                .addPackage("org.jbpm.runtime.manager.impl")
                .addPackage("org.jbpm.runtime.manager.impl.cdi")
                .addPackage("org.jbpm.runtime.manager.impl.cdi.qualifier")
                
                .addPackage("org.jbpm.runtime.manager.impl.context")
                .addPackage("org.jbpm.runtime.manager.impl.factory")
                .addPackage("org.jbpm.runtime.manager.impl.jpa")
                .addPackage("org.jbpm.runtime.manager.impl.manager")
                .addPackage("org.jbpm.runtime.manager.mapper")
                .addPackage("org.jbpm.runtime.manager.impl.task")
                .addPackage("org.jbpm.runtime.manager.impl.tx")
                
                .addPackage("org.jbpm.shared.services.api")
                .addPackage("org.jbpm.shared.services.impl")
                
                .addPackage("org.droolsjbpm.services.api")
                .addPackage("org.droolsjbpm.services.impl")
                .addPackage("org.droolsjbpm.services.api.bpmn2")
                .addPackage("org.droolsjbpm.services.impl.bpmn2")
                .addPackage("org.droolsjbpm.services.impl.event.listeners")
                .addPackage("org.droolsjbpm.services.impl.audit")
                .addPackage("org.droolsjbpm.services.impl.util")
                
                .addPackage("org.droolsjbpm.services.impl.vfs")
                
                .addPackage("org.droolsjbpm.services.impl.example")
                .addPackage("org.kie.commons.java.nio.fs.jgit")
                .addPackage("org.droolsjbpm.services.test") // Identity Provider Test Impl here
                .addAsResource("jndi.properties", "jndi.properties")
                .addAsManifestResource("META-INF/persistence.xml", ArchivePaths.create("persistence.xml"))
                //                .addAsManifestResource("META-INF/Taskorm.xml", ArchivePaths.create("Taskorm.xml"))
                .addAsManifestResource("META-INF/beans.xml", ArchivePaths.create("beans.xml"));

    }
    private static PoolingDataSource pds;

    @BeforeClass
    public static void setup() {
        TestUtil.cleanupSingletonSessionId();
        pds = TestUtil.setupPoolingDataSource();
        Properties props = new Properties();
        props.setProperty("salaboy", "user");

    }

    @AfterClass
    public static void teardown() {
        pds.close();
    }
    
    @After
    public void tearDownTest(){
        int removeAllTasks = taskService.removeAllTasks();
        System.out.println(">>> Removed Tasks > "+removeAllTasks);
    }
    /*
     * end of initialization code, tests start here
     */
    @Inject
    private RuntimeManagerFactory managerFactory;
    @Inject
    private EntityManagerFactory emf;
    @Inject
    private FileService fs;
    @Inject
    protected DomainManagerService domainService;
    
    @Inject
    protected TaskService taskService;
    @Inject
    private BeanManager beanManager;

    @Test
    public void simpleExecutionTest() {
        assertNotNull(managerFactory);
        String path = "processes/support/";
        RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.getDefault()
                .entityManagerFactory(emf)
                .registerableItemsFactory(InjectableRegisterableItemsFactory.getFactory(beanManager, null));
        Iterable<Path> loadProcessFiles = null;

        try {
            loadProcessFiles = fs.loadFilesByType(path, ".+bpmn[2]?$");
        } catch (FileException ex) {
            Logger.getLogger(DomainServicesTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (Path p : loadProcessFiles) {
            builder.addAsset(ResourceFactory.newClassPathResource( fs.getRepositoryRoot() + "/" + path + p.getFileName().toString()), ResourceType.BPMN2);
        }

        RuntimeManager manager = managerFactory.newSingletonRuntimeManager(builder.get());
        testProcessStartOnManager(manager, EmptyContext.get());

        manager.close();

    }

    @Test
    public void runtimeInDomainTest() {

        Map<String, List<RuntimeEngine>> domainsMap = new HashMap<String, List<RuntimeEngine>>();

        Organization organization = new Organization();
        organization.setName("JBoss");
        Domain domain = new Domain();
        domain.setName("My First Domain");
        List<RuntimeId> runtimes = new ArrayList<RuntimeId>();
        RuntimeId runtime1 = new RuntimeId();
        runtime1.setReference("processes/support/");
        runtime1.setDomain(domain);
        runtimes.add(runtime1);
        domain.setRuntimes(runtimes);
        domain.setOrganization(organization);
        List<Domain> domains = new ArrayList<Domain>();
        domains.add(domain);
        organization.setDomains(domains);

        domainService.storeOrganization(organization);
        Organization org = domainService.getOrganizationById(organization.getId());

        for (Domain d : org.getDomains()) {
            for (RuntimeId r : d.getRuntimes()) {
                String reference = r.getReference();
                // Create Runtime Manager Based on the Reference
                RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.getDefault()
                        .entityManagerFactory(emf);
                Iterable<Path> loadProcessFiles = null;

                try {
                    loadProcessFiles = fs.loadFilesByType(reference, ".+bpmn[2]?$");
                } catch (FileException ex) {
                    Logger.getLogger(DomainServicesTest.class.getName()).log(Level.SEVERE, null, ex);
                }
                for (Path p : loadProcessFiles) {
                    builder.addAsset(ResourceFactory.newClassPathResource(fs.getRepositoryRoot() + "/" + reference + p.getFileName().toString()), ResourceType.BPMN2);
                }
                // Parse and get the Metadata for all the assets

                RuntimeManager manager = managerFactory.newSingletonRuntimeManager(builder.get(), d.getName());
                org.kie.internal.runtime.manager.RuntimeEngine runtime = manager.getRuntimeEngine(EmptyContext.get());
                assertNotNull(runtime);
                if (domainsMap.get(d.getName()) == null) {
                    domainsMap.put(d.getName(), new ArrayList<RuntimeEngine>());
                }
                domainsMap.get(d.getName()).add(runtime);
            }

        }
        Domain domainByName = domainService.getDomainByName("My First Domain");

        List<RuntimeEngine> domainRuntimes = domainsMap.get(domainByName.getName());
        RuntimeEngine runtime = domainRuntimes.get(0);
        List<TaskSummary> salaboysTasks = runtime.getTaskService().getTasksAssignedAsPotentialOwner("salaboy", "en-UK");
        assertEquals(0, salaboysTasks.size());

        runtime.getKieSession().startProcess("support.process");
        
        salaboysTasks = runtime.getTaskService().getTasksAssignedAsPotentialOwner("salaboy", "en-UK");
        assertEquals(1, salaboysTasks.size());
        
                       
    }
    
    @Test
    public void initDomainTest(){
        Organization organization = new Organization();
        organization.setName("JBoss");
        Domain domain = new Domain();
        domain.setName("My Second Domain");
        List<RuntimeId> runtimes = new ArrayList<RuntimeId>();
        RuntimeId runtime1 = new RuntimeId();
        runtime1.setReference("processes/support/");
        runtime1.setDomain(domain);
        runtimes.add(runtime1);
        domain.setRuntimes(runtimes);
        domain.setOrganization(organization);
        List<Domain> domains = new ArrayList<Domain>();
        domains.add(domain);
        organization.setDomains(domains);

        domainService.storeOrganization(organization);
        
        domainService.initDomain(domain.getId());
        RuntimeManager runtimesByDomain = domainService.getRuntimesByDomain(domain.getName());
        RuntimeEngine runtime = runtimesByDomain.getRuntimeEngine(ProcessInstanceIdContext.get());
        runtime.getKieSession().startProcess("support.process");
        
        List<TaskSummary> tasks = runtime.getTaskService().getTasksAssignedAsPotentialOwner("salaboy", "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        Collection<ProcessDesc> processesByDomainName = domainService.getProcessesByDomainName("My Second Domain");
        assertNotNull(processesByDomainName);
        
        assertEquals(1, processesByDomainName.size());
    
    }
    
    

    private void testProcessStartOnManager(RuntimeManager manager, Context context) {
        assertNotNull(manager);

        org.kie.internal.runtime.manager.RuntimeEngine runtime = manager.getRuntimeEngine(context);
        assertNotNull(runtime);

        KieSession ksession = runtime.getKieSession();
        assertNotNull(ksession);

        ProcessInstance processInstance = ksession.startProcess("support.process");
        assertNotNull(processInstance);

        List<Status> statuses = new ArrayList<Status>();
        statuses.add(Status.Reserved);
        List<TaskSummary> tasks = runtime.getTaskService().getTasksOwned("salaboy", statuses, "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        runtime.getTaskService().start(tasks.get(0).getId(), "salaboy");

        runtime.getTaskService().complete(tasks.get(0).getId(), "salaboy", null);
        List<TaskSummary> tasksAssignedAsPotentialOwner = runtime.getTaskService().getTasksAssignedAsPotentialOwner("salaboy", "en-UK");

        assertEquals(1, tasksAssignedAsPotentialOwner.size());

        assertNotNull(runtime.getKieSession().getProcessInstance(processInstance.getId()));

    }
}
