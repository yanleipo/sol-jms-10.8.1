/**
 * SolJMSRRGuaranteedRequester.java
 * 
 * This sample shows how to implement a Requester for guaranteed Request-Reply messaging, where 
 *
 *    SolJMSRRGuaranteedRequester: A message Endpoint that sends a guaranteed request message and waits
 *                                 to receive a reply message as a response.
 *    SolJMSRRGuaranteedReplier:   A message Endpoint that waits to receive a request message and responses
 *                                 to it by sending a guaranteed reply message.
 *             
 *  |-----------------------------|  -- RequestQueue/RequestTopic --> |----------------------------|
 *  | SolJMSRRGuaranteedRequester |                                   | SolJMSRRGuaranteedReplier  |
 *  |-----------------------------|  <-------- ReplyQueue ----------  |----------------------------|
 *
 * Notes: the SolJMSRRGuaranteedReplier supports request queue or topic formats, but not both at the same time.
 * 
 * Usage: run SolJMSRRGuaranteedRequester -username USERNAME [-password PASSWORD] [-vpn VPN]
 *                                        -url JNDI_PROVIDER_URL [-cf CONNECTION_FACTORY_JNDI_NAME] 
 *                                        -rt TOPIC_JNDI_NAME -rq QUEUE_JNDI_NAME
 * Where:
 * PASSWORD	is defaulted to empty string
 * VPN defaults to the default vpn
 * JNDI_PROVIDER_URL is the URL to access the JNDI store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default
 * TOPIC_JNDI_NAME is the JNDI name of the topic to use for the request
 * QUEUE_JNDI_NAME is the JNDI name of the queue to use for the request
 * Note: either -rt or -rq must be specified, but not both.
 * 
 * Copyright 2013-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicRequestor;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SupportedProperty;

public class SolJMSRRGuaranteedRequester {
    private static final String SOLJMS_INITIAL_CONTEXT_FACTORY = 
            "com.solacesystems.jndi.SolJNDIInitialContextFactory"; 
        
    // The URL to the JNDI data store. 
    private String jndiProviderURL; 
    
    // The publisher/subscriber name, as configured with SolAdmin.
    private String username;
    
    // Publisher/subscriber password, as configured with an authentication server; default is empty.
    private String password = "";
    
    // The Message VPN on the appliance to connect to.
    private String vpn = null;
    
    // The default JNDI name of the connection factory.
    private String cfJNDIName = "cf/default";
    
    // The JNDI name of the durable subscription
    private String requestTopic = null;
    
    // The JNDI name of the queue
    private String requestQueue = null;
    
    // Format for the arithmetic operation
    private final String ARITHMETIC_EXPRESSION = "\t=================================\n\t  %d %s %d = %s  \t\n\t=================================\n";
    
    enum Operation {
        PLUS,
        MINUS,
        TIMES,
        DIVIDE
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
    
    private void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSRRGuaranteedRequester -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                           "-url JNDI_PROVIDER_URL \n" +
                           "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                           "-rt TOPIC_JNDI_NAME\n" +
                           "-rq QUEUE_JNDI_NAME\n" +
                           "Where:\n" + 
                           "- PASSWORD  is defaulted to empty string \n" +
                           "- VPN defaults to the default vpn \n" +
                           "- JNDI_PROVIDER_URL is the URL to access the JNDI store (for example, smf://10.10.10.10:55555) \n" + 
                           "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" +
                       	   "- TOPIC_JNDI_NAME is the JNDI name of the topic to use for the request.  Note: either -rt or -rq must be specified, but not both.\n" +
                       	   "- QUEUE_JNDI_NAME is the JNDI name of the queue to use for the request.  Note: either -rt or -rq must be specified, but not both.\n");
    }
    
    // InitialContext is used to lookup the JMS administered objects.
    InitialContext initialContext = null;
    
    private void doTopicRequest(SolConnectionFactory cf, Operation operation, int leftHandOperand, int rightHandOperand) throws Exception {
        //JMS Connections used to make the request 
        TopicConnection topicConnection = null;
        //Session for the request/reply.
        TopicSession session = null;
        
        // Lookup Destination.
        Topic topic = (Topic)initialContext.lookup(requestTopic);
        
        // Create a JMS Connection instance using the specified username/password.
        topicConnection = cf.createTopicConnection();
        
        // Create a non-transacted, Auto Ack session.
        session = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

        // Do not forget to start the JMS Connection.
        topicConnection.start();
        
        // Create the topic requestor.
        TopicRequestor requestor = new TopicRequestor(session, topic);

        // Create and send the request.
        // Create a request message.
        StreamMessage requestMessage;
        requestMessage = session.createStreamMessage();
        requestMessage.writeByte(getOperationOrdinal(operation));
        requestMessage.writeInt(leftHandOperand);
        requestMessage.writeInt(rightHandOperand);
        Message incomingMessage = requestor.request(requestMessage);
        
        //We are done
        topicConnection.close();
        
        if (incomingMessage instanceof StreamMessage) {
            System.out.println("Got reply message");
            
            StreamMessage replyMessage = (StreamMessage)incomingMessage;
            if (replyMessage.readBoolean()) {
                System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, Double.toString(replyMessage.readDouble())));
            } else {
                System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, "operation failed"));
            }
        } else {
            System.out.println("Request failed");
        }
    }
    
    private void doQueueRequest(SolConnectionFactory cf, Operation operation, int leftHandOperand, int rightHandOperand) throws Exception {
        //JMS Connections used to make the request 
        QueueConnection queueConnection = null;
        //Session for the request/reply.
        QueueSession session = null;
        
        // Lookup Destination.
        Queue queue = (Queue)initialContext.lookup(requestQueue);
        
        // Create a JMS Connection instance using the specified username/password.
        queueConnection = cf.createQueueConnection();
        
        // Create a non-transacted, Auto Ack session.
        session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        // Do not forget to start the JMS Connection.
        queueConnection.start();
        
        // Create the queue requestor.
        QueueRequestor requestor = new QueueRequestor(session, queue);

        // Create and send the request.
        // Create a request message.
        StreamMessage requestMessage;
        requestMessage = session.createStreamMessage();
        requestMessage.writeByte(getOperationOrdinal(operation));
        requestMessage.writeInt(leftHandOperand);
        requestMessage.writeInt(rightHandOperand);
        Message incomingMessage = requestor.request(requestMessage);
        
        //We are done
        queueConnection.close();
        
        if (incomingMessage instanceof StreamMessage) {
            System.out.println("Got reply message");
            
            StreamMessage replyMessage = (StreamMessage)incomingMessage;
            if (replyMessage.readBoolean()) {
                System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, Double.toString(replyMessage.readDouble())));
            } else {
                System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, "operation failed"));
            }
        } else {
            System.out.println("Request failed");
        }
    }
    
    private void run() {
        // The client needs to specify both of the following properties:
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, SOLJMS_INITIAL_CONTEXT_FACTORY);
        env.put(InitialContext.PROVIDER_URL, jndiProviderURL);
        env.put(Context.SECURITY_PRINCIPAL, username);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(SupportedProperty.SOLACE_JMS_SSL_VALIDATE_CERTIFICATE, "false");  // enables the use of smfs:// without specifying a trust store
        if (vpn != null) {
            env.put(SupportedProperty.SOLACE_JMS_VPN, vpn);
        }
              
        try {
            // Create InitialContext.
            initialContext = new InitialContext(env);
            
            // Lookup ConnectionFactory.
            SolConnectionFactory cf = (SolConnectionFactory)initialContext.lookup(cfJNDIName);
            if (cf.getDirectTransport()) {
                System.out.println("Connection factory's direct transport option was enabled.  Overriding it to disabled.");
                cf.setDirectTransport(false);
            }
            
            if (requestTopic != null) {
                doTopicRequest(cf, Operation.PLUS, 5, 4);
                Thread.sleep(1000);
                doTopicRequest(cf, Operation.MINUS, 5, 4);
                Thread.sleep(1000);
                doTopicRequest(cf, Operation.TIMES, 5, 4);
                Thread.sleep(1000);
                doTopicRequest(cf, Operation.DIVIDE, 5, 4);
            } else {
                doQueueRequest(cf, Operation.PLUS, 5, 4);
                Thread.sleep(1000);
                doQueueRequest(cf, Operation.MINUS, 5, 4);
                Thread.sleep(1000);
                doQueueRequest(cf, Operation.TIMES, 5, 4);
                Thread.sleep(1000);
                doQueueRequest(cf, Operation.DIVIDE, 5, 4);
            }
            
            Thread.sleep(100); // Allow the consumer to ack its handled messages.
        } catch (NamingException e) {
            // Most likely we are not able to lookup an administered object (Topic, Queue, or ConnectionFactory).
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (initialContext != null) {
                try {
                    initialContext.close();
                } catch (Exception e) {}
            }
        }
    }
    
    public static void main(String[] args) {
        try {
            SolJMSRRGuaranteedRequester instance = new SolJMSRRGuaranteedRequester();

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
                    instance.requestTopic = args[i];
                } else if (args[i].equals("-rq")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.requestQueue = args[i];      
                } else {
                    instance.printUsage();
                    return;
                }
            }
            
            if (instance.jndiProviderURL == null 
                    || instance.username == null) {
                instance.printUsage();
                return;
            }
            
            if (instance.requestTopic == null && instance.requestQueue == null) {
                System.out.println("Missing required arguments: -rt or -rq");
                instance.printUsage();
                return;
            }
            
            if (instance.requestTopic != null && instance.requestQueue != null) {
                System.out.println("You must specify -rt or rq but not both");
                return;
            }
            
            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
