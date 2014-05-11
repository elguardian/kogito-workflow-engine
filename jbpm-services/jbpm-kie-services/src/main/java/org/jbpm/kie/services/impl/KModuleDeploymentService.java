package org.jbpm.kie.services.impl;

import static org.kie.scanner.MavenRepository.getMavenRepository;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.apache.commons.codec.binary.Base64;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.KieContainerImpl;
import org.drools.core.marshalling.impl.ClassObjectMarshallingStrategyAcceptor;
import org.drools.core.marshalling.impl.SerializablePlaceholderResolverStrategy;
import org.drools.core.util.StringUtils;
import org.jbpm.kie.services.api.IdentityProvider;
import org.jbpm.kie.services.api.Kjar;
import org.jbpm.kie.services.api.bpmn2.BPMN2DataService;
import org.jbpm.kie.services.impl.model.ProcessAssetDesc;
import org.jbpm.process.audit.event.AuditEventBuilder;
import org.jbpm.runtime.manager.impl.cdi.InjectableRegisterableItemsFactory;
import org.jbpm.runtime.manager.impl.deploy.DeploymentDescriptorManager;
import org.jbpm.runtime.manager.impl.deploy.DeploymentDescriptorMerger;
import org.jbpm.runtime.manager.impl.jpa.EntityManagerFactoryManager;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.internal.deployment.DeployedAsset;
import org.kie.internal.deployment.DeployedUnit;
import org.kie.internal.deployment.DeploymentUnit;
import org.kie.internal.runtime.conf.DeploymentDescriptor;
import org.kie.internal.runtime.conf.MergeMode;
import org.kie.internal.runtime.conf.NamedObjectModel;
import org.kie.internal.runtime.conf.ObjectModel;
import org.kie.internal.runtime.conf.ObjectModelResolver;
import org.kie.internal.runtime.conf.ObjectModelResolverProvider;
import org.kie.internal.runtime.conf.PersistenceMode;
import org.kie.scanner.MavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Kjar
public class KModuleDeploymentService extends AbstractDeploymentService {

    private static Logger logger = LoggerFactory.getLogger(KModuleDeploymentService.class);
    
    private static final String DEFAULT_KBASE_NAME = "defaultKieBase";

    @Inject
    private BeanManager beanManager;    
    @Inject
    private IdentityProvider identityProvider;
    @Inject
    private BPMN2DataService bpmn2Service;
    
    private DeploymentDescriptorMerger merger = new DeploymentDescriptorMerger();
 
    @PostConstruct
    public void onInit() {
    	EntityManagerFactoryManager.get().addEntityManagerFactory("org.jbpm.domain", getEmf());
    }
    
    @Override
    public void deploy(DeploymentUnit unit) {
        super.deploy(unit);
        if (!(unit instanceof KModuleDeploymentUnit)) {
            throw new IllegalArgumentException("Invalid deployment unit provided - " + unit.getClass().getName());
        }
        KModuleDeploymentUnit kmoduleUnit = (KModuleDeploymentUnit) unit;
        KieServices ks = KieServices.Factory.get();
        DeployedUnitImpl deployedUnit = new DeployedUnitImpl(unit);
        ReleaseId releaseId = ks.newReleaseId(kmoduleUnit.getGroupId(), kmoduleUnit.getArtifactId(), kmoduleUnit.getVersion());

        MavenRepository repository = getMavenRepository();
        repository.resolveArtifact(releaseId.toExternalForm());

        KieContainer kieContainer = ks.newKieContainer(releaseId);

        String kbaseName = kmoduleUnit.getKbaseName();
        if (StringUtils.isEmpty(kbaseName)) {
            KieBaseModel defaultKBaseModel = ((KieContainerImpl)kieContainer).getKieProject().getDefaultKieBaseModel();
            if (defaultKBaseModel != null) {
                kbaseName = defaultKBaseModel.getName();
            } else {
                kbaseName = DEFAULT_KBASE_NAME;
            }
        }
        InternalKieModule module = (InternalKieModule) ((KieContainerImpl)kieContainer).getKieModuleForKBase(kbaseName);
        if (module == null) {
            throw new IllegalStateException("Cannot find kbase, either it does not exist or there are multiple default kbases in kmodule.xml");
        }

        Map<String, String> formsData = new HashMap<String, String>();
        Collection<String> files = module.getFileNames();
        
        processResources(module, formsData, files, kieContainer, kmoduleUnit, deployedUnit, releaseId);
        
        if (module.getKieDependencies() != null) {
	        Collection<InternalKieModule> dependencies = module.getKieDependencies().values();
	        for (InternalKieModule depModule : dependencies) {
	        	
	        	logger.debug("Processing dependency module " + depModule.getReleaseId());
	        	files = depModule.getFileNames();
	        	
	        	processResources(depModule, formsData, files, kieContainer, kmoduleUnit, deployedUnit, depModule.getReleaseId());
	        }
        }


        KieBase kbase = kieContainer.getKieBase(kbaseName);        

        AuditEventBuilder auditLoggerBuilder = setupAuditLogger(identityProvider, unit.getIdentifier());

        RuntimeEnvironmentBuilder builder = boostrapRuntimeEnvironmentBuilder(
        		kmoduleUnit, deployedUnit, kieContainer, kmoduleUnit.getMergeMode())
                .knowledgeBase(kbase)
                .classLoader(kieContainer.getClassLoader());
        if (beanManager != null) {
            builder.registerableItemsFactory(InjectableRegisterableItemsFactory.getFactory(beanManager, auditLoggerBuilder, kieContainer,
                    kmoduleUnit.getKsessionName()));
        }
        commonDeploy(unit, deployedUnit, builder.get());
        kmoduleUnit.setDeployed(true);
    }

    
    
