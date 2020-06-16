/**
 * SolJMSSecureSession.java
 
 * This is a simple sample of a Secure JMS Producer.
 *
 * This sample publishes 10 JMS Text Messages to a specified Topic or Queue
 *
 * Notice that the specified username, ConnectionFactory, Topic and Queue 
 * should exist in your configuration (configured with SolAdmin)
 *
 * A server certificate needs to be installed on the appliance and SSL must be
 * enabled on the appliance for this sample to work.
 * Also, in order to connect to the appliance with Certificate Validation enabled
 * (which is enabled by default), the appliance's certificate chain must be signed
 * by one of the root CAs in the trust store used by the sample.
 *
 * For this sample to use CLIENT CERTIFICATE authentication, a trust store has to
 * be set up on the appliance and it must contain the root CA that signed the client
 * certificate. The VPN must also have client-certificate authentication enabled.
 *
 * Usage: 
 * run SolJMSSecureSession [-username USERNAME] [-password PASSWORD] [-vpn VPN]
 * -url JNDI_PROVIDER_URL 
 * [-x AUTH_METHOD] 
 * [-cf CONNECTION_FACTORY_JNDI_NAME] 
 * [-prot PROTOCOL] 
 * [-ciphers CIPHERS] 
 * [-ts TRUST_STORE] 
 * [-tspwd TRUST_STORE_PASSWORD] 
 * [-tsfmt TRUST_STORE_FORMAT] 
 * [-ks KEY_STORE] 
 * [-kspwd KEY_STORE_PASSWORD] 
 * [-ksfmt KEY_STORE_FORMAT] 
 * [-ksnfmt KEY_STORE_NORMALIZED_FORMAT] 
 * [-pk PRIVATE_KEY_ALIAS] 
 * [-pkpwd PRIVATE_KEY_PASSWORD] 
 * [-no_validate_certificates] 
 * [-no_validate_dates] 
 * [-cn TRUSTED_COMMON_NAMES] 
 * 
 * Where:
 * - USERNAME is the username to use to authenticate.  Mandatory when BASIC authentication is used.  Optional when CLIENT_CERTIFICATE authentication is used.
 * - PASSWORD  is defaulted to empty string
 * - AUTH_METHOD authentication scheme (One of : BASIC, CLIENT_CERTIFICATE). (Default: BASIC).  Specifying USERNAME is mandatory when BASIC is used.
 * - VPN defaults to the default vpn 
 * - JNDI_PROVIDER_URL is the URL to access the JNDI store (e.g. smfs://10.10.10.10:55555 for a secure connection) 
 * - CONNECTION_FACTORY_JNDI_NAME  is defaulted to cf/default 
 * - PROTOCOL is defaulted to SSLv3,TLSv1,TLSv1.1,TLSv1.2
 * - CIPHERS is defaulted to all supported ciphers
 * - TRUST_STORE is the path to a trust store file that contains trusted root CAs.  This parameter is mandatory unless -no_validate_certificates is specified.  Used to validate the appliance's server certificate.
 * - TRUST_STORE_FORMAT is the format of the specified trust store.  Possible values : [JKS, PKCS12].  The default value is JKS.
 * - TRUST_STORE_PASSWORD is the password for the specified trust store.  This is used to check the trust store's integrity.
 * - TRUSTED_COMMON_NAMES is the list of acceptable common names for matching in server certificates (default: no CN validation performed)
 * - KEY_STORE is the key store file to use for client certificate authentication.  Mandatory when CLIENT_CERTIFICATE authentication is used.
 * - KEY_STORE_FORMAT is the format of the specified key store. The default value is JKS.
 * - KEY_STORE_NORMALIZED_FORMAT is the format of the internal normalized key store.  If not specified the format is the same as the key store format.
 * - KEY_STORE_PASSWORD is the password for the specified key store.  This is used to check the key store's integrity.  This parameter is mandatory when PRIVATE_KEY_PASSWORD is not specified.  It is also used to decipher the private key when the PRIVATE_KEY_PASSWORD option is omitted.
 * - PRIVATE_KEY_ALIAS is the alias of the private key to use for client certificate authentication.
 * - PRIVATE_KEY_PASSWORD is the password to decipher the private key from the key store (default: the value passed for KEY_STORE_PASSWORD).
 * 
 * Copyright 2004-2020 Solace Corporation. All rights reserved.
 */

