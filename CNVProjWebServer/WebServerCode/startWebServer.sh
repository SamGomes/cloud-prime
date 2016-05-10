#!/bin/bash

#

export CLASSPATH=./WebServerCode/instrumented/instrumentedOutput:./awsJavaSDK/lib/aws-java-sdk-1.10.69.jar:./awsJavaSDK/third-party/lib/*:.


javac *.java

javac  -source 1.4 ./WebServerCode/instrumented/*.java -d ./WebServerCode/instrumented/output/

javac  -source 1.4 ./InstrumentationTool/FactInstr.java -d  ./WebServerCode/instrumented/instrumentedOutput/
#javac -source 1.4 ./WebServerCode/instrumented/WebServer.java ./WebServerCode/instrumented/FactorizeMain.java ./WebServerCode/instrumented/Factorize.java -d ./WebServerCode/instrumented/output/


cd WebServerCode/instrumented/instrumentedOutput/

java -cp ../../../:. FactInstr ../output/ ../instrumentedOutput 

cd ../../../

java WebServer

