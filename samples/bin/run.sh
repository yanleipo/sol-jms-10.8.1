#!/bin/sh
#######################################################################
#   Run Solace JMS sample applications 
#
#   Copyright 2004-2020 Solace Corporation. All rights reserved.
#######################################################################

echo ""
echo "Copyright 2004-2020 Solace Corporation. All rights reserved."
echo ""

#classpath function
lcp() {
  # if the directory is empty, then it will return the input string
  if [ -f "$1" ] ; then
    if [ -z "$LOCALCLASSPATH" ] ; then
      LOCALCLASSPATH="$1"
    else
      LOCALCLASSPATH="$1":"$LOCALCLASSPATH"
    fi
  fi
}

# First check the arguments
sampleList=" SolJMSActiveFlowIndication
 SolJMSConsumer
 intro.SolJMSHelloWorldPub
 intro.SolJMSHelloWorldQueuePub
 intro.SolJMSHelloWorldQueueSub
 intro.SolJMSHelloWorldSub
 SolJMSLDAPBind
 SolJMSLDAPLookup
 SolJMSProducer
 SolJMSProgConsumer
 SolJMSRRDirectReplier
 SolJMSRRDirectRequester
 SolJMSRRGuaranteedReplier
 SolJMSRRGuaranteedRequester
 SolJMSQueueBrowser
 Replication
 SolJMSSecureSession
 XATransactions"
 
# check if user gave no arguments
if [ $# -lt 1 ] ; then
 echo "Expecting one of the following as the first argument:"
 echo "$sampleList"
exit
fi
#
found=0
for i in $sampleList
do
  if [ $1 = "$i" ] ; then
    found=1
    break
  fi
done
if [ $found -eq 0 ] ; then 
 echo "Expecting one of the following as the first argument:"
 echo "$sampleList"
 exit
fi


if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    JAVACMD=`which java 2> /dev/null `
    if [ -z "$JAVACMD" ] ; then
        JAVACMD=java
    fi
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  echo "  We cannot execute $JAVACMD"
  exit 1
fi

if [ -n "$CLASSPATH" ] ; then
  LOCALCLASSPATH="$CLASSPATH"
fi

# add in the required dependency .jar files
for i in ../../lib/*.jar
do
  lcp $i
done

# Uncomment to enable logging
#for i in ../../lib/optional/*.jar
#do
#  lcp $i
#done
#LOCALCLASSPATH=../config:"$LOCALCLASSPATH"

LOCALCLASSPATH=../classes:"$LOCALCLASSPATH"

# You can specify outgoing interface to use by inserting -DJMS_Solace_localhost=<interface ip> 
# after "$JAVACMD" in the line below 
exec "$JAVACMD" -Xmx256M -Xms256M -classpath "$LOCALCLASSPATH" com.solacesystems.jms.samples."$@"

