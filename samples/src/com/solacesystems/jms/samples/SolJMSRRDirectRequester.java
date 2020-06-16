/**
 * SolJMSRRDirectRequester.java
 * 
 * This sample shows how to implement a Requester for direct Request-Reply messaging, where 
 *
 *    SolJMSRRDirectRequester: A message Endpoint that sends a request message and waits to receive a
 *                             reply message as a response.
 *    SolJMSRRDirectReplier:   A message Endpoint that waits to receive a request message and responses
 *                             to it by sending a reply message.
 *  
 *  |-------------------------|  ---RequestTopic --> |------------------------|
 *  | SolJMSRRDirectRequester |                      | SolJMSRRDirectReplier  |
 *  |-------------------------|  <--ReplyToTopic---- |------------------------|
 * 
 * Usage: run SolJMSRRDirectRequester -username USERNAME [-password PASSWORD] [-vpn VPN]
 *                                    -url JNDI_PROVIDER_URL [-cf CONNECTION_FACTORY_JNDI_NAME] 
 *                                    -rt REQUEST_TOPIC_JNDI_NAME [-compression]
 * Where:
 * PASSWORD	is defaulted to empty string
 * VPN defaults to the default vpn
 * JNDI_PROVIDER_URL is the URL to access the JNDI store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default
 * REQUEST_TOPIC_JNDI_NAME is the JNDI name of the topic to use for the request
 * -compression uses compression for JNDI lookups (use the appliance's compressed port in -url)
 * 
 * Copyright 2013-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SupportedProperty;

public class SolJMSRRDirectRequester {

    Session session = null;
    MessageProducer producer = null;
    MessageConsumer consumer = null;
    InitialContext initialContext = null;
    
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
    
    // Use Compression on JNDI Lookups. 
    private boolean compression = false;

    //Time to wait for a reply before timing out
    private int timeoutMs = 2000;
    
    // Format for the arithmetic operation
    private final String ARITHMETIC_EXPRESSION = "\t=================================\n\t  %d %s %d = %s  \t\n\t=================================\n";
    
    enum Operation {
        PLUS,
        MINUS,
        TIMES,
        DIVIDE
    }

    private void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSRRDirectRequester -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                            "-url JNDI_PROVIDER_URL \n" +
                            "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                            "-rt REQUEST_TOPIC_JNDI_NAME \n" +
                            "[-compression] \nWhere:\n" +
                            "- PASSWORD  is defaulted to empty string \n" +
                            "- VPN defaults to the default vpn \n" +
                            "- JNDI_PROVIDER_URL is the URL to access the JNDI store (e.g. smf://10.10.10.10:55555) \n" + 
                            "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" + 
                            "- REQUEST_TOPIC_JNDI_NAME is the JNDI name of the topic to use for the request \n" +
                            "- -compression uses compression for JNDI lookups (use the appliance's compressed port in -url)\n");
    }
    
    byte getOperationOrdinal(Operation operation) throws Exception {
        switch(operation) {
        case PLUS:
            return 1;
        case MINUS:
            return 2;
        case TIMES:
            return 3;
        case DIVIDE:
            return 4;
        default:
            throw new Exception("Unknown operation value");
        }
    }
    
    public void doRequest(String topicJNDIName, Operation operation, int leftHandOperand, int rightHandOperand) throws Exception {
        try {
            //The response will be received on this temporary queue.
            TemporaryQueue replyToQueue = session.createTemporaryQueue();
            // From the session, create a consumer for receiving the request's
            // reply from RRDirectReplier
            consumer = session.createConsumer(replyToQueue);
            
            Destination requestDestination = null;
            // Lookup Topic.
            if (topicJNDIName != null) {
                requestDestination = (Destination)initialContext.lookup(topicJNDIName);
            }
    
            // Create a request message.
            StreamMessage requestMessage;
            requestMessage = session.createStreamMessage();
            requestMessage.writeByte(getOperationOrdinal(operation));
            requestMessage.writeInt(leftHandOperand);
            requestMessage.writeInt(rightHandOperand);
            
            //The application must put the destination of the reply in the replyTo field of the request
            requestMessage.setJMSReplyTo(replyToQueue);
            //The application must put a correlation ID in the request
            String correlationId = UUID.randomUUID().toString();
            requestMessage.setJMSCorrelationID(correlationId);
    
            //Send the request then wait for a reply
            
            //It is important to specify a delivery mode of NON-PERSISTENT here, because
            //only NON-PERSISTENT will be sent as DIRECT messages over the Solace bus
            //(NON-PERSISTENT are sent as DIRECT because the connection factory that was used
            // was sent to use Direct Messaging).
            //Leaving priority and Time to Live to their defaults.
            //NOTE: Priority is not supported by the Solace Message Bus
            producer.send(requestDestination, requestMessage, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            Message incomingMessage = consumer.receive(timeoutMs);
    
            if (incomingMessage != null) {
                if (incomingMessage.getJMSCorrelationID() == null) {
                    throw new Exception("Received a reply message with no correlationID.  This field is needed for a direct request.");
                }
                
                //The reply's correlationID must match the request's correlationID 
                if (!incomingMessage.getJMSCorrelationID().equals(correlationId)) {
                    throw new Exception("Received invalid correlationID in reply message.");
                }
                if (incomingMessage instanceof StreamMessage) {
                    System.out.println("Got reply message");
                    
                    StreamMessage replyMessage = (StreamMessage)incomingMessage;
                    if (!replyMessage.getBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE)) {
                        System.out.println("Warning: Received a reply message without the isReplyMsg flag set.");
                    }
                    if (replyMessage.readBoolean()) {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, Double.toString(replyMessage.readDouble())));
                    } else {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, "operation failed"));
                    }
                } else {
                    System.out.println("Request failed");
                }
            } else {
                System.out.println("Failed to receive a reply within " + timeoutMs + "msecs");
            }
        } finally {
            if (consumer != null) {
                consumer.close();
                consumer = null;
            }
        }
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
        
        // JMS Connection
        Connection connection = null;
        
        try {
            // Create InitialContext.
            initialContext = new InitialContext(env);
            
            // Lookup ConnectionFactory.
            SolConnectionFactory cf = (SolConnectionFactory)initialContext.lookup(cfJNDIName);
            if (!cf.getDirectTransport()) {
                //This sample has to use direct messaging, so we force the connection factory
                //to create direct messaging connections here.
                System.out.println("The specified connection factory is not using direct messaging.  Overriding it to use direct messaging.\nThis is required so that NON-PERSITENT messages sent by the application gets sent as Direct Message over the Solace Message Bus.");
                cf.setDirectTransport(true);
            }

            // Create connection.
            connection = cf.createConnection();
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            //The producer that will be used to send requests
            producer = session.createProducer(null);
            
            //Start receiving messages
            connection.start();

            doRequest(topicJNDIName, Operation.PLUS, 5, 4);
            Thread.sleep(1000);
            doRequest(topicJNDIName, Operation.MINUS, 5, 4);
            Thread.sleep(1000);
            doRequest(topicJNDIName, Operation.TIMES, 5, 4);
            Thread.sleep(1000);
            doRequest(topicJNDIName, Operation.DIVIDE, 5, 4);
            
        } catch (NamingException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        } catch (Exception e) {
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
            SolJMSRRDirectRequester instance = new SolJMSRRDirectRequester();

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
                } else if (args[i].equals("-rt")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.topicJNDIName = args[i];
                } else if (args[i].equals("-compression")) {
                    instance.compression = true;      
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
            if ((instance.topicJNDIName == null)) {
                instance.printUsage();
                System.out.println("Please specify the request destination topic (REQUEST_TOPIC_JNDI_NAME)");
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
