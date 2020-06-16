/**
 * SolJMSProgConsumer.java
 * 
 * This is a simple sample of a basic JMS Consumer created from a 
 * programmatically created connection factory.
 *
 * This sample subscribes to a specified Topic or Queue and
 * receives and prints the type of all received messages.
 *
 * Notice that the specified username, Topic, Queue, and durable subscription name 
 * (which is mapped to the name of a durable Topic Endpoint)
 * should exist in your configuration (configured with SolAdmin).
 *
 * Usage: run SolJMSProgConsumer -username USERNAME [-password PASSWORD] -host HOST [-vpn VPN]
 *           [-physicalTopic TOPIC] [-durableSN DURABLE_SUBSCRIPTION_NAME] [-tempTopic]
 *           [-physicalQueue QUEUE] [-tempQueue]
 * Where:
 * PASSWORD is defaulted to empty string
 * 
 * Copyright 2004-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;

public class SolJMSProgConsumer {
    private static class VMExitHandler extends Thread {
        private Connection connection;
        
        public VMExitHandler(Connection connection) {
            this.connection = connection;
        }
        
        public void run() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {}
            }
        }
    }
    
    // The appliance hostname or ip address. 
    private String host; 
    
    // The publisher/subscriber name, as configured with SolAdmin.
    private String username;
    
    // The publisher/subscriber password, as configured with an authentication server; default is empty.
    private String password = "";
    
    // The Message VPN on the appliance to connect to.
    private String vpn = null;

    // The Durable Subscription Name, which is mapped to the name of a Durable Topic Endpoint configured with SolAdmin.
    private String durableSubscriptionName;

    // The Physical name of the topic.
    private String topicName;
    
    // Whether to use a temporary topic.
    private boolean tempTopic = false;

    // The physical name of the queue.
    private String queueName;

    // Whether to use a temporary queue.
    private boolean tempQueue = false;
        
    private void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSProgConsumer -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                           "-host HOST \n" +
                           "[-durableSN DURABLE_SUBSCRIPTION_NAME] \n" +
                           "[-physicalTopic TOPIC_NAME] \n" +
                           "[-tempTopic] \n" + 
                           "[-physicalQueue QUEUE_NAME] \n" + 
                           "[-tempQueue]\nWhere:\n" +
                           "- PASSWORD  is defaulted to empty string \n" +
                           "- VPN defaults to the default vpn \n" +
                           "- Only one of [-physicalTopic, -tempTopic, -physicalQueue, -tempQueue] can be specified\n");
    }
    
    private void run() {
        
        // JMS Connection
        Connection connection = null;
        
        try {
            // Create the connection factory
            SolConnectionFactory cf = SolJmsUtility.createConnectionFactory();
            cf.setHost(host);
            cf.setUsername(username);
            cf.setPassword(password);
            cf.setDirectTransport(false);
            cf.setSSLValidateCertificate(false);  // enables the use of smfs://  without specifying a trust store
            if (vpn != null) {
                cf.setVPN(vpn);
            }
            
            // Create a JMS Connection instance using the specified username/password.
            connection = cf.createConnection();
                          
            Runtime.getRuntime().addShutdownHook(new VMExitHandler(connection));
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = null;
            // Create a Topic.
            if (topicName != null) {
                topic = session.createTopic(topicName);
            }
            
            // Create a Temporary Topic.
            if (tempTopic) {
                topic = session.createTemporaryTopic();
            }

            Queue queue = null;
            // Create a Queue.
            if (queueName != null) {
                queue = session.createQueue(queueName);
            }

            // Create a Temporary Queue.
            if (tempQueue) {
                queue = session.createTemporaryQueue();
            }

            // From the session, create a consumer for the destination.
            MessageConsumer consumer = null;
            if (topic != null) {
                if (durableSubscriptionName == null) {
                    consumer = session.createConsumer(topic);
                } else {
                    consumer = session.createDurableSubscriber(topic, durableSubscriptionName);
                }
            }
            else {
                consumer = session.createConsumer(queue);
            }
            
            // Do not forget to start the JMS Connection.
            connection.start();
            
            // Output a message on the console.
            System.out.println("Waiting for a message ... (press Ctrl+C) to terminate ");
            
            // Wait for messages.
            while (true) {
                Message testMessage = consumer.receive();
                if (testMessage == null) {
                    System.out.println("An error has occured... Exiting");
                    System.exit(1);
                } else {
                    System.out.println("Received a JMS Message of type: " + testMessage.getClass().getName());
                }
            }
        } catch (JMSException e) {
            if (e.getMessage().contains("Consumer was closed while in receive")) {
                System.out.println("Exiting.....");
            } else {
                e.printStackTrace();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        try {
            SolJMSProgConsumer instance = new SolJMSProgConsumer();

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-host")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.host = args[i];
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
                } else if (args[i].equals("-durableSN")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.durableSubscriptionName = args[i];      
                } else if (args[i].equals("-physicalTopic")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.topicName = args[i];      
                } else if (args[i].equals("-tempTopic")) {
                    instance.tempTopic = true;      
                } else if (args[i].equals("-physicalQueue")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.queueName = args[i];      
                } else if (args[i].equals("-tempQueue")) {
                    instance.tempQueue = true;      
                } else {
                    instance.printUsage();
                    System.out.println("Illegal argument specified - " + args[i]);
                    return;
                }
            }
            
            if (instance.host == null) {
                instance.printUsage();
                System.out.println("Please specify \"-host\" parameter");
                return;
            }
            if (instance.username == null) {
                instance.printUsage();
                System.out.println("Please specify \"-username\" parameter");
                return;
            }
            if ((instance.topicName == null) && (instance.queueName == null) &&
                (!instance.tempTopic) && (!instance.tempQueue)) {
                instance.printUsage();
                System.out.println("Please specify one of [-physicalTopic, -tempTopic, -physicalQueue, -tempQueue]");
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
