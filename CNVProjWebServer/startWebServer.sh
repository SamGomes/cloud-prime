#!/bin/bash

# init
function pause(){
   read -p "$*"
}

isCompiled=false

# this function is called when Ctrl-C is sent
function trap_ctrlc ()
{
    # perform cleanup here
    echo "  trapped...  WebServer stopped at user command!"

    # exit shell script with error code 2
    # if omitted, shell script will continue execution
    exit 2
}

echo "Do you wish to compile the web server source code? [Y/n]"
read input
if [[ $input == "Y" || $input == "y" ]]; then
        printf "This compiles the non instrumented Web Server code\n"

		pause "Press [Enter] to continue or Ctrl-C to cancel"

		printf "Compiling...\n"

		javac  ./WebServerCode/nonInstrumented/*.java -d ./WebServerCode/nonInstrumented/output/

		printf "Finished compiling project\n"

		isCompiled=true 
else    
		printf "The source code was not compiled\n"
		# do nothing
fi

if $isCompiled ; then
	continue 
else
	printf "Before proceding ensure you compiled the source code at least once.\nOtherwise the Web Server WILL NOT start.\n"
fi

pause "Press [Enter] to start the Web Server or Ctrl-C to cancel"

printf "Starting WebServer...\n"

printf "WebServer started\n"

trap "trap_ctrlc" 2

java -cp WebServerCode/nonInstrumented/output WebServer
