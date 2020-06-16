################################################################
# Readme File for Running Solace Messaging API for 
# JMS Sample Applications
# Copyright 2004-2020 Solace Corporation. All rights reserved.
################################################################


                         TABLE OF CONTENTS
                         
    INTRODUCTION
    SOFTWARE REQUIREMENTS
    HOW TO BUILD SAMPLES
    REQUIRED APPLIANCE CONFIGURATION
    HOW TO RUN SAMPLES
    INTRODUCTORY SAMPLES LIST
    TROUBLESHOOTING
----------------------------------------------------------------




INTRODUCTION:

The included samples provide a basic introduction to using the Solace Messaging API for JMS. Before working with these samples, ensure that you have read and understood the basic concepts found in the Solace Messaging API for JMS User Guide. 



SOFTWARE REQUIREMENTS:

The following third-party software tools are required for building and running JMS samples:
1) Apache Ant version 1.6.5
2) Java SDK 6.0 or above

The following libraries are required to build and run the JMS samples:
    Solace Libraries:
       sol-jms-<version>.jar
    3rd Party Libraries:
        commons-beanutils-1.7.0.jar
        commons-codec-1.6.jar
        commons-lang-2.2.jar
        commons-logging-1.1.1.jar
        concurrent-1.3.4.jar
        dom4j-1.5.2.jar
        forms-1.0.5.jar
        gnujaxp.jar
        fscontext.jar
        gui-commands-1.1.36.jar
        j2ee.jar
        jsch.jar
        jsr173_api.jar
        jug-asl-2.0rc6.jar
        providerutil.jar


HOW TO BUILD THE SAMPLES:

To build the samples, go to the samples directory, and invoke "ant build". Note that this command performs clean before starting the build process.

REQUIRED APPLIANCE CONFIGURATION:

To use persistent messaging, the following requirements must be met:
1) A Solace appliance with an Assured Delivery Blade (ADB) is required.
2) The message spool must be enabled.
3) Durable Topic Endpoints must be created with the same names as the subscription names used.
4) Queues must be created with the same names as the physical names corresponding to the JNDI queues that are looked up.
5) When connecting to a appliance using SolOS-CR, a publisher and subscriber must exist with the same name as the username (Context.SECURITY_PRINCIPAL) used for Initial Context creation (does not apply to SolOS-TR).

HOW TO RUN THE SAMPLES:

A startup script is provided to setup the Java CLASSPATH and start any sample. To run the script, navigate to the bin directory from the shell and issue the following command:

    On Linux:
        run.sh CLASSNAME 

    On Windows:
        run.bat CLASSNAME 
        
    If you are running the samples on Linux/Unix ensure that execute
    permission is enabled for run.sh.
            
    Command line help:
        If you do not pass any cmd line args after the CLASSNAME, you will get the list of options supported for that sample.

    
INTRODUCTORY SAMPLES LIST:

The following is a list of introductory samples included in this package. For more details, go to each corresponding source file in 
samples/src/com/solacesystems/jms/samples:

        SolJMSActiveFlowIndication.java
        SolJMSConsumer.java
        intro/SolJMSHelloWorldPub.java
        intro/SolJMSHelloWorldQueuePub.java
        intro/SolJMSHelloWorldQueueSub.java
        intro/SolJMSHelloWorldSub.java
        SolJMSLDAPBind.java
        SolJMSLDAPLookup.java
        SolJMSProducer.java
        SolJMSProgConsumer.java
        SolJMSSecureSession.java
        SolJMSRRDirectRequester.java
        SolJMSRRDirectReplier.java
        SolJMSRRGuaranteedRequester.java
        SolJMSRRGuaranteedReplier.java
        SolJMSQueueBrowser.java
        Replication.java
        XATransactions


Copyright 2009-2020 Solace Corporation. All rights reserved. 
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to use and copy the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
UNLESS STATED ELSEWHERE BETWEEN YOU AND SOLACE CORPORATION, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