    @Override
	public void undeploy(DeploymentUnit unit) {
    	if (!(unit instanceof KModuleDeploymentUnit)) {
            throw new IllegalArgumentException("Invalid deployment unit provided - " + unit.getClass().getName());
        }
        KModuleDeploymentUnit kmoduleUnit = (KModuleDeploymentUnit) unit;
		super.undeploy(unit);
		
		KieServices ks = KieServices.Factory.get();
		ReleaseId releaseId = ks.newReleaseId(kmoduleUnit.getGroupId(), kmoduleUnit.getArtifactId(), kmoduleUnit.getVersion());
		ks.getRepository().removeKieModule(releaseId);
	}

    protected RuntimeEnvironmentBuilder boostrapRuntimeEnvironmentBuilder(KModuleDeploymentUnit deploymentUnit,
    		DeployedUnit deployedUnit, KieContainer kieContainer, MergeMode mode) {
    	DeploymentDescriptor descriptor = deploymentUnit.getDeploymentDescriptor();
    	if (descriptor == null) {
	    	DeploymentDescriptorManager descriptorManager = new DeploymentDescriptorManager("org.jbpm.domain");
	    	List<DeploymentDescriptor> descriptorHierarchy = descriptorManager.getDeploymentDescriptorHierarchy(kieContainer);
	    	
			descriptor = merger.merge(descriptorHierarchy, mode);
			deploymentUnit.setDeploymentDescriptor(descriptor);
    	} else if (descriptor != null && !deploymentUnit.isDeployed()) {
    		DeploymentDescriptorManager descriptorManager = new DeploymentDescriptorManager("org.jbpm.domain");
	    	List<DeploymentDescriptor> descriptorHierarchy = descriptorManager.getDeploymentDescriptorHierarchy(kieContainer);
	    	
	    	descriptorHierarchy.add(0, descriptor);
	    	descriptor = merger.merge(descriptorHierarchy, mode);
			deploymentUnit.setDeploymentDescriptor(descriptor);
    	}
    	
		// first set on unit the strategy
		deploymentUnit.setStrategy(descriptor.getRuntimeStrategy());
		
		// setting up runtime environment via builder
		RuntimeEnvironmentBuilder builder = null;
		if (descriptor.getPersistenceMode() == PersistenceMode.NONE) {
			builder = RuntimeEnvironmentBuilder.Factory.get().newDefaultInMemoryBuilder();
		} else {
			builder = RuntimeEnvironmentBuilder.Factory.get().newDefaultBuilder();
		}
		// populate various properties of the builder
		EntityManagerFactory emf = EntityManagerFactoryManager.get().getOrCreate(descriptor.getPersistenceUnit());
		builder.entityManagerFactory(emf);
		
		Map<String, Object> contaxtParams = new HashMap<String, Object>();
		contaxtParams.put("entityManagerFactory", emf);
		// process object models that are globally configured (environment entries, session configuration)
		for (NamedObjectModel model : descriptor.getEnvironmentEntries()) {
			Object entry = getInstanceFromModel(model, kieContainer, contaxtParams);
			builder.addEnvironmentEntry(model.getName(), entry);
		}
		
		for (NamedObjectModel model : descriptor.getConfiguration()) {
			Object entry = getInstanceFromModel(model, kieContainer, contaxtParams);
			builder.addConfiguration(model.getName(), (String) entry);
		}
		ObjectMarshallingStrategy[] mStrategies = new ObjectMarshallingStrategy[descriptor.getMarshallingStrategies().size() + 1];
		int index = 0;
		for (ObjectModel model : descriptor.getMarshallingStrategies()) {
			Object strategy = getInstanceFromModel(model, kieContainer, contaxtParams);
			mStrategies[index] = (ObjectMarshallingStrategy)strategy;
			index++;
		}
		// lastly add the main default strategy
		mStrategies[index] = new SerializablePlaceholderResolverStrategy(ClassObjectMarshallingStrategyAcceptor.DEFAULT);
		builder.addEnvironmentEntry(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, mStrategies);
		
		builder.addEnvironmentEntry("KieDeploymentDescriptor", descriptor);
		
		// populate all assets with roles for this deployment unit
		if (descriptor.getRequiredRoles() != null && !descriptor.getRequiredRoles().isEmpty()) {
			for (DeployedAsset desc : deployedUnit.getDeployedAssets()) {
				if (desc instanceof ProcessAssetDesc) {
					((ProcessAssetDesc) desc).setRoles(descriptor.getRequiredRoles());
				}
			}
		}
    		
    	return builder;
    }

    
    protected Object getInstanceFromModel(ObjectModel model, KieContainer kieContainer, Map<String, Object> contaxtParams) {
    	ObjectModelResolver resolver = ObjectModelResolverProvider.get(model.getResolver());
		if (resolver == null) {
			logger.warn("Unable to find ObjectModelResolver for {}", model.getResolver());
		}
		
		return resolver.getInstance(model, kieContainer.getClassLoader(), contaxtParams);
    }

