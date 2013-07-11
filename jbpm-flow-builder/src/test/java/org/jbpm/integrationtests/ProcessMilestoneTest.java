package org.jbpm.integrationtests;

import static org.junit.Assert.*;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.drools.compiler.compiler.DroolsError;
import org.drools.compiler.compiler.PackageBuilder;
import org.drools.core.RuleBase;
import org.drools.core.RuleBaseFactory;
import org.drools.core.WorkingMemory;
import org.drools.core.rule.Package;
import org.jbpm.integrationtests.test.Person;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.test.util.AbstractBaseTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessMilestoneTest extends AbstractBaseTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessMilestoneTest.class);
    
    @Test
    public void testMilestone() {
        PackageBuilder builder = new PackageBuilder();
        Reader source = new StringReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<process xmlns=\"http://drools.org/drools-5.0/process\"\n" +
            "         xmlns:xs=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xs:schemaLocation=\"http://drools.org/drools-5.0/process drools-processes-5.0.xsd\"\n" +
            "         type=\"RuleFlow\" name=\"flow\" id=\"org.drools.milestone\" package-name=\"org.jbpm\" version=\"1\" >\n" +
            "\n" +
            "  <header>\n" +
            "    <imports>\n" +
            "      <import name=\"org.jbpm.integrationtests.test.Person\" />\n" +
            "    </imports>\n" +
            "  </header>\n" +
            "\n" +
            "  <nodes>\n" +
            "    <start id=\"1\" name=\"Start\" />\n" +
            "    <milestone id=\"2\" name=\"Milestone\" >\n" +
            "      <constraint type=\"rule\" dialect=\"mvel\" >Person( name == \"John Doe\" )</constraint>" +
            "    </milestone>\n" +
            "    <end id=\"3\" name=\"End\" />\n" +
            "  </nodes>\n" +
            "\n" +
            "  <connections>\n" +
            "    <connection from=\"1\" to=\"2\" />\n" +
            "    <connection from=\"2\" to=\"3\" />\n" +
            "  </connections>\n" +
            "\n" +
            "</process>");
        builder.addRuleFlow(source);
        Package pkg = builder.getPackage();
        RuleBase ruleBase = RuleBaseFactory.newRuleBase();
        ruleBase.addPackage( pkg );
        WorkingMemory workingMemory = ruleBase.newStatefulSession();
        
        ProcessInstance processInstance = ( ProcessInstance )
            workingMemory.startProcess("org.drools.milestone");
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstance.getState());
        workingMemory.insert(new Person("Jane Doe", 20));
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstance.getState());
        workingMemory.insert(new Person("John Doe", 50));
        workingMemory.fireAllRules();
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
    }
    
    @Test
    public void testMilestoneWithProcessInstanceConstraint() {
        PackageBuilder builder = new PackageBuilder();
        Reader source = new StringReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<process xmlns=\"http://drools.org/drools-5.0/process\"\n" +
            "         xmlns:xs=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xs:schemaLocation=\"http://drools.org/drools-5.0/process drools-processes-5.0.xsd\"\n" +
            "         type=\"RuleFlow\" name=\"flow\" id=\"org.drools.milestone\" package-name=\"org.jbpm\" version=\"1\" >\n" +
            "\n" +
            "  <header>\n" +
            "    <imports>\n" +
            "      <import name=\"org.jbpm.integrationtests.test.Person\" />\n" +
            "      <import name=\"org.jbpm.integrationtests.ProcessMilestoneTest.ProcessUtils\" />\n" +
            "    </imports>\n" +
            "    <variables>\n" +
            "      <variable name=\"name\" >\n" +
            "        <type name=\"org.drools.core.process.core.datatype.impl.type.StringDataType\" />\n" +
            "      </variable>\n" +
            "    </variables>\n" +
            "  </header>\n" +
            "\n" +
            "  <nodes>\n" +
            "    <start id=\"1\" name=\"Start\" />\n" +
            "    <milestone id=\"2\" name=\"Milestone\" >\n" +
            "      <constraint type=\"rule\" dialect=\"mvel\" >processInstance: org.jbpm.ruleflow.instance.RuleFlowProcessInstance()\n" +
            "Person( name == (ProcessUtils.getValue(processInstance, \"name\")) )</constraint>" +
            "    </milestone>\n" +
            "    <end id=\"3\" name=\"End\" />\n" +
            "  </nodes>\n" +
            "\n" +
            "  <connections>\n" +
            "    <connection from=\"1\" to=\"2\" />\n" +
            "    <connection from=\"2\" to=\"3\" />\n" +
            "  </connections>\n" +
            "\n" +
            "</process>");
        builder.addRuleFlow(source);
        Package pkg = builder.getPackage();
        for (DroolsError error: builder.getErrors().getErrors()) {
        	logger.error(error.toString());
        }
        RuleBase ruleBase = RuleBaseFactory.newRuleBase();
        ruleBase.addPackage( pkg );
        WorkingMemory workingMemory = ruleBase.newStatefulSession();
        
        Person john = new Person("John Doe", 20);
        Person jane = new Person("Jane Doe", 20);
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", john.getName());
        ProcessInstance processInstanceJohn = ( ProcessInstance )
            workingMemory.startProcess("org.drools.milestone", params);
        workingMemory.insert(processInstanceJohn);
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstanceJohn.getState());

        params = new HashMap<String, Object>();
        params.put("name", jane.getName());
        ProcessInstance processInstanceJane = ( ProcessInstance )
            workingMemory.startProcess("org.drools.milestone", params);
        workingMemory.insert(processInstanceJane);
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstanceJane.getState());
        
        workingMemory.insert(jane);
        workingMemory.fireAllRules();
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstanceJohn.getState());
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstanceJane.getState());
        
        workingMemory.insert(john);
        workingMemory.fireAllRules();
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstanceJohn.getState());
    }
    
    public static class ProcessUtils {
    	
    	public static Object getValue(RuleFlowProcessInstance processInstance, String name) {
    		VariableScopeInstance scope = (VariableScopeInstance)
    			processInstance.getContextInstance(VariableScope.VARIABLE_SCOPE);
    		return scope.getVariable(name);
    	}
    	
    }

}
