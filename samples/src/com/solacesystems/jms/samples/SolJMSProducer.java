/**
 * SolJMSProducer.java
 * 
 * This is a simple sample of a basic JMS Producer.
 *
 * This sample publishes 10 JMS Text Messages to a specified Topic or Queue
 *
 * Notice that the specified username, ConnectionFactory, Topic and Queue 
 * should exist in your configuration (configured with SolAdmin)
 *
 * Usage: run SolJMSProducer -username USERNAME [-password PASSWORD] -url JNDI_PROVIDER_URL 
 *                            [-cf CONNECTION_FACTORY_JNDI_NAME] [-topic TOPIC_JNDI_NAME] 
 *                            [-queue QUEUE_JNDI_NAME]
 * Where:
 * PASSWORD	is defaulted to empty string
 * JNDI_PROVIDER_URL is the URL to access the JNDI store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default 
 *
 * Copyright 2004-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SupportedProperty;

/**
 * Creates a JMS Producer that sends 10 JMS text messages.
 */
public class SolJMSProducer {
    
    private static final String SOLJMS_INITIAL_CONTEXT_FACTORY = 
        "com.solacesystems.jndi.SolJNDIInitialContextFactory"; 

    // URL to the JNDI data store.
    private String jndiProviderURL; 
    
    // The publisher/subscriber name, as configured with SolAdmin.
    private String username;
    
    // The publisher/subscriber password, as configured with an authentication server; default is empty.
    private String password = "";
    
    // The Message VPN on the appliance to connect to.
    private String vpn = null;

    // The default JNDI name of the connection factory.
    private String cfJNDIName = "cf/default";
    
    // The JNDI name of the topic.
    private String topicJNDIName;
    
    // The physical name of the topic.
    private String topicName;

    // The JNDI name of the queue.
    private String queueJNDIName;

    // The physical name of the queue.
    private String queueName;

    // Use XML.
    private boolean xml = false;
    
    // Use Deliver To One.
    private boolean dto = false;
    
    // Use Compression on JNDI Lookups.
    private boolean compression = false;
    
    // Optimize for Direct.
    private boolean optDirect = false;
    
    // The number of messages to send.
    private int numMsgsToSend = 10;
    
    // Kerberos authentication
    private boolean kerberos = false;

    private  void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSProducer -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                            "-url JNDI_PROVIDER_URL \n" +
                            "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                            "[-topic TOPIC_JNDI_NAME] \n" +
                            "[-physicalTopic TOPIC_NAME] \n" +
                            "[-queue QUEUE_JNDI_NAME] \n" + 
                            "[-physicalQueue QUEUE_NAME] \n" +
                            "[-xml] \n" +
                            "[-dto] \n" + 
                            "[-compression] \n" +
                            "[-optDirect]\n" +
                            "[-x AUTHENTICATION_SCHEME]\nWhere:\n" +
                            "- PASSWORD  is defaulted to empty string \n" +
                            "- VPN defaults to the default vpn \n" +
                            "- JNDI_PROVIDER_URL is the URL to access the JNDI store (e.g. smf://10.10.10.10:55555) \n" + 
                            "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" + 
                            "- Only one of [-topic, -physicalTopic, -queue, -physicalQueue] can be specified\n" +
                            "- -xml sends XML payload used with content routing\n" +
                            "- -dto sets the deliver to one flag (direct messaging only)\n" + 
                            "- -compression uses compression for JNDI lookups (use the appliance's compressed port in -url)\n" +
                            "- -optDirect optimizes for single producer of direct messages\n" +
                            "- AUTHENTICATION_SCHEME is one of basic (default), kerberos");
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
        if (compression) {
            env.put(SupportedProperty.SOLACE_JMS_COMPRESSION_LEVEL, 1);     // non-zero compression level
        }
        if (optDirect) {
            env.put(SupportedProperty.SOLACE_JMS_OPTIMIZE_DIRECT, true);
        }
        if (kerberos) {
            env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_GSS_KRB);
        }

        // InitialContext is used to lookup the JMS administered objects.
    	InitialContext initialContext = null;
    	
    	// JMS Connection
    	Connection connection = null;
    	
        try {
            // Create InitialContext.
            initialContext = new InitialContext(env);
        	
            // Lookup ConnectionFactory.
        	ConnectionFactory cf = (ConnectionFactory)initialContext.lookup(cfJNDIName);
        	
        	// Create connection.
            connection = cf.createConnection();
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Destination destination = null;
            // Lookup Topic.
            if (topicJNDIName != null) {
                destination = (Destination)initialContext.lookup(topicJNDIName);
            }
            
            // Create a Topic.
            if (topicName != null) {
                destination = session.createTopic(topicName);
            }

            // Lookup Queue.
            if (queueJNDIName != null) {
                destination = (Destination)initialContext.lookup(queueJNDIName);
            }
            
            // Create a Queue
            if (queueName != null) {
                destination = session.createQueue(queueName);
            }

            // From the session, create a producer for the destination.
            // Use the default delivery mode as set in the connection factory
            MessageProducer producer = session.createProducer(destination);

            // Create a text message.
            TextMessage testMessage;
            if (xml) {
                testMessage = session.createTextMessage("<title>Hello from SolJMSProducer</title>");
            } else {
                testMessage = session.createTextMessage("Hello from SolJMSProducer");
            }
            // Message properties override the setting in the connection factory.
            testMessage.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_ISXML, xml);
            
            if (dto) {
                testMessage.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_DELIVER_TO_ONE, true);
            }
            
            System.out.println("About to send " + numMsgsToSend + " JMS Text Message(s)");
            // Send text message.
            for (int i = 0; i < numMsgsToSend; i++) {
                producer.send(testMessage);
                System.out.println("SENT: " + testMessage.getText());
                try {
                    Thread.sleep(1000);
                } catch (Exception ex) {}
            }
            System.out.println("DONE");            
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
            SolJMSProducer instance = new SolJMSProducer();

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
                } else if (args[i].equals("-topic")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.topicJNDIName = args[i];
                } else if (args[i].equals("-physicalTopic")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.topicName = args[i];      
                } else if (args[i].equals("-queue")) {
                	i++;
                	if (i >= args.length) instance.printUsage();
                	instance.queueJNDIName = args[i];
                } else if (args[i].equals("-physicalQueue")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.queueName = args[i];      
                } else if (args[i].equals("-xml")) {
                    instance.xml = true;      
                } else if (args[i].equals("-dto")) {
                    instance.dto = true;      
                } else if (args[i].equals("-compression")) {
                    instance.compression = true;      
                } else if (args[i].equals("-optDirect")) {
                    instance.optDirect = true;      
                } else if (args[i].equals("-x")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    if (args[i].equals("basic")) {
                        // default
                    } else if (args[i].equals("kerberos")) {
                        instance.kerberos = true;     
                    } else {
                        instance.printUsage();
                        System.out.println("Illegal authentication type specified - \"" + args[i] + "\", expected one of basic, kerberos");
                    }
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
            if ((instance.topicJNDIName == null) && (instance.queueJNDIName == null) &&
                (instance.topicName == null) && (instance.queueName == null)) {
                instance.printUsage();
                System.out.println("Please specify one of [-topic, -physicalTopic, -queue, -physicalQueue]");
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