	protected void processResources(InternalKieModule module, Map<String, String> formsData, Collection<String> files,
    		KieContainer kieContainer, DeploymentUnit unit, DeployedUnitImpl deployedUnit, ReleaseId releaseId) {
        for (String fileName : files) {
            if(fileName.matches(".+bpmn[2]?$")) {
                ProcessAssetDesc process;
                try {
                    String processString = new String(module.getBytes(fileName), "UTF-8");
                    process = bpmn2Service.findProcessId(processString, kieContainer.getClassLoader());
                    process.setEncodedProcessSource(Base64.encodeBase64String(processString.getBytes()));
                    process.setDeploymentId(unit.getIdentifier());
                    process.setForms(formsData);
                    deployedUnit.addAssetLocation(process.getId(), process);
                } catch (UnsupportedEncodingException e) {
                	throw new IllegalArgumentException("Unsupported encoding while processing process " + fileName);
                }
            } else if (fileName.matches(".+ftl$")) {
                try {
                    String formContent = new String(module.getBytes(fileName), "UTF-8");
                    Pattern regex = Pattern.compile("(.{0}|.*/)([^/]*?)\\.ftl");
                    Matcher m = regex.matcher(fileName);
                    String key = fileName;
                    while (m.find()) {
                        key = m.group(2);
                    }
                    formsData.put(key, formContent);
                } catch (UnsupportedEncodingException e) {
                	throw new IllegalArgumentException("Unsupported encoding while processing form " + fileName);
                }
            } else if (fileName.matches(".+form$")) {
                try {
                    String formContent = new String(module.getBytes(fileName), "UTF-8");
                    Pattern regex = Pattern.compile("(.{0}|.*/)([^/]*?)\\.form");
                    Matcher m = regex.matcher(fileName);
                    String key = fileName;
                    while (m.find()) {
                        key = m.group(2);
                    }
                    formsData.put(key+".form", formContent);
                } catch (UnsupportedEncodingException e) {
                	throw new IllegalArgumentException("Unsupported encoding while processing form " + fileName);
                }
            } else if( fileName.matches(".+class$")) { 
                String className = fileName.replaceAll("/", ".");
                className = className.substring(0, fileName.length() - ".class".length());
                try {
                    deployedUnit.addClass(kieContainer.getClassLoader().loadClass(className));
                    logger.debug( "Loaded {} into the classpath from deployment {}", className, releaseId.toExternalForm());
                } catch (ClassNotFoundException cnfe) {
                    throw new IllegalArgumentException("Class " + className + " not found in the project");
                }
            }
        }
    }
}