package com.solacesystems.jms.samples;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SupportedProperty;

/**
 * Creates a JMS secure session that sends 10 JMS text messages.
 * 
 */
public class SolJMSSecureSession {
    
    private enum AuthenticationScheme {
        BASIC,
        CLIENT_CERTIFICATE,
        KERBEROS
    };
    
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

    private String excludeProtocols = null;
    
    private String ciphers = null;
    
    private String trustStore = null;
    
    private String trustStorePwd = null;
    
    private String trustStoreFmt = "JKS";

    private String keyStore = null;
    
    private String keyStorePwd = null;
    
    private String keyStoreFmt = "JKS";
    
    private String keyStoreNormalizedFmt = null;
    
    private String privateKeyAlias = null;

    private String privateKeyPwd = null;
    
    private boolean validateCertificates = true;
    
    private boolean validateCertificateDate = true;
    
    private String trustedCommonNames = null;
    
    private String sslConnectionDowngradeTo = null;
    
    private boolean compression = false;
    
    // The number of messages to send.
    private int numMsgsToSend = 10;
    
    // Kerberos authentication
    private AuthenticationScheme authScheme = AuthenticationScheme.BASIC;

    private  void printUsage() {
        System.out.println("\nUsage: \nrun SolJMSSecureSession [-username USERNAME] [-password PASSWORD] [-vpn VPN]\n" +
                            "-url JNDI_PROVIDER_URL \n" +
                            "[-x AUTH_METHOD] \n" +
                            "[-cf CONNECTION_FACTORY_JNDI_NAME] \n" +
                            "[-compression] \n" +
                            "[-exclprots EXCLUDE_PROTOCOLS] \n" +
                            "[-ciphers CIPHERS] \n" +
                            "[-ts TRUST_STORE] \n" +
                            "[-tspwd TRUST_STORE_PASSWORD] \n" +
                            "[-tsfmt TRUST_STORE_FORMAT] \n" +
                            "[-ks KEY_STORE] \n" +
                            "[-kspwd KEY_STORE_PASSWORD] \n" +
                            "[-ksfmt KEY_STORE_FORMAT] \n" +
                            "[-ksnfmt KEY_STORE_NORMALIZED_FORMAT] \n" +
                            "[-pk PRIVATE_KEY_ALIAS] \n" +
                            "[-pkpwd PRIVATE_KEY_PASSWORD] \n" +
                            "[-no_validate_certificates] \n" +
                            "[-no_validate_dates] \n" +
                            "[-cn TRUSTED_COMMON_NAMES] \n" +
                            "[-d PLAIN_TEXT]\nWhere:\n" + 
                            "- USERNAME is the username to use to authenticate.  Mandatory when BASIC authentication is used.  Optional when CLIENT_CERTIFICATE authentication is used.\n" +
                            "- PASSWORD is defaulted to empty string \n" +
                            "- AUTH_METHOD authentication scheme (One of : BASIC, CLIENT_CERTIFICATE). (Default: BASIC).  Specifying USERNAME is mandatory when BASIC is used.\n" +
                            "- VPN defaults to the default vpn \n" +
                            "- JNDI_PROVIDER_URL is the URL to access the JNDI store (e.g. smfs://10.10.10.10:55555 for a secure connection) \n" + 
                            "- CONNECTION_FACTORY_JNDI_NAME  is defaulted to " + cfJNDIName + " \n" +
                            "- EXCLUDE_PROTOCOLS is defaulted to an empty string\n" +
                            "- CIPHERS is defaulted to all supported ciphers\n" +
                            "- TRUST_STORE is the path to a trust store file that contains trusted root CAs.  This parameter is mandatory unless -no_validate_certificates is specified.  Used to validate the appliance's server certificate.\n" +
                            "- TRUST_STORE_FORMAT is the format of the specified trust store.  Possible values : [JKS, PKCS12].  The default value is JKS.\n" + 
                            "- TRUST_STORE_PASSWORD is the password for the specified trust store.  This is used to check the trust store's integrity.\n" +
                            "- TRUSTED_COMMON_NAMES is the list of acceptable common names for matching in server certificates (default: no CN validation performed)\n" +
                            "- KEY_STORE is the key store file to use for client certificate authentication.  Mandatory when CLIENT_CERTIFICATE authentication is used.\n" + 
                            "- KEY_STORE_FORMAT is the format of the specified key store. The default value is JKS.\n" +
                            "- KEY_STORE_NORMALIZED_FORMAT is the format of the internal normalized key store.  If not specified the format is the same as the key store format.\n" +
                            "- KEY_STORE_PASSWORD is the password for the specified key store.  This is used to check the key store's integrity.  This parameter is mandatory when the PRIVATE_KEY_PASSWORD option is not specified.  It is also used to decipher the private key when the PRIVATE_KEY_PASSWORD option is omitted.\n" + 
                            "- PRIVATE_KEY_ALIAS is the alias of the private key to use for client certificate authentication.\n" +
                            "- PRIVATE_KEY_PASSWORD is the password to decipher the private key from the key store (default: the value passed for KEY_STORE_PASSWORD)." +
                            "- PLAIN_TEXT SSL connection downgrade protocol");
    }
    
