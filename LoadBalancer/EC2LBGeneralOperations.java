/* 2016-04 Extended by Samuel Gomes */
/*
 * Copyright 2010-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EC2LBGeneralOperations {

    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */

    static AmazonEC2 ec2;
    static AmazonCloudWatchClient cloudWatch; 

    
    private static int runningInstances = 0;
    private static int activeInstances = 0;
    private static ArrayList<Instance> instances;

    // instances states - true if is healthy : false if cant receive requests
    private static ConcurrentHashMap<Instance,String> instancesState; //to check if its is running right

    // Healthy instances that are available to receive requests
    private static ConcurrentHashMap<String,Instance> runningInstancesArray;

    // Every instance that belongs to the loadBalancer (non-terminated ones)
    private static ConcurrentHashMap<String,Instance> activeInstancesArray;

    private static ArrayList<String> LoadBalancerExpectionList;
    private static Timer timer = new Timer();
    private static DescribeInstancesResult describeInstancesRequest;
    private static List<Reservation> reservations;
    private static int TIME_TO_REFRESH_INSTANCES = 5000;
    private static int HEALTH_CHECK_PERIOD = 2000;
      
    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     * 
     */
     

     static void init() throws Exception {

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = new AmazonEC2Client(credentials);
        cloudWatch= new AmazonCloudWatchClient(credentials);
        

        ec2.setEndpoint("ec2.us-west-2.amazonaws.com");
        cloudWatch.setEndpoint("monitoring.us-west-2.amazonaws.com"); 

        instances = new ArrayList<>();

        instancesState = new ConcurrentHashMap<>();

        runningInstancesArray = new ConcurrentHashMap<>();
        activeInstancesArray = new ConcurrentHashMap<>();

        LoadBalancerExpectionList = new ArrayList<>();



        updateRunningInstances();
        startTimer(); // Starts timer for refreshing instances
    }

    static Instance startInstance(String ami) throws Exception {

        System.out.println("Starting a new instance.");

        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(ami)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName("CNV-lab-AWS")
                .withSecurityGroups("CNV-ssh+http")
                .withMonitoring(true);

        RunInstancesResult runInstancesResult =
                ec2.runInstances(runInstancesRequest);
        Instance newInstanceId = runInstancesResult.getReservation().getInstances()
                .get(0);

        return newInstanceId;
    }
    
    static void terminateInstance(String instanceId) throws Exception {
        System.out.println("Terminating instance.");
        
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
    }


    public static void startTimer(){

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateRunningInstances();
                tryInstances();
                System.out.println("activeIntances: "+activeInstances);
                System.out.println("runningIntances: "+runningInstances);
            }
        }, TIME_TO_REFRESH_INSTANCES, TIME_TO_REFRESH_INSTANCES);

    }



    public synchronized static void tryInstances(){

        instancesState.clear();

        for(Instance instance :instances) {

            final HttpParams httpParams = new BasicHttpParams();
            HttpConnectionParams.setSoTimeout(httpParams,HEALTH_CHECK_PERIOD);
            HttpClient client = new DefaultHttpClient(httpParams);
            String url = "http://" + instance.getPublicIpAddress() + ":8000/f.html?n=2";
            HttpGet request = new HttpGet(url);

            HttpResponse response;
            int statusCode = 404;

            // While the response code is not 200 keep sending requests
            // This will ensure the web server is already running when we forward the request
            try {
                response = client.execute(request);
                statusCode = response.getStatusLine().getStatusCode();
                System.out.println("Responsed: " + statusCode);
            } catch (IOException e) {
                System.out.println("New instance unreachable");
            }
            if(statusCode!=200){
                instancesState.put(instance,"false");
            }else {
                instancesState.put(instance, "true");
            }
        }

    }



    public synchronized static void updateRunningInstances(){
        describeInstancesRequest = ec2.describeInstances();
        reservations = describeInstancesRequest.getReservations();

        instances.clear();
        runningInstancesArray.clear();
        runningInstances = 0;
        activeInstancesArray.clear();
        activeInstances = 0;

        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }

        for(Instance instance :instances){
            if (!LoadBalancerExpectionList.contains(instance.getPublicIpAddress())){
                String state = instance.getState().getName();

                if (state.equals("running")){
                    if(instancesState.get(instance)=="true") {
                        runningInstances++;
                        runningInstancesArray.put(instance.getInstanceId(), instance);
                    }
                }
                if (state.equals("running")){
                    activeInstances++;
                    activeInstancesArray.put(instance.getInstanceId(),instance);
                }
            }else{
                System.out.println("Found Load Balancer " + instance.getPrivateIpAddress());
            }
        }

    }

    public static ConcurrentHashMap<String,Instance> getRunningInstancesArray(){
        return runningInstancesArray;
     }

    public static ConcurrentHashMap<String,Instance> getActiveInstancesArray(){
        return activeInstancesArray;
    }

    public static void addLoadBalancerToExceptionList(String ip){
        LoadBalancerExpectionList.add(ip);
    }

    static synchronized int getActiveInstances(){
        return activeInstances;
    }
    static synchronized int getRunningInstances(){
        return runningInstances;
    }

}
