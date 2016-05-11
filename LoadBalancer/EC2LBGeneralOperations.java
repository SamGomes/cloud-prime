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
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    static AmazonEC2      ec2;
    static AmazonCloudWatchClient cloudWatch; 

    
    private static int runningInstances = 0;
    private static int activeInstances = 0;
    private static int MIN_INSTANCES = 1;
    private static int MAX_INSTANCES = 5;
    private static int MAX_START_NEW_INSTANCES = 1;
    private static int NUMBER_INSTANCES_CPU_ABOVE_60 = 0;
    private static ArrayList<Instance> instances;
    private static ConcurrentHashMap<String,Instance> runningInstancesArray;
    private static ArrayList<String> instancesBeingStartedArray = new ArrayList<>();
    private static ArrayList<String> LoadBalancerExpectionList;
    private static Timer timer = new Timer();
    private static DescribeInstancesResult describeInstancesRequest;
    private static List<Reservation> reservations;
    private static int TIME_TO_REFRESH_INSTANCES = 10000;
    private static int MIN_TO_CHECK_IF_IDLE = 2;
    private static boolean isLaunchingInstance = false;
    private static final String AMI_ID = "ami-7f74891f";
      
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

        instances = new ArrayList<Instance>();
        runningInstancesArray = new ConcurrentHashMap<>();
        LoadBalancerExpectionList = new ArrayList<String>();

        updateRunningInstances(); 
        startTimer(); // Starts timer for refreshing instances
    }

    static Instance startInstance( DescribeInstancesResult describeInstancesRequest,List<Reservation> reservations,ArrayList<Instance> instances,String role,String ami) throws Exception {

        System.out.println("runningInstances: " + runningInstances + ".");
        System.out.println("Starting a new instance.");

        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(ami)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName("CNV-Final")
                .withSecurityGroups("CNV-ssh+http")
                .withMonitoring(true);

        RunInstancesResult runInstancesResult =
                ec2.runInstances(runInstancesRequest);
        Instance newInstanceId = runInstancesResult.getReservation().getInstances()
                .get(0);

        tryNewInstance(newInstanceId.getPublicIpAddress());

        return newInstanceId;
    }
    
    static void terminateInstance(String instanceId, ArrayList instances) throws Exception {
         TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
         termInstanceReq.withInstanceIds(instanceId);
         ec2.terminateInstances(termInstanceReq);
         runningInstances--;
    }

    public static ArrayList<Instance> getInstances(){
        return instances;
    }

    public static void startTimer(){

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateRunningInstances();
            }
        }, TIME_TO_REFRESH_INSTANCES, TIME_TO_REFRESH_INSTANCES);
    }

    static int getRunningInstances(){
         return runningInstances;
     }
    static int getActiveInstances(){
        return activeInstances;
    }

    public static synchronized void updateRunningInstances(){
        describeInstancesRequest = ec2.describeInstances();
        reservations = describeInstancesRequest.getReservations();
        instances.clear();
        runningInstances = 0;
        activeInstances = 0;
        long currentDate = TimeUnit.MILLISECONDS.toMinutes(new Date().getTime());

        System.out.println("Starting instances: "+ instancesBeingStartedArray.size());

        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }

        if (instances.size() == 0){
            try {
                isLaunchingInstance = true;
                startInstance(null,null,null,null,AMI_ID);
                isLaunchingInstance = false;
            } catch (Exception e) {
                System.out.println("Unable to start instance");
            }
        }
        //Build the running instances array
        for(Instance instance :instances){
            if (!LoadBalancerExpectionList.contains(instance.getPublicIpAddress())){
                String state = instance.getState().getName();
                if(!instancesBeingStartedArray.contains(instance.getInstanceId())){
                    if (state.equals("running") && tryNewInstance(instance.getPublicIpAddress())){
                        runningInstances++;
                        runningInstancesArray.put(instance.getInstanceId(), instance);
                    }
                    if (!state.equals("terminated")){
                        activeInstances++;
                    }
                }else{
                    if (tryNewInstance(instance.getPublicIpAddress())){
                        isLaunchingInstance = false;
                        instancesBeingStartedArray.remove(instance.getInstanceId());
                        runningInstances++;
                        runningInstancesArray.put(instance.getInstanceId(),instance);
                    }
                }
            }else{
                System.out.println("Found Load Balancer " + instance.getPrivateIpAddress());
            }
        }
        // Check if any instance can be terminated or if the load of the instances is high
        for (String instance: runningInstancesArray.keySet()){
            long instanceTime = TimeUnit.MILLISECONDS.toMinutes(
                    runningInstancesArray.get(instance).getLaunchTime().getTime());
            if (currentDate-instanceTime > MIN_TO_CHECK_IF_IDLE){
                if (getInstanceCPU(instance) < 30 && runningInstancesArray.size() > MIN_INSTANCES){
                    try {
                        terminateInstance(instance,null);
                        runningInstancesArray.remove(instance);
                    } catch (Exception e) {
                        System.out.println("Unable to stop instance");
                        e.printStackTrace();
                    }
                }
            }
            if (getInstanceCPU(instance) > 60
                    && runningInstancesArray.size() < MAX_INSTANCES
                    && !isLaunchingInstance){
                NUMBER_INSTANCES_CPU_ABOVE_60++;
            }
        }
        // If all instances have their CPU load above 60%, launch a new one
        if (instancesBeingStartedArray.size() < MAX_START_NEW_INSTANCES){
            if (NUMBER_INSTANCES_CPU_ABOVE_60 == runningInstancesArray.size() && !isLaunchingInstance){
                try {
                    isLaunchingInstance = true;
                    NUMBER_INSTANCES_CPU_ABOVE_60 = 0;
                    Instance newInstance = startInstance(null,null,null,null,AMI_ID);
                    instancesBeingStartedArray.add(newInstance.getInstanceId());
                } catch (Exception e) {
                    System.out.println("Unable to start instance");
                }
            }
        }
    }
    public static ConcurrentHashMap<String,Instance> getRunningInstancesArray(){
        return runningInstancesArray;
     }
    public static void addLoadBalancerToExceptionList(String ip){
        LoadBalancerExpectionList.add(ip);
    }

    public static Instance getInstance(String id){
        return runningInstancesArray.get(id);

    }

    public static String getInstanceStatus(String instanceId) {
        DescribeInstancesRequest describeInstanceRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult describeInstanceResult = ec2.describeInstances(describeInstanceRequest);
        return describeInstanceResult.getReservations().get(0).getInstances().get(0).getState().getName();
    }

    public static Instance getInstanceById(String instanceId){
        DescribeInstancesRequest describeInstanceRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult describeInstanceResult = ec2.describeInstances(describeInstanceRequest);
        return describeInstanceResult.getReservations().get(0).getInstances().get(0);
    }

    public static boolean tryNewInstance(String instancePublicAddress){

        HttpClient client = HttpClientBuilder.create().build();
        String url = "http://"+instancePublicAddress+":8000/f.html?n=2";
        HttpGet request = new HttpGet(url);
        HttpResponse response;
        int statusCode = 404;

        // While the response code is not 200 keep sending requests
        // This will ensure the web server is already running when we forward the request
        //while(statusCode != 200){
            try {
                response = client.execute(request);
                statusCode = response.getStatusLine().getStatusCode();
            } catch (IOException e) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                    System.out.println("Instance still unreachable");
                }
            }
//        }
        if (statusCode != 200){
            return false;
        }
        return true;
    }

    static float getInstanceCPU(String instanceId){
        double dpWAverage=0;
        float overallCPUAverage=0;
        long offsetInMilliseconds = 1000 * 60 * 2;
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");
        String id = instanceId;

        instanceDimension.setValue(id);
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                .withNamespace("AWS/EC2")
                .withPeriod(60)
                .withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withDimensions(instanceDimension)
                .withEndTime(new Date());
        GetMetricStatisticsResult getMetricStatisticsResult =
                EC2LBGeneralOperations.cloudWatch.getMetricStatistics(request);
        List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();

        int datapointCount=0;
        for (Datapoint dp : datapoints) {
            datapointCount++;
            dpWAverage += 1/datapointCount * dp.getAverage();
        }
        overallCPUAverage += dpWAverage;

        return overallCPUAverage;
    }
}
