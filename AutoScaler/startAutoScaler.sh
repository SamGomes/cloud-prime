#!/bin/bash

#

export CLASSPATH=./WebServerCode/instrumented/instrumentedOutput:./awsJavaSDK/lib/aws-java-sdk-1.10.69.jar:./awsJavaSDK/third-party/lib/*:.



javac  *.java

java EC2CustomAutoScaling

