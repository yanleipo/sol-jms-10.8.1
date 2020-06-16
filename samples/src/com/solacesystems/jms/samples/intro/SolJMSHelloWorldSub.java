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
 * SolJMSHelloWorldSub
 *
 * This sample shows the basics of creating session, connecting a session,
 * subscribing to a topic, and receiving a message. This is meant to be a
 * very basic example for demonstration purposes.
 */

package com.solacesystems.jms.samples.intro;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

public class SolJMSHelloWorldSub {
    
    public static void main(String... args) throws JMSException, NamingException {
        if (args.length < 5) {
            System.out.println("Usage: JMSHelloWorldSub <jndi-provider-url> <vpn> <client-username> <connection-factory> <jndi-topic>");
            System.exit(-1);
        }

        // The client needs to specify both of the following properties:
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
        Topic topic = (Topic)initialContext.lookup((String)args[4]);

        // From the session, create a consumer for the destination.
        MessageConsumer consumer = session.createConsumer(topic);
           
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
                System.out.println("Received a JMS Message:\n" + SolJmsUtility.dumpMessage(testMessage));
            }
        }
    }
}
