/**
 * Replication.java
 * 
 * This sample illustrates the use of an unacked list when using replication
 * with host lists.
 *
 * This sample publishes persistent messages while maintaining 
 * an unacked list.
 *
 * Notice that the specified username and ConnectionFactory 
 * should exist in your configuration (configured with SolAdmin).
 *
 * Usage: run Replication -username USERNAME [-password PASSWORD] -url JNDI_PROVIDER_URL 
 *           [-cf CONNECTION_FACTORY_JNDI_NAME]
 * Where:
 * PASSWORD is defaulted to empty string
 * JNDI_PROVIDER_URL is the url to access the jndi store 
 *                   (e.g. smf://10.10.10.10:55555) 
 * CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default 
 * 
 * Copyright 2012-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;
import java.util.LinkedList;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.DeliveryMode;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.solacesystems.jms.SolConnectionEventListener;
import com.solacesystems.jms.SolConnectionEventSource;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SolProducerEventListener;
import com.solacesystems.jms.SolProducerEventSource;
import com.solacesystems.jms.SupportedProperty;
import com.solacesystems.jms.events.SolConnectionEvent;
import com.solacesystems.jms.events.SolProducerEvent;
import com.solacesystems.jms.events.SolRepublishUnackedMessageEvent;

public class Replication implements ExceptionListener, SolProducerEventListener, SolConnectionEventListener {

    public class UnackedList {
        
        private LinkedList<Message> mylist = new LinkedList<Message>();
        
        synchronized public void add(Message msg) {
            mylist.add(msg);
        }
        
        synchronized public void remove(String key) throws JMSException {
            for(int i = 0; i < mylist.size(); i++) {
                Message msg = mylist.get(i);
                if ((msg.getJMSCorrelationID() != null) &&
                    (msg.getJMSCorrelationID().equals(key))) {
                    mylist.remove(i);
                    return;
                }
            }
            throw new IllegalArgumentException("Message for key \"" + key + "\" not found");
        }
        
        synchronized public Message[] get() {
            Message[] msgs = new Message[mylist.size()];
            for(int i = 0; i < mylist.size(); i++) {
                msgs[i] = mylist.get(i);
            }
            return msgs;
        }
        
        synchronized public String toString() {
            if (mylist.size() == 0) {
                return "Unacked List Empty";
            } else {
                StringBuffer buf = new StringBuffer("UnackedList= ");
                for(int i = 0; i < mylist.size(); i++) {
                    if (i > 0) {
                        buf.append(",");
                    }
                    try {
                        buf.append(mylist.get(i).getJMSCorrelationID());
                    } catch (JMSException e) {
                    }
                }
                return buf.toString();
            }
        }
    }

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
        
    // The number of messages to send.
    private int numMsgsToSend = 100000;
    
    // JMS Connection
    Connection connection = null;    

    // Unacked list
    private UnackedList unackedlist = new UnackedList();
    
    // Number of resent messages
    private int numMsgsResent = 0;
    
    private  void printUsage() {
        System.out.println("\nUsage: \nrun Replication -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                            "-url JNDI_PROVIDER_URL \nWhere:\n" +
                            "- PASSWORD  is defaulted to empty string \n" +
                            "- VPN defaults to the default vpn \n" +
                            "- JNDI_PROVIDER_URL is the URL to access the JNDI store (e.g. smf://10.10.10.10:55555) \n");
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
                
        Session session = null;
        Topic topic = null;
        MessageProducer producer = null;
        
        try {
            SolConnectionFactory cf = SolJmsUtility.createConnectionFactory(env);
            cf.setReconnectRetries(-1);
                    
            // Create connection.
            connection = cf.createConnection();
            connection.setExceptionListener(this);
            SolConnectionEventSource solConnectionEventSource = (SolConnectionEventSource)connection;
            solConnectionEventSource.setConnectionEventListener(this);
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            topic = session.createTopic("replication_topic");
            
            producer = session.createProducer(topic);
            ((SolProducerEventSource)producer).setProducerEventListener(this);  // register for producer events
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         
            for (int i = 0; i < numMsgsToSend; i++) {
            	// Create and send a text message.
            	TextMessage testMessage = session.createTextMessage("Message " + i);
            	testMessage.setJMSCorrelationID("Message " + i);
            	unackedlist.add(testMessage);
            	producer.send(testMessage);
            	unackedlist.remove(testMessage.getJMSCorrelationID());
            	System.out.println("SENT: " + testMessage.getText());
            	testMessage = null;
            }
        } catch (JMSException e) {
        	System.out.println(e.getMessage());
        } catch (Exception e) {
            	System.out.println(e.getMessage());
        } finally {
        	if (connection != null) {
        		try {
        			connection.close();
        		} catch (Exception e) {}
            }
        }	
        
        if (unackedlist.get().length > 0) {
            System.err.println(unackedlist.get().length + " unacked messages");
        }
        else {
            System.out.println("Done: " + numMsgsToSend + " messages sent (with " +  numMsgsResent + " messages renumbered and resent)!" );
        }
        System.exit(0);
    }
    
    public static void main(String[] args) {
        try {
            Replication instance = new Replication();

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

            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    public void onException(JMSException exception) {
        exception.printStackTrace();
    }
    
    public void onEvent(SolProducerEvent event) {
        if (event instanceof SolRepublishUnackedMessageEvent) {
            SolRepublishUnackedMessageEvent e = (SolRepublishUnackedMessageEvent) event;
            numMsgsResent += e.getNumberOfUnackedMessages();
        }
        System.out.println(event.toString());
    }

	public void onEvent(SolConnectionEvent event) {
		System.out.println("Connection Event: " + event.toString());
	}
}
