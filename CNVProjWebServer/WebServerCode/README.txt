------------------------------------------------------------------------------
------------------------------------------------------------------------------
------------------------------- CloudPrime -----------------------------------
---------------------Cloud Computing and Virtualization-----------------------
------------------------------------------------------------------------------
------------------------------------------------------------------------------

INTRODUCTION
------------

The objective of this project is to develop a web server elastic cluster that 
performs CPU-intensive calculations. The proposed solution is implemented 
using Amazon Web Services in order to create a scalable service that has a 
minimal response time when a user queries the server for the factorization 
of a number. 

Made by:
 * Gonçalo Grazina nº 65970 METI - goncalo.n.grazina@tecnico.ulisboa.pt
 * Isabel Costa nº 76394  METI - isabel.costa@tecnico.ulisboa.pt
 * Samuel Gomes nº 76415 MEIC - samuel.gomes@tecnico.ulisboa.pt

INSTALLATION
------------
 
 * The installation of this project requires the BIT tool. The .zip package 
   should be extracted to a directory that contains the BIT package. The
   directory tree should now be presented as follow:

     * BIT
     * InstrumentationTool
     * WebServerCode
     * serverComunicationInterfaces
     * CompileProject.sh
     * startWebServer.sh	
     * startInstrumentedWebServer.sh
     * CleanProject.sh
     * README.txt

 * Prior to compiling the project, you should set the CLASSPATH variable to 
   the directory you chose. It can be set by issuing the following command 
   'export CLASSPATH='$CLASSPATH:/dir/path:/dir/path/InstrumentationTool:./''

 * To compile the project, run either the 'startWebServer' or 
  'startInstrumentedWebServer' scripts. The first one refers to the non
   instrumented code while the seconds one refers to the instrumented version.
   When it ends, you will be prompt to start the web server. If you choose 
   not to, you can start it after by running the scripts again and choosing
   only to launch the server. 

 * If you wish to clean the compiled files run the 'cleanProject' script.

------------
