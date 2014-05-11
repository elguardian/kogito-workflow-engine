package org.jbpm.runtime.manager.impl.deploy;

import static org.junit.Assert.assertNotNull;

import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.junit.Test;
import org.kie.internal.runtime.conf.DeploymentDescriptor;
import org.kie.internal.runtime.conf.ObjectModel;

public class JaxbMarshalingTest {

	private Class<?>[] jaxbClasses = { DeploymentDescriptorImpl.class};
	
	@Test
	public void testJaxbDeploymentDescriptorSerialization() throws Exception {
		DeploymentDescriptor descriptor = new DeploymentDescriptorImpl();
		descriptor.getBuilder()
		.addTaskEventListener(new ObjectModel("org.jbpm.task.Listener", new Object[]{"test", "another"}));
		
		String output = convertJaxbObjectToString(descriptor);
		System.out.println(output);
		assertNotNull(output);
	}
	
    public String convertJaxbObjectToString(Object object) throws JAXBException {
        Marshaller marshaller = JAXBContext.newInstance(jaxbClasses).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter stringWriter = new StringWriter();

        marshaller.marshal(object, stringWriter);
        String output = stringWriter.toString();

        return output;
    }
}
