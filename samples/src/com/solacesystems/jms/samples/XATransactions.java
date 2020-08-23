/**
 * XATransactions.java
 * 
 * This sample demonstrates the use of XA Transactions outside an application server. 
 * 
 * Usage: run XATransactions -username USERNAME [-password PASSWORD] [-vpn VPN] -url APPLIANCE_URL 
 * 
 * Where:
 * PASSWORD	is defaulted to empty string
 * APPLIANCE_URL is the url to access the appliance 
 *                   (e.g. smf://10.10.10.10:55555) 
 * 
 * Copyright 2012-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Calendar;
import java.util.Hashtable;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TemporaryQueue;
import javax.jms.Destination;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.solacesystems.common.xa.SolXid;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.Endpoint;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.SDTStream;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SolXAConnectionFactory;
import com.solacesystems.jms.SupportedProperty;

import java.util.Random;

public class XATransactions implements ExceptionListener {

    private static final String SOLJMS_INITIAL_CONTEXT_FACTORY = 
        "com.solacesystems.jndi.SolJNDIInitialContextFactory"; 
    
    // URL to the router.
    private String jndiProviderURL; 
    
    // The publisher/subscriber name, as configured with SolAdmin.
    private String username;
    
    // The publisher/subscriber password, as configured with an authentication server; default is empty.
    private String password = "";
    
    // The Message VPN on the appliance to connect to.
    private String vpn = null;
    
    // JMS XA Connection
    XAConnection connection = null;
        
    private  void printUsage() {
        System.out.println("\nUsage: \nrun XATransactions -username USERNAME [-password PASSWORD] [-vpn VPN]\n" +
                            "-url APPLIANCE_URL \nWhere:\n" +
                            "- PASSWORD  is defaulted to empty string \n" +
                            "- VPN defaults to the default vpn \n" +
                            "- APPLIANCE_URL is the URL to access the appliance (e.g. smf://10.10.10.10:55555) \n");
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
                
        XASession session = null;
        XAResource xaResource = null;
        MessageProducer producer = null;
        MessageConsumer consumer = null;
        
        try {
            SolXAConnectionFactory cf = SolJmsUtility.createXAConnectionFactory(env);
            cf.setDirectTransport(false);
            
            connection = cf.createXAConnection();
            connection.start();
            session = connection.createXASession();
            
            //TemporaryQueue queue = session.createTemporaryQueue(); 
            Destination queue = session.createQueue("q");
            
 
            producer = session.createProducer(queue);
            consumer = session.createConsumer(queue);
            
            xaResource = session.getXAResource();
            Random rand = new Random();
            int int_random1 = rand.nextInt(1000);
            Xid xid = createXid(1,int_random1);
            xaResource.start(xid, XAResource.TMNOFLAGS);

            String myString = String.format("%1$-" + 32000 + "s", "Charles");
            Message send = session.createTextMessage(myString);
            
            for(int count=0; count<20; count++)
            {
            	producer.send(send);
            }

            xaResource.end(xid, XAResource.TMSUCCESS);
            
            xaResource.commit(xid, false);
            
            Message rcvd = consumer.receive(10000);
            System.out.println(rcvd);
            
            int int_random = rand.nextInt(1000);
            xid = createXid(1,int_random);
            
            /* Starts work on behalf of a transaction branch specified in xid. 
             * The transaction branch will also contain messages received before the method is called.
             */
            xaResource.start(xid, XAResource.TMNOFLAGS);
            
            xaResource.end(xid, XAResource.TMSUCCESS);
            xaResource.prepare(xid);
            

            
            xaResource.commit(xid, true);   
        } catch (Exception e) {
        	e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {}
            }
        }
        System.out.println("DONE");    
        System.exit(0);
    }
    
    /*
     * Creates an Xid using a Solace provided implementation.
     * An alternate implementation of javax.transaction.xa.Xid can be used instead.
     */
    protected Xid createXid(int gid, int bid) {
    	return new SolXid(0, new byte[] {(byte) gid}, new byte[] {(byte) bid});
    }
    
    public static void main(String[] args) {
        try {
            XATransactions instance = new XATransactions();

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
}
