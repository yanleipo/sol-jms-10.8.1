/**
 * SolJMSRRGuaranteedReplier.java
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
 * Usage: run SolJMSRRGuaranteedReplier -username USERNAME [-password PASSWORD] [-vpn VPN]
 *                                     -url JNDI_PROVIDER_URL [-cf CONNECTION_FACTORY_JNDI_NAME] 
 *                                     -rt TOPIC_JNDI_NAME -rs DURABLE_SUBSCRIPTION_NAME -rq QUEUE_JNDI_NAME
 * Where:
 * PASSWORD	is defaulted to empty string
 * VPN defaults to the default vpn
 * JNDI_PROVIDER_URL is the URL to access the JNDI store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default
 * TOPIC_JNDI_NAME is the JNDI name of the topic to listen on for incoming requests
 * DURABLE_SUBSCRIPTION_NAME is the JNDI name of the durable topic subscription (Required when -rt is specified)
 * QUEUE_JNDI_NAME is the JNDI name of the queue receiving incoming requests
 * Note: either -rt or -rq must be specified, but not both.
 * 
 * Copyright 2013-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SupportedProperty;

public class SolJMSRRGuaranteedReplier {
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
    
    // Durable Subscription Name. (This is mapped to the name of a Durable 
    // Topic Endpoint as configured with the SolAdmin.)
    private String durableSubscriptionName;
    
    // The JNDI name of the topic for the durable subscriber
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
    
    Operation getOperationEnum(byte ordinal) throws Exception {
        switch(ordinal) {
        case 1:
            return Operation.PLUS;
        case 2:
            return Operation.MINUS;
        case 3:
            return Operation.TIMES;
        case 4:
            return Operation.DIVIDE;
        default:
            throw new Exception("Unknown operation value");
        }
    }

    class RequestHandler implements MessageListener {

        MessageProducer producer;
        Session session;
        
        public RequestHandler(Session session) throws JMSException {
            this.session = session;
            this.producer = session.createProducer(null);
        }
        
        private double computeOperation(Operation operation, int leftOperand, int rightOperand) throws Exception {
            switch(operation) {
                case PLUS:
                    return (double)(leftOperand + rightOperand);
                case MINUS:
                    return (double)(leftOperand - rightOperand);
                case TIMES:
                    return (double)(leftOperand * rightOperand);
                case DIVIDE:
                    return (double)((double)leftOperand / (double)rightOperand);
                default:
                    throw new Exception("Unkown operation");
            }
        }
        
        //Create a success reply with a result
        private Message createReplyMessage(Message request, double result) throws JMSException {
            StreamMessage replyMessage = session.createStreamMessage();

            //We have a result, thus we indicate a success
            replyMessage.writeBoolean(true);
            replyMessage.writeDouble(result);
            
            return replyMessage;
        }
        
        //Create a failure reply with no result
        private Message createReplyMessage(Message request) throws Exception {
            StreamMessage replyMessage = session.createStreamMessage();

            //We do not have a result, thus we indicate a failure
            replyMessage.writeBoolean(false);
            
            return replyMessage;
        }
        
        private void sendReply(Message request, Message reply) throws Exception {
            Destination replyDestination = null;
            try {
                replyDestination = request.getJMSReplyTo();
            } catch (JMSException e) {
                System.out.println("Exception while retrieving the request's replyto destination:");
                e.printStackTrace();
            }
            
            if (replyDestination == null) {
                System.out.println("Failed to parse the request message : Missing replyto destination.");
                System.out.println("Here's a message dump:" + request.toString());
                
                throw new Exception("Missing replyto destination");
            }            
            
            producer.send(replyDestination, reply);
        }
        
        public void onMessage(Message message) {
            System.out.println("Received request message, trying to parse it");

            if (message instanceof StreamMessage) {
                StreamMessage requestMessage = (StreamMessage)message;
                try {
                    Byte operationByte = requestMessage.readByte();
                    int leftOperand = requestMessage.readInt();
                    int rightOperand = requestMessage.readInt();
                    
                    Operation operation = null;
                    try {
                        operation = getOperationEnum(operationByte);
                    } catch(Exception e) {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, "UNKNOWN", rightOperand, "operation failed"));
                        Message reply = createReplyMessage(message);
                        sendReply(message, reply);
                        return;
                    }

                    System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, operation.toString(), rightOperand, "?"));
                    double result = computeOperation(operation, leftOperand, rightOperand);
                    if (Double.isInfinite(result) || Double.isNaN(result)) {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, operation.toString(), rightOperand, "operation failed"));
                        
                        try {
                            Message reply = createReplyMessage(message);
                            sendReply(message, reply);
                        } catch(Exception e) {
                            System.out.println("Couldn't reply to request : ");
                            e.printStackTrace();
                        }
                        //Operation failure
                        return;      
                    }
                    else {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, operation.toString(), rightOperand, Double.toString(result)));
    
                        try {
                            Message reply = createReplyMessage(message, result);
                            sendReply(message, reply);
                        } catch(Exception e) {
                            System.out.println("Couldn't reply to request : ");
                            e.printStackTrace();
                        }
                        //Success
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            System.out.println("Failed to parse the request message, here's a message dump:" + message.toString());
        }
    }
    
    private void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSRRGuaranteedReplier -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                           "-url JNDI_PROVIDER_URL \n" +
                           "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                           "-rt TOPIC_JNDI_NAME -rs DURABLE_SUBSCRIPTION_NAME \n" +
                           "-rq QUEUE_JNDI_NAME\n" + 
                           "Where:\n" + 
                           "- PASSWORD  is defaulted to empty string \n" +
                           "- VPN defaults to the default vpn \n" +
                           "- JNDI_PROVIDER_URL is the URL to access the JNDI store (for example, smf://10.10.10.10:55555) \n" + 
                           "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" +
                           "- TOPIC_JNDI_NAME is the JNDI name of the topic to listen on for incoming requests.  Note: either -rt or -rq must be specified, but not both.\n" +
                           "- DURABLE_SUBSCRIPTION_NAME is the JNDI name of the durable topic subscription (Required when -rt is specified) \n" +
                           "- QUEUE_JNDI_NAME is the JNDI name of the queue receiving incoming requests.  Note: either -rt or -rq must be specified, but not both.\n");
    }
    
    // InitialContext is used to lookup the JMS administered objects.
    InitialContext initialContext = null;

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

            Connection connection = cf.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            if (requestTopic != null) {
                Topic topic = (Topic)initialContext.lookup(requestTopic);
                TopicSubscriber subscriber = session.createDurableSubscriber(topic, durableSubscriptionName);
                subscriber.setMessageListener(new RequestHandler(session));
            } else {
                Queue queue = (Queue)initialContext.lookup(requestQueue);
                MessageConsumer consumer = session.createConsumer(queue);
                consumer.setMessageListener(new RequestHandler(session));
            }
            connection.start();
            
            System.out.println("Listening for request messages ... Press enter to exit");
            System.in.read();
            
            //We are done.
            connection.close();
            
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
            SolJMSRRGuaranteedReplier instance = new SolJMSRRGuaranteedReplier();

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
                } else if (args[i].equals("-rs")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.durableSubscriptionName = args[i];
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
            
            if ((instance.requestTopic != null && instance.durableSubscriptionName == null) || 
                  (instance.requestTopic == null && instance.durableSubscriptionName != null)) {
                System.out.println("You must specify both -rt and -rs for a guaranteed topic replier");
                instance.printUsage();
                return;
            }
            
            if (instance.requestTopic == null && instance.requestQueue == null) {
                System.out.println("Missing required arguments: -rt and -rs, or -rq");
                instance.printUsage();
                return;
            }
            
            if (instance.requestTopic != null && instance.requestQueue != null) {
                System.out.println("You must specify -rt or -rq but not both");
                return;
            }
            
            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
