package org.jbpm.kie.services.impl.bpmn2;

import org.drools.core.xml.ExtensibleXmlParser;
import org.jbpm.bpmn2.xml.GlobalHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;


/**
 * This handler adds classes used in global declarations 
 * to the list of referenced classes.
 */
public class ProcessGetGlobalHandler extends GlobalHandler {

    private BPMN2DataServiceExtensionSemanticModule module;
    private ProcessDescriptionRepository repository;
    
    public ProcessGetGlobalHandler(BPMN2DataServiceExtensionSemanticModule module) {
        this.module = module;
        this.repository = module.getRepo();
    }
    
    public Object start(final String uri,
                        final String localName,
                        final Attributes attrs,
                        final ExtensibleXmlParser parser) throws SAXException {
        // does checks
        super.start(uri, localName, attrs, parser);
        
        final String type = attrs.getValue( "type" );
        
        String mainProcessId = module.getRepoHelper().getProcess().getId();
        ProcessDescRepoHelper repoHelper = repository.getProcessDesc(mainProcessId);
        if( type.contains(".") ) { 
            repoHelper.getReferencedClasses().add(type);
        } else { 
            repoHelper.getUnqualifiedClasses().add(type);
        }
        
        return null;
    }    
    
}
