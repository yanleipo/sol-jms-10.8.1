/**
 * SolJMSRRDirectReplier.java
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
 * Usage: run SolJMSRRDirectReplier -username USERNAME [-password PASSWORD] [-vpn VPN]
 *                                  -url JNDI_PROVIDER_URL [-cf CONNECTION_FACTORY_JNDI_NAME] 
 *                                  -rt TOPIC_JNDI_NAME [-compression]
 * Where:
 * PASSWORD	is defaulted to empty string
 * VPN defaults to the default vpn
 * JNDI_PROVIDER_URL is the URL to access the JNDI store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default
 * TOPIC_JNDI_NAME is the JNDI name of the topic to listen on for incoming requests
 * -compression uses compression for JNDI lookups (use the appliance's compressed port in -url)
 *  
 * Copyright 2013-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SupportedProperty;

public class SolJMSRRDirectReplier {

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

    // Format for the arithmetic operation
    private final String ARITHMETIC_EXPRESSION = "\t=================================\n\t  %d %s %d = %s  \t\n\t=================================\n";
    
    enum Operation {
        PLUS,
        MINUS,
        TIMES,
        DIVIDE
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
            
            //Copy the correlation ID from the request to the reply
            replyMessage.setJMSCorrelationID(request.getJMSCorrelationID());
            //For direct messaging only, this flag is needed to interoperate with
            //CCSMP, CSCSMP and JCSMP request reply APIs.
            replyMessage.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE, Boolean.TRUE);
            
            //We have a result, thus we indicate a success
            replyMessage.writeBoolean(true);
            replyMessage.writeDouble(result);
            
            return replyMessage;
        }
        
        //Create a failure reply with no result
        private Message createReplyMessage(Message request) throws Exception {
            StreamMessage replyMessage = session.createStreamMessage();
            
            if (request.getJMSCorrelationID() == null) {
                throw new Exception("Received a request with no correlationID.  This field is needed for a direct request.");
            }
            
            //Copy the correlation ID from the request to the reply
            replyMessage.setJMSCorrelationID(request.getJMSCorrelationID());
            //For direct messaging only, this flag is needed to interoperate with
            //CCSMP, CSCSMP and JCSMP request reply APIs.
            replyMessage.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE, Boolean.TRUE);

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
            
            //Send the reply to the replyTo field of the request.
            
            //It is important to specify a delivery mode of NON-PERSISTENT here, because
            //only NON-PERSISTENT will be sent as DIRECT messages over the Solace bus
            //(NON-PERSISTENT are sent as DIRECT because the connection factory that was used
            // was sent to use Direct Messaging).
            //Leaving priority and Time to Live to their defaults.
            //NOTE: Priority is not supported by the Solace Message Bus
            producer.send(replyDestination, reply, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
        }
        
        public void onMessage(Message message) {
            System.out.println("Received request message, trying to parse it");

            if (message instanceof StreamMessage) {
                StreamMessage requestMessage = (StreamMessage)message;
                try {
                    byte operationByte = requestMessage.readByte();
                    int leftOperand = requestMessage.readInt();
                    int rightOperand = requestMessage.readInt();
                     
                    Operation operation = null;
                    try {
                        operation = getOperationEnum(operationByte);
                    } catch (Exception e) {
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
        System.out.println("\nUsage: \nrun SolJMSRRDirectReplier -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                            "-url JNDI_PROVIDER_URL \n" +
                            "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                            "-rt TOPIC_JNDI_NAME \n" +
                            "[-compression] \nWhere:\n" +
                            "- PASSWORD  is defaulted to empty string \n" +
                            "- VPN defaults to the default vpn \n" +
                            "- JNDI_PROVIDER_URL is the URL to access the JNDI store (e.g. smf://10.10.10.10:55555) \n" + 
                            "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" + 
                            "- TOPIC_JNDI_NAME is the JNDI name of the topic to listen on for incoming requests \n" +
                            "- -compression uses compression for JNDI lookups (use the appliance's compressed port in -url)\n");
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

        // InitialContext is used to lookup the JMS administered objects.
        InitialContext initialContext = null;
        
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
                System.out.println("The specified connection factory is not using direct messaging.  Overriding it to use direct messaging.");
                cf.setDirectTransport(true);
            }

            // Create connection.
            connection = cf.createConnection();
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Destination requestDestination = null;
            // Lookup Topic.
            if (topicJNDIName != null) {
                requestDestination = (Destination)initialContext.lookup(topicJNDIName);
            }

            MessageConsumer consumer = session.createConsumer(requestDestination);
            consumer.setMessageListener(new RequestHandler(session));
            
            //Start receiving messages
            connection.start();

            System.out.println("Listening for request messages ... Press enter to exit");
            System.in.read();
            
            //We are done
            connection.close();
        } catch (NamingException e) {
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
            SolJMSRRDirectReplier instance = new SolJMSRRDirectReplier();

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
