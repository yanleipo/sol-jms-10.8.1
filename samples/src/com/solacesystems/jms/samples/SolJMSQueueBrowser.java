/**
 * SolJMSQueueBrowser.java
 * 
 * This sample demonstrates how to browse a queue's contents.
 * 
 * Usage: run SolJMSQueueBrowser -username USERNAME [-password PASSWORD] [-vpn VPN]
 *                               -url JNDI_PROVIDER_URL [-cf CONNECTION_FACTORY_JNDI_NAME] 
 *                               [-queue QUEUE_JNDI_NAME] [-physicalQueue QUEUE_NAME]
 * Where:
 * PASSWORD	is defaulted to empty string
 * VPN defaults to the default vpn
 * JNDI_PROVIDER_URL is the URL to access the JNDI store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default
 * Only one of [-queue, -physicalQueue] can be specified
 * 
 * Copyright 2004-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.NoSuchElementException;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

public class SolJMSQueueBrowser {
    private static final String SOLJMS_INITIAL_CONTEXT_FACTORY = 
        "com.solacesystems.jndi.SolJNDIInitialContextFactory"; 
    
    // The URL to the JNDI data store
    private String jndiProviderURL; 
    
    // The publisher/subscriber name, as configured with SolAdmin.
    private String username;
    
    // The publisher/subscriber password, as configured with an authentication server; default is empty.
    private String password = "";
    
    // The Message VPN on the appliance to connect to.
    private String vpn = null;

    // The default JNDI name of the connection factory.
    private String cfJNDIName = "cf/default";

    // The JNDI name of the queue.
    private String queueJNDIName;

    // The physical name of the queue.
    private String queueName;
                

    private  void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSQueueBrowser -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                            "-url JNDI_PROVIDER_URL \n" +
                            "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                            "[-queue QUEUE_JNDI_NAME] \n" + 
                            "[-physicalQueue QUEUE_NAME] \n" +
                            "Where:\n" + 
                            "- PASSWORD  is defaulted to empty string \n" +
                            "- VPN defaults to the default vpn \n" +
                            "- JNDI_PROVIDER_URL is the URL to access the JNDI store (for example, smf://10.10.10.10:55555) \n" + 
                            "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" + 
                            "- Only one of [-queue, -physicalQueue] can be specified");
    }
    
    private void run() {
        
        // The client needs to specify both of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, SOLJMS_INITIAL_CONTEXT_FACTORY);
        env.put(InitialContext.PROVIDER_URL, jndiProviderURL);
        env.put(Context.SECURITY_PRINCIPAL, username);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(SupportedProperty.SOLACE_JMS_SSL_VALIDATE_CERTIFICATE, false);  // enables the use of smfs://  without specifying a trust store
        if (vpn != null) {
            env.put(SupportedProperty.SOLACE_JMS_VPN, vpn);
        }

        // InitialContext is used to lookup the JMS administered objects.
        InitialContext initialContext = null;
        
        // JMS Connection.
        Connection connection = null;
        
        try {
            // Create InitialContext.
            initialContext = new InitialContext(env);
            
            // Lookup ConnectionFactory.
            SolConnectionFactory cf = (SolConnectionFactory)initialContext.lookup(cfJNDIName);
            
            // AD transport window size
            cf.setReceiveADWindowSize(255);
            
            // Create connection.
            connection = cf.createConnection();
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue queue = null;
            // Lookup Queue.
            if (queueJNDIName != null) {
                queue = (Queue)initialContext.lookup(queueJNDIName);
            }
            
            // Create a Queue.
            if (queueName != null) {
                queue = session.createQueue(queueName);
            }
            
            // Create the QueueBrowser.
            QueueBrowser browser = session.createBrowser(queue);
            
            // Iterate over the messages on the queue.
            Enumeration<?> enumeration = browser.getEnumeration();
            Message rcvd = null;
            // enumeration.hasMoreElements() returns true only if there are 
            // messages in the local queue. If there are no local messages and
            // messages are in flight from the appliance, it will return false.
            // So it does not necessarily mean that the appliance queue is empty.
            // If you want to browse every message on a queue, it is better to use
            // enumeration.nextElement(). It keeps returning messages until 
            // the local queue is empty and it has not received a message for 10 seconds.
            while(true) {
            	try {
	            	rcvd = (Message)enumeration.nextElement();
	            	
	                if (rcvd instanceof TextMessage) {
	                    System.out.println("RCVD: " + ((TextMessage)rcvd).getText());
	                } else {
	                    System.out.println("RCVD: " + SolJmsUtility.dumpMessage(rcvd));
	                }
            	} catch (NoSuchElementException e) {
            		break;
            	} 
            }
        } catch (NamingException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {}
            }
            if (initialContext != null) {
                try {
                    initialContext.close();
                } catch (Exception e) {}
            }
        }
    }
    
    public static void main(String[] args) {
        try {
            SolJMSQueueBrowser instance = new SolJMSQueueBrowser();

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-url")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.jndiProviderURL = args[i];
                } else if (args[i].equals("-username")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.username = args[i];
                } else if (args[i].equals("-password")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.password = args[i];
                } else if (args[i].equals("-vpn")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.vpn = args[i];              
                } else if (args[i].equals("-cf")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.cfJNDIName = args[i];
                } else if (args[i].equals("-queue")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.queueJNDIName = args[i];
                } else if (args[i].equals("-physicalQueue")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.queueName = args[i];      
                } else {
                    instance.printUsage();
                    System.out.println("Illegal argument specified - " + args[i]);
                    return;
                }
            }
            
            if (instance.jndiProviderURL == null) {
                instance.printUsage();
                System.out.println("Please specify \"-url\" parameter");
                return;
            }
            if (instance.username == null) {
                instance.printUsage();
                System.out.println("Please specify \"-username\" parameter");
                return;
            }
            if ((instance.queueJNDIName == null) && (instance.queueName == null)) {
                instance.printUsage();
                System.out.println("Please specify one of [-queue, -physicalQueue]");
                return;
            }

            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }    
}
