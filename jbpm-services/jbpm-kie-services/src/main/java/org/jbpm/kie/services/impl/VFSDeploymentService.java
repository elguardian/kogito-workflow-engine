package org.jbpm.kie.services.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.jbpm.kie.services.api.DeploymentUnit;
import org.jbpm.kie.services.api.IdentityProvider;
import org.jbpm.kie.services.api.Vfs;
import org.jbpm.kie.services.api.bpmn2.BPMN2DataService;
import org.jbpm.kie.services.impl.audit.ServicesAwareAuditEventBuilder;
import org.jbpm.kie.services.impl.model.ProcessDesc;
import org.jbpm.process.audit.AbstractAuditLogger;
import org.jbpm.process.audit.AuditLoggerFactory;
import org.jbpm.runtime.manager.impl.RuntimeEnvironmentBuilder;
import org.jbpm.runtime.manager.impl.cdi.InjectableRegisterableItemsFactory;
import org.jbpm.shared.services.api.FileException;
import org.jbpm.shared.services.api.FileService;
import org.jbpm.shared.services.api.JbpmServicesPersistenceManager;
import org.kie.api.io.ResourceType;
import org.kie.commons.java.nio.file.Path;
import org.kie.internal.io.ResourceFactory;

@ApplicationScoped
@Vfs
public class VFSDeploymentService extends AbstractDeploymentService {

    @Inject
    private BeanManager beanManager;
    @Inject
    private JbpmServicesPersistenceManager pm;
    @Inject
    private FileService fs;
    @Inject
    private EntityManagerFactory emf;
    @Inject
    private IdentityProvider identityProvider; 
    @Inject
    private BPMN2DataService bpmn2Service;


    @Override
    public void deploy(DeploymentUnit unit) {
        super.deploy(unit);
        if (!(unit instanceof VFSDeploymentUnit)) {
            throw new IllegalArgumentException("Invalid deployment unit provided - " + unit.getClass().getName());
        }
        
        DeployedUnitImpl deployedUnit = new DeployedUnitImpl(unit);
        VFSDeploymentUnit vfsUnit = (VFSDeploymentUnit) unit;
        // Create Runtime Manager Based on the Reference
        RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.getDefault()
                .entityManagerFactory(emf);
        
        AbstractAuditLogger auditLogger = AuditLoggerFactory.newJPAInstance(emf);
        ServicesAwareAuditEventBuilder auditEventBuilder = new ServicesAwareAuditEventBuilder();
        auditEventBuilder.setIdentityProvider(identityProvider);
        auditEventBuilder.setDeploymentUnitId(vfsUnit.getIdentifier());
        auditLogger.setBuilder(auditEventBuilder);
        if (beanManager != null) {
            builder.registerableItemsFactory(InjectableRegisterableItemsFactory.getFactory(beanManager, auditLogger));
        }
        loadProcesses(vfsUnit, builder, deployedUnit);
        loadRules(vfsUnit, builder, deployedUnit); 
        
        commonDeploy(vfsUnit, deployedUnit, builder.get());
        
    }

   
    
    protected void loadProcesses(VFSDeploymentUnit vfsUnit, RuntimeEnvironmentBuilder builder, DeployedUnitImpl deployedUnit) {
        Iterable<Path> loadProcessFiles = null;

        try {
            Path processFolder = fs.getPath(vfsUnit.getRepository() + vfsUnit.getRepositoryFolder());
            loadProcessFiles = fs.loadFilesByType(processFolder, ".+bpmn[2]?$");
        } catch (FileException ex) {
            Logger.getLogger(VFSDeploymentService.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (Path p : loadProcessFiles) {
            String processString = "";
            try {
                processString = new String(fs.loadFile(p));
                builder.addAsset(ResourceFactory.newByteArrayResource(processString.getBytes()), ResourceType.BPMN2);
                ProcessDesc process = bpmn2Service.findProcessId(processString, null);
                process.setOriginalPath(p.toUri().toString());
                process.setDeploymentId(vfsUnit.getIdentifier());
                deployedUnit.addAssetLocation(process.getId(), process);
                
            } catch (Exception ex) {
                Logger.getLogger(VFSDeploymentService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    protected void loadRules(VFSDeploymentUnit vfsUnit, RuntimeEnvironmentBuilder builder, DeployedUnitImpl deployedUnit) {
        Iterable<Path> loadRuleFiles = null;

        try {
            Path rulesFolder = fs.getPath(vfsUnit.getRepository() + vfsUnit.getRepositoryFolder());
            loadRuleFiles = fs.loadFilesByType(rulesFolder, ".+drl");
        } catch (FileException ex) {
            Logger.getLogger(VFSDeploymentService.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (Path p : loadRuleFiles) {
            String ruleString = "";
            try {
                ruleString = new String(fs.loadFile(p));
                builder.addAsset(ResourceFactory.newByteArrayResource(ruleString.getBytes()), ResourceType.DRL);                
                
            } catch (Exception ex) {
                Logger.getLogger(VFSDeploymentService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    


    public JbpmServicesPersistenceManager getPm() {
        return pm;
    }

    public void setPm(JbpmServicesPersistenceManager pm) {
        this.pm = pm;
    }

    public FileService getFs() {
        return fs;
    }

    public void setFs(FileService fs) {
        this.fs = fs;
    }

    public EntityManagerFactory getEmf() {
        return emf;
    }

    public void setEmf(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public IdentityProvider getIdentityProvider() {
        return identityProvider;
    }

    public void setIdentityProvider(IdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }

    public BPMN2DataService getBpmn2Service() {
        return bpmn2Service;
    }

    public void setBpmn2Service(BPMN2DataService bpmn2Service) {
        this.bpmn2Service = bpmn2Service;
    }


}
