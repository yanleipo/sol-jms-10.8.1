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
 *  SolJMSHelloWorldQueuePub
 *
 *  This sample shows the basics of creating session, connecting a session,
 *  looking up a queue, and publishing a guaranteed message to the queue.
 *  This is meant to be a very basic example for demonstration purposes.
 */

package com.solacesystems.jms.samples.intro;

import java.util.Hashtable;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SupportedProperty;

public class SolJMSHelloWorldQueuePub {
    
	public static void main(String... args) throws JMSException, NamingException {
        // Check command line arguments
        if (args.length < 5) {
            System.out.println("Usage: SolJMSHelloWorldPub <jndi-provider-url> <vpn> <client-username> <connection-factory> <jndi-queue>");
            System.out.println();
            System.out.println(" Note: the client-username provided must have adequate permissions in its client");
            System.out.println("       profile to send and receive guaranteed messages, and to create endpoints.");
            System.out.println("       Also, the message-spool for the VPN must be configured with >0 capacity.");
            System.exit(-1);
        }
        System.out.println("SolJMSHelloWorldQueuePub initializing...");
        
        // The client needs to specify all of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
        env.put(InitialContext.PROVIDER_URL, (String)args[0]);
        env.put(SupportedProperty.SOLACE_JMS_VPN, (String)args[1]);
        env.put(Context.SECURITY_PRINCIPAL, (String)args[2]);
        
        // InitialContext is used to lookup the JMS administered objects.
    	InitialContext initialContext = new InitialContext(env);
    	// Lookup ConnectionFactory.
    	QueueConnectionFactory cf = (QueueConnectionFactory)initialContext.lookup((String)args[3]);
    	// JMS Connection
    	QueueConnection connection = cf.createQueueConnection();

        // Create a non-transacted, Auto Ack session.
        Session session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        // Lookup Queue.
        Queue queue = (Queue)initialContext.lookup((String)args[4]);

        // From the session, create a producer for the destination.
        // Use the default delivery mode as set in the connection factory
        MessageProducer producer = session.createProducer(queue);

        // Create a text message.
        TextMessage testMessage = session.createTextMessage("Hello world!");

        System.out.printf("Connected. About to send message '%s' to queue '%s'...%n", testMessage.getText(), queue.toString());

        producer.send(testMessage);
        System.out.println("Message sent. Exiting.");

        connection.close();

        initialContext.close();
	}
}
