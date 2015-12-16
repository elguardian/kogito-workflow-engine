/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.jbpm.process.workitem.email;

import java.util.Random;

import org.drools.core.process.instance.impl.WorkItemImpl;
import org.jbpm.test.util.AbstractBaseTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test tests whether or not it's possible to use the SendHtml functionality to send mail that will be <i>relayed</i>. 
 * </p>
 * The situation that this test is the following:<ol>
 * <li>You have a jBPM process that runs on a server on domain "willywonka.com".</li>
 * <li>However, you want to send e-mail, from a node within your jBPM process, to e-mail addresses at "oompaloompa.org". </li>
 * </ol>
 * </p>
 * Unfortunately, there's not really a way to test this with the Wiser/SubethaSMTP framework, which means you have to use real 
 * mail/smtp servers -- which is what this test does. 
 * </p>
 * Fill in the fromAddress, fromPassword and toAddress (and if neccessary, modify the user and smtpServerFrom fields) and run this
 * test. If no exceptions are thrown -- and you receive an e-mail, then you're good!
 */
public class SendHtmlRealTest extends AbstractBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(SendHtmlRealTest.class);
    private Random random = new Random();
    int uniqueTestNum = -1;

    // the following fields should all be filled with information that relates to 1 domain: willywonka.com, for example.
    String fromAddress = "XXXX @gmail.com";
    String user = fromAddress.substring(0, fromAddress.indexOf("@"));
    String fromPassword = "XXXX's password";
    String smtpServerFrom = "smtp." + fromAddress.substring(fromAddress.indexOf("@")+1);
   
    // the toAddress should be on a different domain -- oompaloompa.org, for example.
    String toAddress = "XX XX @redhat.com";
        
    @Before
    public void setUp() throws Exception {
        uniqueTestNum = random.nextInt(Integer.MAX_VALUE);
    }

    @Test 
    @Ignore
    public void sendEmailViaAnotherDomain() throws Exception { 
        String testMethodName = Thread.currentThread().getStackTrace()[1].getMethodName(); 
        logger.info("{} : {}", testMethodName, uniqueTestNum);
        
        WorkItemImpl workItem = new WorkItemImpl();
        workItem.setParameter( "To", toAddress );
        workItem.setParameter( "From", fromAddress );
        workItem.setParameter( "Reply-To", fromAddress );
        
        workItem.setParameter( "Subject", this.getClass().getSimpleName() + " test message [" + uniqueTestNum + "]" );
        workItem.setParameter( "Body", "\nThis is the test message generated by the " 
                + testMethodName + " test (" + uniqueTestNum + ").\n" );
        
        Connection connection = new Connection(smtpServerFrom, "25", user, fromPassword);
        // Gmail requires use of the STARTTLS protocol
        connection.setStartTls(true);
        
        Email email = EmailWorkItemHandler.createEmail(workItem, connection);
        SendHtml.sendHtml(email);
    }
}