    private void run() {
        
        // The client needs to specify both of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, SOLJMS_INITIAL_CONTEXT_FACTORY);
        env.put(InitialContext.PROVIDER_URL, jndiProviderURL);
        if (username != null) {
            env.put(Context.SECURITY_PRINCIPAL, username);
        }
        env.put(Context.SECURITY_CREDENTIALS, password);
        
        if (compression) {
            env.put(SupportedProperty.SOLACE_JMS_COMPRESSION_LEVEL, 9);     // non-zero compression level
        }
        
        // SSL
        if (excludeProtocols != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_EXCLUDED_PROTOCOLS, excludeProtocols);
        }
        if (ciphers != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_CIPHER_SUITES, ciphers);
        }
        if (trustStore != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_TRUST_STORE, trustStore);
        }
        env.put(SupportedProperty.SOLACE_JMS_SSL_TRUST_STORE_FORMAT, trustStoreFmt);
        if (trustStorePwd != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_TRUST_STORE_PASSWORD, trustStorePwd);
        }
        if (trustedCommonNames != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_TRUSTED_COMMON_NAME_LIST, trustedCommonNames);
        }
        env.put(SupportedProperty.SOLACE_JMS_SSL_VALIDATE_CERTIFICATE, validateCertificates);
        env.put(SupportedProperty.SOLACE_JMS_SSL_VALIDATE_CERTIFICATE_DATE, validateCertificateDate);
        
        if (keyStore != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_KEY_STORE, keyStore);
        }
        if (keyStorePwd != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_KEY_STORE_PASSWORD, keyStorePwd);
        }
        if (keyStoreFmt != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_KEY_STORE_FORMAT, keyStoreFmt);
        }
        if (keyStoreNormalizedFmt != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_KEY_STORE_NORMALIZED_FORMAT, keyStoreNormalizedFmt);
        }
        if (privateKeyAlias != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_PRIVATE_KEY_ALIAS, privateKeyAlias);
        }
        if (privateKeyPwd != null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_PRIVATE_KEY_PASSWORD, privateKeyPwd);
        }
        if (vpn != null) {
            env.put(SupportedProperty.SOLACE_JMS_VPN, vpn);
        }
        if (sslConnectionDowngradeTo !=  null) {
            env.put(SupportedProperty.SOLACE_JMS_SSL_CONNECTION_DOWNGRADE_TO, sslConnectionDowngradeTo);
        }
        if (authScheme.equals(AuthenticationScheme.BASIC)) {
            env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_BASIC);
        } else if (authScheme.equals(AuthenticationScheme.CLIENT_CERTIFICATE)) {
            env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_CLIENT_CERTIFICATE);
        } else {
            env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_GSS_KRB);
        }

        // InitialContext is used to lookup the JMS administered objects.
    	InitialContext initialContext = null;
    	
    	// JMS Connection
    	Connection connection = null;
    	
        try {
            // Create InitialContext.
            initialContext = new InitialContext(env);
        	
            // Lookup ConnectionFactory.
        	ConnectionFactory cf = (ConnectionFactory)initialContext.lookup(cfJNDIName);
        	
        	// Create connection.
            connection = cf.createConnection();
            
            // Print version information.
            ConnectionMetaData metadata = connection.getMetaData();
            System.out.println(metadata.getJMSProviderName() + " " + metadata.getProviderVersion());

            // Create a non-transacted, Auto Ack session.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Destination destination = session.createTopic("secure/session");

            // From the session, create a producer for the destination.
            // Use the default delivery mode as set in the connection factory
            MessageProducer producer = session.createProducer(destination);

            // Create a text message.
            TextMessage testMessage = session.createTextMessage("Hello from SolJMSSecureSession");

            
            System.out.println("About to send " + numMsgsToSend + " JMS Text Message(s)");
            // Send text message.
            for (int i = 0; i < numMsgsToSend; i++) {
                producer.send(testMessage);
                System.out.println("SENT: " + testMessage.getText());
                try {
                    Thread.sleep(1000);
                } catch (Exception ex) {}
            }
            System.out.println("DONE");            
        } catch (NamingException e) {
        	e.printStackTrace();
        } catch (JMSException e) {
        	e.printStackTrace();
        } finally {
        	if (connection != null) {
        		try {
        			connection.close();
        		} catch (Exception e) {}
        	}
        	if (initialContext != null) {
        		try {
        			initialContext.close();
        		} catch (Exception e) {}
        	}
        }
    }
    
    public static void main(String[] args) {
        try {
            SolJMSSecureSession instance = new SolJMSSecureSession();

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
                } else if (args[i].equals("-exclprots")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.excludeProtocols = args[i];
                } else if (args[i].equals("-ciphers")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.ciphers = args[i];      
                } else if (args[i].equals("-ts")) {
                	i++;
                	if (i >= args.length) instance.printUsage();
                	instance.trustStore = args[i];
                } else if (args[i].equals("-tsfmt")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.trustStoreFmt = args[i];      
                } else if (args[i].equals("-tspwd")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.trustStorePwd = args[i];      
                } else if (args[i].equals("-ks")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.keyStore = args[i];
                } else if (args[i].equals("-ksfmt")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.keyStoreFmt = args[i];      
                } else if (args[i].equals("-ksnfmt")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.keyStoreNormalizedFmt = args[i];      
                } else if (args[i].equals("-kspwd")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.keyStorePwd = args[i];      
                } else if (args[i].equals("-pk")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.privateKeyAlias = args[i];      
                } else if (args[i].equals("-pkpwd")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.privateKeyPwd = args[i];      
                } else if (args[i].equals("-no_validate_certificates")) {
                    instance.validateCertificates = false;      
                } else if (args[i].equals("-no_validate_dates")) {
                    instance.validateCertificateDate = false;      
                } else if (args[i].equals("-cn")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.trustedCommonNames = args[i];      
                } else if (args[i].equals("-x")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    if (args[i].toLowerCase().equals("basic")) {
                        instance.authScheme = AuthenticationScheme.BASIC;
                    } else if (args[i].toLowerCase().equals("client_certificate")) {
                        instance.authScheme = AuthenticationScheme.CLIENT_CERTIFICATE;     
                    } else if (args[i].toLowerCase().equals("kerberos")) {
                        instance.authScheme = AuthenticationScheme.KERBEROS;     
                    } else {
                        instance.printUsage();
                        System.out.println("Illegal authentication type specified - \"" + args[i] + "\", expected one of basic, kerberos");
                    }  
                }
                else if (args[i].equals("-d")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.sslConnectionDowngradeTo = args[i];      
                }
                else if (args[i].equals("-compression")) {
                	instance.compression = true;
                }
                else {
                    instance.printUsage();
                    System.out.println("Illegal argument specified - " + args[i]);
                    return;
                }
            }
            
            if (instance.jndiProviderURL == null) {
                instance.printUsage();
                System.out.println("Please specify \"-url\" parameter");
                return;
            } else {
                if (!instance.jndiProviderURL.toLowerCase().startsWith("smfs://")) {
                    System.err.println("Please use smfs:// in \"-url\" parameter for a secure session");
                    return;
                }
            }
            if (instance.username == null && instance.authScheme == AuthenticationScheme.BASIC) {
                instance.printUsage();
                System.out.println("Please specify \"-username\" parameter, or -x CLIENT_CERTIFICATE with -ks and -kspwd or -pkpwd parameters to specify a client certificate.");
                return;
            }

            if (instance.authScheme == AuthenticationScheme.CLIENT_CERTIFICATE && (instance.keyStore == null || (instance.keyStorePwd == null && instance.privateKeyPwd == null))) {
                instance.printUsage();
                System.out.println("Please specify KEY_STORE (-ks) and KEY_STORE_PASSWORD (-kspwd) or PRIVATE_KEY_PASSWORD (-pkpwd) when using CLIENT_CERTIFICATE authentication scheme.");
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
