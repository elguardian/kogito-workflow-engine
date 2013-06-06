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

import java.util.Properties;

import javax.persistence.EntityManagerFactory;

import org.drools.core.impl.EnvironmentFactory;
import org.jbpm.process.core.timer.GlobalSchedulerService;
import org.jbpm.runtime.manager.api.SchedulerProvider;
import org.jbpm.runtime.manager.impl.mapper.InMemoryMapper;
import org.kie.api.KieBase;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderError;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.runtime.manager.Mapper;
import org.kie.internal.runtime.manager.RegisterableItemsFactory;
import org.kie.internal.runtime.manager.RuntimeEnvironment;
import org.kie.internal.task.api.UserGroupCallback;

/**
 * The most basic implementation of <code>RuntimeEnvironment</code> that at the same time serves as base 
 * implementation for all extensions. Encapsulates all important configuration that <code>RuntimeManager</code>
 * requires for execution.
 * <ul>
 *  <li>EntityManagerFactory - shared for all runtime engine build based on same <code>RuntimeEnvironment</code></li>
 *  <li>Environment - Drools/jBPM environment object - will be cloned for every <code>RuntimeEngine</code></li>
 *  <li>KieSessionConfiguration - will be build passed on defined properties - cloned for every <code>RuntimeEngine</code></li>
 *  <li>KieBase - resulting knowledge base build on given assets or returned if it was preset</li>
 *  <li>RegisterableItemsFactory - factory used to provide listeners and work item handlers</li>
 *  <li>Mapper - mapper used to keep context information</li>
 *  <li>UserGroupCallback - user group callback, if not given null will be returned</li>
 *  <li>GlobalSchedulerService - since this environment implements <code>SchedulerProvider</code>
 *  it allows to get <code>GlobalTimerService</code> if available</li>
 * </ul>
 *
 */
public class SimpleRuntimeEnvironment implements RuntimeEnvironment, SchedulerProvider {
    
    protected boolean usePersistence;
    protected EntityManagerFactory emf;
    
    protected Environment environment;
    protected KieSessionConfiguration configuration;
    protected KieBase kbase;
    protected KnowledgeBuilder kbuilder;
    protected RegisterableItemsFactory registerableItemsFactory;
    protected Mapper mapper;
    protected UserGroupCallback userGroupCallback;
    protected GlobalSchedulerService schedulerService;
    
    protected Properties sessionConfigProperties;
    
    public SimpleRuntimeEnvironment() {
        this(new SimpleRegisterableItemsFactory());        
    }
    
    public SimpleRuntimeEnvironment(RegisterableItemsFactory registerableItemsFactory) {
        this.environment = EnvironmentFactory.newEnvironment();
        this.kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        this.registerableItemsFactory = registerableItemsFactory;

    }
    
    public void init() {
        if (this.mapper == null) {
            this.mapper = new InMemoryMapper();
        }
    }
    
    /**
     * Adds given asset to knowledge builder to produce KieBase
     * @param resource asset to be added 
     * @param type type of the asset
     */
    public void addAsset(Resource resource, ResourceType type) {
        this.kbuilder.add(resource, type);
        if (this.kbuilder.hasErrors()) {            
            StringBuffer errorMessage = new StringBuffer();
            for( KnowledgeBuilderError error : kbuilder.getErrors()) {
                errorMessage.append(error.getMessage() + ",");
            }
            this.kbuilder.undo();
            throw new IllegalArgumentException("Cannot add asset: " + errorMessage.toString());
        }
    }
    
    /**
     * Adds element to the drools/jbpm environment - the value must be thread save as it will be shared between all 
     * <code>RuntimeEngine</code> instances
     * @param name name of the environment entry
     * @param value value of the environment entry
     */
    public void addToEnvironment(String name, Object value) {
        this.environment.set(name, value);
    }
    
    /**
     * Adds configuration property that will be part of <code>KieSessionConfiguration</code>
     * @param name name of the property
     * @param value value of the property
     */
    public void addToConfiguration(String name, String value) {
        if (this.sessionConfigProperties == null) {
            this.sessionConfigProperties = new Properties();
        }
        this.sessionConfigProperties.setProperty(name, value);
    }

    @Override
    public KieBase getKieBase() {
        if (this.kbase == null) {
            this.kbase = kbuilder.newKnowledgeBase();
        }
        return this.kbase;
    }

    @Override
    public Environment getEnvironment() {
        // this environment is like template always make a new copy when this method is called
        return copyEnvironment();
    }

    @Override
    public KieSessionConfiguration getConfiguration() {
        if (this.sessionConfigProperties != null) {
            return KnowledgeBaseFactory.newKnowledgeSessionConfiguration(this.sessionConfigProperties);
        }
        return null;
    }
    @Override
    public boolean usePersistence() {
        
        return this.usePersistence;
    }
    
    @Override
    public RegisterableItemsFactory getRegisterableItemsFactory() {
        return this.registerableItemsFactory;
    }
    
    @Override
    public void close() {

    }

    protected void addIfPresent(String name, Environment copy) {
        Object value = this.environment.get(name);
        if (value != null) {
            copy.set(name, value);
        }
    }
    
    protected Environment copyEnvironment() {
        Environment copy = EnvironmentFactory.newEnvironment();
        
        addIfPresent(EnvironmentName.ENTITY_MANAGER_FACTORY,copy);
        addIfPresent(EnvironmentName.CALENDARS, copy);
        addIfPresent(EnvironmentName.DATE_FORMATS, copy);
        addIfPresent(EnvironmentName.GLOBALS, copy);
        addIfPresent(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, copy);
        addIfPresent(EnvironmentName.PERSISTENCE_CONTEXT_MANAGER, copy);
        addIfPresent(EnvironmentName.TRANSACTION_MANAGER, copy);
        addIfPresent(EnvironmentName.TRANSACTION_SYNCHRONIZATION_REGISTRY, copy);
        addIfPresent(EnvironmentName.TRANSACTION, copy);
        
        return copy;
    }
    @Override
    public Mapper getMapper() {
        return this.mapper;
    }
    
    @Override
    public UserGroupCallback getUserGroupCallback() {
        return this.userGroupCallback;
    }
    
    public void setUserGroupCallback(UserGroupCallback userGroupCallback) {
        this.userGroupCallback = userGroupCallback;
    }

    public Properties getSessionConfigProperties() {
        return sessionConfigProperties;
    }
    public void setSessionConfigProperties(Properties sessionConfigProperties) {
        this.sessionConfigProperties = sessionConfigProperties;
    }

    public void setUsePersistence(boolean usePersistence) {
        this.usePersistence = usePersistence;
    }

    public void setKieBase(KieBase kbase) {
        this.kbase = kbase;
    }
    
    public void setMapper(Mapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public GlobalSchedulerService getSchedulerService() {
        return this.schedulerService;
    }
    
    public void setSchedulerService(GlobalSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setRegisterableItemsFactory(RegisterableItemsFactory registerableItemsFactory) {
        this.registerableItemsFactory = registerableItemsFactory;
    }
    
    public EntityManagerFactory getEmf() {
        return emf;
    }
    public void setEmf(EntityManagerFactory emf) {
        this.emf = emf;
    }
}
