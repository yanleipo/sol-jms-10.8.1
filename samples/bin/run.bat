@echo off

REM ======================================================================
REM   Run JMS sample applications
REM
REM   Copyright 2004-2020 Solace Corporation. All rights reserved.
REM ======================================================================

echo Copyright 2004-2020 Solace Corporation. All rights reserved.


if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_SOLJMS_HOME=%~dp0..

if "%SOLJMS_HOME%"=="" set SOLJMS_HOME=%DEFAULT_SOLJMS_HOME%
set DEFAULT_SOLJMS_HOME=

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).
set SOLJMS_CMD_LINE_ARGS=%1
if ""%1""=="""" goto errorMsg
set FOUND=1
for %%i in (SolJMSRRDirectReplier SolJMSRRDirectRequester SolJMSRRGuaranteedReplier SolJMSRRGuaranteedRequester SolJMSActiveFlowIndication SolJMSConsumer intro.SolJMSHelloWorldPub intro.SolJMSHelloWorldQueuePub intro.SolJMSHelloWorldQueueSub intro.SolJMSHelloWorldSub SolJMSLDAPBind SolJMSLDAPLookup SolJMSProducer SolJMSProgConsumer SolJMSQueueBrowser Replication SolJMSSecureSession XATransactions) do (
  if ""%%i""==""%1"" set FOUND=0
)

if "%FOUND%"=="1" goto errorMsg

shift
:setupArgs
if ""%1""=="""" goto doneStart
set SOLJMS_CMD_LINE_ARGS=%SOLJMS_CMD_LINE_ARGS% %1
shift
goto setupArgs
rem This label provides a place for the argument list loop to break out 
rem and for NT handling to skip to.

:doneStart
set _JAVACMD=%JAVACMD%
set LOCALCLASSPATH=%SOLJMS_HOME%\classes;%CLASSPATH%
for %%i in ("%SOLJMS_HOME%\..\lib\*.jar") do call "%SOLJMS_HOME%\bin\lcp.bat" %%i

if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=%JAVA_HOME%\bin\java.exe
goto run

:noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=java.exe
echo.
echo Warning: JAVA_HOME environment variable is not set.
echo.

:run

if "%SOLJMS_OPTS%" == "" set SOLJMS_OPTS=-Xmx256M -Xms256M

REM Uncomment to enable remote debugging
REM SET SOLJMS_DEBUG_OPTS=-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005

REM Uncomment to enable logging:
REM set LOCALCLASSPATH=%SOLJMS_HOME%\config;%LOCALCLASSPATH%
REM for %%i in ("%SOLJMS_HOME%\..\lib\optional\*.jar") do call "%SOLJMS_HOME%\bin\lcp.bat" %%i

REM You can specify outgoing interface to use by inserting  -DJMS_Solace_localhost=<interface ip> 
REM after "%_JAVACMD%" in the line below
"%_JAVACMD%" %SOLJMS_DEBUG_OPTS% %SOLJMS_OPTS% -classpath "%LOCALCLASSPATH%" com.solacesystems.jms.samples.%SOLJMS_CMD_LINE_ARGS%
goto end

:errorMsg

echo Expecting one of the following as the first argument:
echo SolJMSActiveFlowIndication
echo SolJMSConsumer
echo intro.SolJMSHelloWorldPub
echo intro.SolJMSHelloWorldQueuePub
echo intro.SolJMSHelloWorldQueueSub
echo intro.SolJMSHelloWorldSub
echo SolJMSLDAPBind
echo SolJMSLDAPLookup
echo SolJMSProducer
echo SolJMSProgConsumer
echo SolJMSRRDirectReplier
echo SolJMSRRDirectRequester
echo SolJMSRRGuaranteedReplier
echo SolJMSRRGuaranteedRequester
echo SolJMSQueueBrowser
echo SolJMSPerfTest
echo Replication
echo SolJMSSecureSession
echo XATransactions
goto end

:end
set LOCALCLASSPATH=
set _JAVACMD=
set SOLJMS_CMD_LINE_ARGS=

if "%OS%"=="Windows_NT" @endlocal

:mainEnd

