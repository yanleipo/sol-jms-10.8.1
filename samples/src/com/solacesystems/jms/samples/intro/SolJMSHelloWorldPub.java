/**
 *  Copyright 2012-2020 Solace Corporation. All rights reserved.
 *
 *  http://www.solace.com
 *
 *  This source is distributed under the terms and conditions
 *  of any contract or contracts between Solace and you or
 *  your company. If there are no contracts in place use of
 *  this source is not authorized. No support is provided and
 *  no distribution, sharing with others or re-use of this
 *  source is authorized unless specifically stated in the
 *  contracts referred to above.
 *
 * SolJMSHelloWorldPub
 *
 * This sample shows the basics of creating session, connecting a session,
 * and publishing a direct message to a topic. This is meant to be a very
 * basic example for demonstration purposes.
 */

package com.solacesystems.jms.samples.intro;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SupportedProperty;

public class SolJMSHelloWorldPub {
	
	public static void main(String... args) throws JMSException, NamingException {
    	// Check command line arguments
        if (args.length < 5) {
            System.out.println("Usage: SolJMSHelloWorldPub <jndi-provider-url> <vpn> <client-username> <connection-factory> <jndi-topic>");
            System.exit(-1);
        }
        System.out.println("SolJMSHelloWorldPub initializing...");
        
        // The client needs to specify all of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
        env.put(InitialContext.PROVIDER_URL, (String)args[0]);
        env.put(SupportedProperty.SOLACE_JMS_VPN, (String)args[1]);
        env.put(Context.SECURITY_PRINCIPAL, (String)args[2]);

        // InitialContext is used to lookup the JMS administered objects.
    	InitialContext initialContext = new InitialContext(env);
        // Lookup ConnectionFactory.
    	ConnectionFactory cf = (ConnectionFactory)initialContext.lookup((String)args[3]);    	
    	// JMS Connection
    	Connection connection = cf.createConnection();

        // Create a non-transacted, Auto Ack session.
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Lookup Topic.
        Destination destination = (Destination)initialContext.lookup((String)args[4]);

        // From the session, create a producer for the destination.
        // Use the default delivery mode as set in the connection factory
        MessageProducer producer = session.createProducer(destination);

        // Create a text message.
        TextMessage testMessage = session.createTextMessage("Hello world!");
        
        System.out.printf("Connected. About to send message '%s' to topic '%s'...%n", testMessage.getText(), destination.toString());

        producer.send(testMessage);
        System.out.println("Message sent. Exiting.");

        connection.close();

        initialContext.close();
    }    
}
