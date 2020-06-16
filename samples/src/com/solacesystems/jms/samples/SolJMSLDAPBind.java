/**
 * SolJMSLDAPBind.java
 * 
 * This sample binds/rebinds/unbinds a JMS administered object into an LDAP.
 *
 * This sample requires:
 * - An LDAP server to be running and configured to allow the 
 *   specified username and password. This sample was tested using OpenLDAP for windows 2.4.26.
 *
 * Usage: 
 * run SolJMSLDAPBind -ldapURL URL -ldapUsername USERAME -ldapPassword PASSWORD -operation OPERATION [-cf] [-topic TOPIC] [-queue QUEUE] -dn DN
 * Where:
 * - OPERATION  is one of [BIND, REBIND, UNBIND]
 * 
 * Example Command Line arguments (binds a queue):
 * -ldapURL ldap://localhost:389 -ldapUsername cn=Manager,dc=solacesystems,dc=com -ldapPassword secret 
 * -operation BIND -queue queue1  -dn cn=queue1,dc=solacesystems,dc=com
 * 
 * Copyright 2004-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;
import java.util.Iterator;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.Reference;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SolQueue;
import com.solacesystems.jms.SolTopic;

public class SolJMSLDAPBind {     
    // Operation to perform
    public enum Operation {
        BIND,       // adds an element to the LDAP
        REBIND,     // replaces an element in the LDAP
        UNBIND,     // removes an element from the LDAP
        LIST        // lists elements in the LDAP
    };
    
    // LDAP Initial Context Factory
    private static final String LDAP_INITIAL_CONTEXT_FACTORY = 
            "com.sun.jndi.ldap.LdapCtxFactory"; 
    
    // The URL of the LDAP server
    private String ldapURL = null;
    
    // Username used to log into the ldap server
    private String ldapUsername = null;

    // Password used to log into the ldap server
    private String ldapPassword = null;

    // the bind operation to perform
    private Operation operation = null;
    
    // the object to bind - a connection factory, topic, or queue
    private boolean cf = false;
    private String topicName = null;    
    private String queueName = null;
    
    // The distinguished name of the element to bind
    private String dn = null;
            
    private void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSLDAPBind -ldapURL URL -ldapUsername USERAME -ldapPassword PASSWORD -operation OPERATION [-cf] [-topic TOPIC] [-queue QUEUE] -dn DN\n" +
                           "Where:\n" + 
                           "- OPERATION  is one of [BIND, REBIND, UNBIND, LIST]\n");
    }
        
    private void run() {
        Context ctx = null;
        try {
            // Create the LDAP Initial Context
            Hashtable<String,String> env = new Hashtable<String,String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_INITIAL_CONTEXT_FACTORY);
            env.put(Context.PROVIDER_URL, ldapURL);
            env.put(Context.REFERRAL, "throw");
            env.put(Context.SECURITY_PRINCIPAL, ldapUsername);
            env.put(Context.SECURITY_CREDENTIALS, ldapPassword);
            ctx = new InitialContext(env);

            // Just unbind and return
            if (operation.equals(Operation.UNBIND)) {
                NamingEnumeration<NameClassPair> enumer = ctx.list(dn);
                if (enumer.hasMore()) {
                    while(enumer.hasMore()) {
                        NameClassPair pair = enumer.next();
                        ctx.unbind(pair.getName() + "," + dn);
                    }
                } else {
                    ctx.unbind(dn);
                }
                return;
            }

            // Create the object to bind and get its reference
            Reference ref = null;
            if (topicName != null) {
                SolTopic topic = SolJmsUtility.createTopic(topicName);
                ref = topic.getReference();
            } else if (queueName != null) {
                SolQueue queue = SolJmsUtility.createQueue(queueName);
                ref = queue.getReference();
            } else if ((operation.equals(Operation.BIND)) || operation.equals(Operation.REBIND)) {
                SolConnectionFactory cf = SolJmsUtility.createConnectionFactory((Hashtable<?,?>)null);
                // Get the default values and set the connection factory properties
                Iterator<String> it = cf.getPropertyNames().iterator();
                while(it.hasNext()) {
                    String name = it.next();
                    Object effectiveValue = cf.getEffectiveProperty(name);
                    if (effectiveValue != null) {
                        cf.setProperty(name, effectiveValue);
                    }
                }
                ref = cf.getReference();
            }

            // bind or rebind the object
            if (operation.equals(Operation.BIND)) {
                ctx.bind(dn, ref);
            } else if (operation.equals(Operation.REBIND)) {
                ctx.rebind(dn, ref);
            } else {
                NamingEnumeration<NameClassPair> enumer = ctx.list(dn);
                System.out.println("Listing of " + dn + " {");
                while(enumer.hasMore()) {
                    NameClassPair pair = enumer.next();
                    System.out.println(pair.getName());
                }
                System.out.println("}\n");
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception e) {}
            }
        }
    }
        
    public static void main(String[] args) {
        try {
            SolJMSLDAPBind instance = new SolJMSLDAPBind();
             for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-ldapURL")) {
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
                } else if (args[i].equals("-operation")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.operation = Operation.valueOf(args[i]);
                } else if (args[i].equals("-cf")) {
                    instance.cf = true;
                } else if (args[i].equals("-topic")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.topicName = args[i];
                } else if (args[i].equals("-queue")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.queueName = args[i];      
                } else if (args[i].equals("-dn")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.dn = args[i];      
                } else {
                    instance.printUsage();
                    System.out.println("Illegal argument specified - " + args[i]);
                    return;
                }
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
            if (instance.operation == null) {
                instance.printUsage();
                System.out.println("Please specify \"-operation\" parameter");
                return;
            }
            if ((!instance.operation.equals(Operation.UNBIND)) && (!instance.operation.equals(Operation.LIST))) {
                if ((!instance.cf) && (instance.queueName == null) && (instance.topicName == null)) {
                    instance.printUsage();
                    System.out.println("Please specify one of [-cf, -topic, -queue]");
                    return;
                }
            }
            if (instance.dn == null) {
                instance.printUsage();
                System.out.println("Please specify \"-dn\" parameter");
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
