/**
 * SolJMSActiveFlowIndication.java
 * 
 * This is a simple sample of a basic JMS Consumer used to demonstrate
 * Active Flow Indication events.
 *
 * This application with create two Message Consumers for the same exclusive
 * queue.  The Active Flow Indication events received by the application will be
 * output to the console.
 *
 * Notice that the specified username, ConnectionFactory, Topic, Queue, and durable subscription name 
 * (which is mapped to the name of a durable Topic Endpoint)
 * should exist in your configuration (configured with SolAdmin).
 *
 * Usage: run SolJMSActiveFlowIndication -username USERNAME [-password PASSWORD] -url JNDI_PROVIDER_URL 
 *           [-cf CONNECTION_FACTORY_JNDI_NAME] [-queue QUEUE_JNDI_NAME]
 *
 * Where:
 * PASSWORD is defaulted to empty string
 * JNDI_PROVIDER_URL is the url to access the jndi store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default 
 * 
 * Copyright 2004-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.io.IOException;
import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolConsumerEventListener;
import com.solacesystems.jms.SolConsumerEventSource;
import com.solacesystems.jms.SupportedProperty;
import com.solacesystems.jms.events.SolConsumerEvent;

public class SolJMSActiveFlowIndication {
    
    private static class VMExitHandler extends Thread {
        private Connection connection;
        private InitialContext initialContext;
        
        public VMExitHandler(Connection connection, InitialContext initialContext) {
            this.connection = connection;
            this.initialContext = initialContext;
        }
        
        public void run() {
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

    private static final String SOLJMS_INITIAL_CONTEXT_FACTORY = 
        "com.solacesystems.jndi.SolJNDIInitialContextFactory"; 
    
    // The URL to the JNDI data store. 
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
    
    // Whether to use compression on JNDI Lookups. 
    private boolean compression = false;
    
    // Optimize for Direct.
    private boolean optDirect = false;

    // Kerberos authentication
    private boolean kerberos = false;
    
    private void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSActiveFlowIndication -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                           "-url JNDI_PROVIDER_URL \n" +
                           "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                           "[-queue QUEUE_JNDI_NAME] \n" +
                           "[-physicalQueue QUEUE_NAME] \n" + 
                           "[-compression] \n" +
                           "[-optDirect]\n" +
                           "[-x AUTHENTICATION_SCHEME]\nWhere:\n" +
                           "- PASSWORD  is defaulted to empty string \n" +
                           "- VPN defaults to the default vpn \n" +
                           "- JNDI_PROVIDER_URL is the url to access the jndi store (e.g. smf://10.10.10.10:55555) \n" + 
                           "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" + 
                           "- Only one of [-queue, -physicalQueue, -tempQueue] can be specified\n" +
                           "- -compression uses compression for JNDI lookups (use the appliance's compressed port in -url)\n" +
                           "- -optDirect optimizes for single consumer of direct messages\n" +
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
        Connection connection2 = null;
        
        try {
            // Create InitialContext.
            initialContext = new InitialContext(env);
            
            // Lookup ConnectionFactory.
            ConnectionFactory cf = (ConnectionFactory)initialContext.lookup(cfJNDIName);
            
            // Create a JMS Connection instance using the specified username/password.
            connection = cf.createConnection();
            connection2 = cf.createConnection();
                          
            Runtime.getRuntime().addShutdownHook(new VMExitHandler(connection, initialContext));
            Runtime.getRuntime().addShutdownHook(new VMExitHandler(connection2, null));
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Lookup Queue, if Specified.
            Queue queue = null;
            if (queueJNDIName != null) {
                queue = (Queue)initialContext.lookup(queueJNDIName);
            }

            // Create a Queue.
            if (queueName != null) {
                queue = session.createQueue(queueName);
            }

            Queue queue2 = session.createQueue(queue.getQueueName());

            // From the session, create the first consumer for the destination.
            MessageConsumer consumer1 = null;
            consumer1 = session.createConsumer(queue);
            
            MessageCounter messageCounter = new MessageCounter();
            consumer1.setMessageListener(messageCounter);
            SolConsumerEventSource solConsumerEventSource1 = (SolConsumerEventSource)consumer1;
			solConsumerEventSource1.setSolConsumerEventListener(new SolConsumerEventListener() {
				public void onEvent(SolConsumerEvent event) {
					System.out.println("From first consumer : " + event.toString());
				}
            	
            });
			
            // From the session, create the second consumer for the destination.
            MessageConsumer consumer2 = null;
            consumer2 = session2.createConsumer(queue2);
            SolConsumerEventSource solConsumerEventSource2 = (SolConsumerEventSource)consumer2;
			solConsumerEventSource2.setSolConsumerEventListener(new SolConsumerEventListener() {
				public void onEvent(SolConsumerEvent event) {
					System.out.println("From second consumer : " + event.toString());
				}
            	
            });            
            
            // Do not forget to start the JMS Connection.
            connection.start();
            
            // Output a message on the console.
            System.out.println("Press enter to terminate the first consumer.");
            byte[] b = new byte[80];
            try
            {
                System.in.read(b);
            } catch(IOException ex)
            {
                
            }
            
            System.out.println("Number of message received: " +
            messageCounter.getMessageCount());
            
            consumer1.close();
            session.close();
            connection.close();
            
            System.out.println("An active event should be received from the second consumer.");
            System.out.println("Press enter to exit.");
            try
            {
                System.in.read(b);
            } catch(IOException ex)
            {
                
            }
            consumer2.close();
            connection2.close();
            session.close();
            session2.close();
            
            
        } catch (NamingException e) {
            // Most likely we are not able to lookup an administered object (Topic, Queue or ConnectionFactory).
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        try {
            SolJMSActiveFlowIndication instance = 
                    new SolJMSActiveFlowIndication();

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
                }else if (args[i].equals("-cf")) {
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
            if ((instance.queueName == null) &&
                (instance.queueJNDIName == null)) {
                instance.printUsage();
                System.out.println("Please specify one of [-queue, -physicalQueue, -tempQueue]");
                return;
            }
            
            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
    
    class MessageCounter implements MessageListener
    {
        int mMessageNumber = 0;
        MessageCounter(){
            
        }
        
        public int getMessageCount()
        {
            return mMessageNumber;
        }
        
        public void onMessage(Message message) {
            mMessageNumber++;
            System.out.println("Got a message");
        }
    }
}
