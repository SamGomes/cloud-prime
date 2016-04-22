#!/bin/bash

# init
function pause(){
   read -p "$*"
}

DIR1="./WebServerCode/instrumented/output/"
DIR2="./WebServerCode/instrumented/instrumentedOutput/"
DIR3="./WebServerCode/nonInstrumented/output/"

printf "This script will remove the .class files and directories added during compiling\n"

pause "Press [Enter] to continue or Ctrl-C to cancel"

printf "Cleaning project directory...\n"

if [ "$(ls $DIR1)" ]; then
	rm ./WebServerCode/instrumented/output/*.class     
else
    echo "$DIR1 is Empty"
fi

if [ "$(ls $DIR2)" ]; then
	rm ./WebServerCode/instrumented/instrumentedOutput/*.class     
else
    echo "$DIR2 is Empty"
fi

if [ "$(ls $DIR3)" ]; then
	rm ./WebServerCode/nonInstrumented/output/*.class    
else
    echo "$DIR3 is Empty"
fi

printf "Cleaning ended\n"
