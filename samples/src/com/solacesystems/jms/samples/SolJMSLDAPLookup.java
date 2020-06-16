/**
 * SolJMSLDAPLookup.java
 * 
 * This sample looks up a connection factory and destination in an LDAP,
 * creates a connection, and then sends a message to, and receives a 
 * message from the destination.
 *
 * This sample requires:
 * - An LDAP server to be running and configured to allow the 
 *   specified username and password. This sample was tested using OpenLDAP for windows 2.4.26.
 * - A Connection Factory and Destination be present in the LDAP
 *   (this can be achieved through the sample SolJMSLDAPBind.java).
 * - the specified username, durable subscription name 
 * (which is mapped to the name of a durable Topic Endpoint)
 * should exist in your configuration (configured with SolAdmin).
 *
 * Usage: 
 * run SolJMSLDAPLookup -routerIP ROUTER_IP -username USERNAME [-password PASSWORD] [-vpn VPN] [-durableSN DURABLE_SUBSCRIPTION_NAME]
 * -ldapURL URL -ldapUsername USERAME -ldapPassword PASSWORD -ldapCFDN CONNECTION_FACTORY_DN -ldapDestDN DESTINATION_DN
 * Where:
 * - PASSWORD  is defaulted to empty string 
 * - VPN defaults to the default vpn 
 * 
 * Example Command Line arguments:
 * -routerIP 192.168.160.28 -username user1 -ldapURL ldap://localhost:389 -ldapUsername cn=Manager,dc=solacesystems,dc=com 
 * -ldapPassword secret -ldapCFDN cn=cf1,dc=solacesystems,dc=com -ldapDestDN cn=topic1,dc=solacesystems,dc=com
 * 
 * Copyright 2004-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.solacesystems.jms.SolConnectionFactory;

public class SolJMSLDAPLookup {    
    // IP Address of the appliance 
    private String routerIP = null;
    
    // The username, as configured with SolAdmin.
    private String username = null;
        
    // The password, as configured with an authentication server; default is empty.
    private String password = "";
        
    // The Message VPN on the appliance to connect to.
    private String vpn = null;
    
    // The Durable Subscription Name, which is mapped to the name of a Durable Topic Endpoint configured with SolAdmin.
    private String durableSubscriptionName;

    // LDAP Initial Context Factory
    private static final String LDAP_INITIAL_CONTEXT_FACTORY = 
            "com.sun.jndi.ldap.LdapCtxFactory"; 

    // The URL to the LDAP server
    private String ldapURL;
    
    // Username used to log into the ldap server
    private String ldapUsername;

    // Password used to log into the ldap server
    private String ldapPassword;

    // Connection Factory Distinguished Name - Must exist in the LDAP
    private String ldapCFDN;
    
    // Destination Distinguished Name  - Must exist in the LDAP
    private String ldapDestDN;
        
    private void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSLDAPLookup -routerIP ROUTER_IP -username USERNAME [-password PASSWORD] [-vpn VPN] [-durableSN DURABLE_SUBSCRIPTION_NAME]\n" +
                           "-ldapURL URL -ldapUsername USERAME -ldapPassword PASSWORD -ldapCFDN CONNECTION_FACTORY_DN -ldapDestDN DESTINATION_DN\n" +
                           "Where:\n" + 
                           "- PASSWORD  is defaulted to empty string \n" +
                           "- VPN defaults to the default vpn \n");
    }
        
    private void run() {   
        
        // Initial Context
        InitialContext ctx = null;

        // JMS Connection
        Connection connection = null;
            
        try {
            // Create the LDAP Initial Context
            Hashtable<String,String> env = new Hashtable<String,String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_INITIAL_CONTEXT_FACTORY);
            env.put(Context.PROVIDER_URL, ldapURL);
            env.put(Context.REFERRAL, "throw");
            env.put(Context.SECURITY_PRINCIPAL, ldapUsername);
            env.put(Context.SECURITY_CREDENTIALS, ldapPassword);
            ctx = new InitialContext(env);

            // lookup the connection factory
            SolConnectionFactory cf = (SolConnectionFactory)ctx.lookup(ldapCFDN);
            cf.setHost(routerIP);
            cf.setUsername(username);
            cf.setPassword(password);
            cf.setVPN(vpn);
            
            // lookup the destination
            Object destination = ctx.lookup(ldapDestDN);
                            
            // Create a JMS Connection instance .
            connection = cf.createConnection();
                                    
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create the producer and consumer
            MessageConsumer consumer = null;
            MessageProducer producer = null;
            if (destination instanceof Topic) {
                Topic topic = (Topic)destination;
                if (durableSubscriptionName == null) {
                    consumer = session.createConsumer(topic);
                } else {
                    consumer = session.createDurableSubscriber(topic, durableSubscriptionName);
                }
                producer = session.createProducer(topic);
            } else if (destination instanceof Queue) {
                Queue queue = (Queue)destination;
                consumer = session.createConsumer(queue);
                producer = session.createProducer(queue);
            } else {
                printUsage();
                System.out.println("Destination must be a topic or a queue");
                System.exit(0);
            }
            
            // set the consumer's message listener
            consumer.setMessageListener(new MessageListener() {
                public void onMessage(Message message) {
                    if (message instanceof TextMessage) {
                        try {
                            System.out.println("Received Message: " + ((TextMessage)message).getText() + " on destination " + message.getJMSDestination());
                        } catch (JMSException e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("Received Message: " + message);
                    }
                }
            });
            
            // Start the JMS Connection.
            connection.start();

            // Send a message to the consumer
            Message testMessage = session.createTextMessage("SolJMSLDAPLookup Sample");
            producer.send(testMessage);
            
            // Wait for the message to be received and printed out before exiting
            Thread.sleep(2000);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {}
            }
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception e) {}
            }
        }
    }
        
    public static void main(String[] args) {
        try {
            SolJMSLDAPLookup instance = new SolJMSLDAPLookup();
             for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-routerIP")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.routerIP = args[i];
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
                } else if (args[i].equals("-ldapURL")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.ldapURL = args[i];
                } else if (args[i].equals("-ldapUsername")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.ldapUsername = args[i];
                } else if (args[i].equals("-ldapPassword")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.ldapPassword = args[i];      
                } else if (args[i].equals("-ldapCFDN")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.ldapCFDN = args[i];      
                } else if (args[i].equals("-ldapDestDN")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.ldapDestDN = args[i];      
                } else {
                    instance.printUsage();
                    System.out.println("Illegal argument specified - " + args[i]);
                    return;
                }
            }
               
            if (instance.routerIP == null) {
                instance.printUsage();
                System.out.println("Please specify \"-routerIP\" parameter");
                return;
            }
            if (instance.username == null) {
                instance.printUsage();
                System.out.println("Please specify \"-username\" parameter");
                return;
            }
            if (instance.ldapURL == null) {
                instance.printUsage();
                System.out.println("Please specify \"-ldapURL\" parameter");
                return;
            }
            if (instance.ldapUsername == null) {
                instance.printUsage();
                System.out.println("Please specify \"-ldapUsername\" parameter");
                return;
            }
            if (instance.ldapPassword == null) {
                instance.printUsage();
                System.out.println("Please specify \"-ldapPassword\" parameter");
                return;
            }
            if (instance.ldapCFDN == null) {
                instance.printUsage();
                System.out.println("Please specify \"-ldapCFDN\" parameter");
                return;
            }
            if (instance.ldapDestDN == null) {
                instance.printUsage();
                System.out.println("Please specify \"-ldapDestDN\" parameter");
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
