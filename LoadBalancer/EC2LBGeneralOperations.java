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
import java.util.*;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

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
    private static ArrayList<Instance> instances;
    private static HashMap<String,Instance> runningInstancesArray;
    private static ArrayList<String> LoadBalancerExpectionList;
    private static Timer timer = new Timer();
    private static DescribeInstancesResult describeInstancesRequest;
    private static List<Reservation> reservations;
    private static int TIME_TO_REFRESH_INSTANCES = 5000;
      
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
        runningInstancesArray = new HashMap<>();
        LoadBalancerExpectionList = new ArrayList<String>();

        updateRunningInstances(); 
        startTimer(); // Starts timer for refreshing instances
    }

    static String startInstance( DescribeInstancesResult describeInstancesRequest,List<Reservation> reservations,ArrayList<Instance> instances,String role,String ami) throws Exception {
        
        
        System.out.println("runningInstances: "+runningInstances+".");
        
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
         String newInstanceId = runInstancesResult.getReservation().getInstances()
                                   .get(0).getInstanceId();
         
         runningInstances++;
         return newInstanceId;
    }
    
    static void terminateInstance(String instanceId, ArrayList instances) throws Exception {
        
        System.out.println("Terminating instance.");
        
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
                System.out.println(""+runningInstancesArray.size());
            }
        }, TIME_TO_REFRESH_INSTANCES, TIME_TO_REFRESH_INSTANCES);
    }

    static int getRunningInstances(){
         return runningInstances;
     }

    public static void updateRunningInstances(){
        describeInstancesRequest = ec2.describeInstances();
        reservations = describeInstancesRequest.getReservations();

        instances.clear();
        runningInstancesArray.clear();
        runningInstances = 0;
        
        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }
        
        for(Instance instance :instances){
            if (!LoadBalancerExpectionList.contains(instance.getPublicIpAddress())){
                String state = instance.getState().getName();

                if (state.equals("running")){
                    runningInstances++;
                    //runningInstancesArray.add(instance);
                    runningInstancesArray.put(instance.getInstanceId(),instance);
                }
            }else{
                System.out.println("Found Load Balancer " + instance.getPrivateIpAddress());
            }
        }
    }
    public static HashMap<String,Instance> getRunningInstancesArray(){
        return runningInstancesArray;
     }
    public static void addLoadBalancerToExceptionList(String ip){
        System.out.println("Public ip" + ip);
        LoadBalancerExpectionList.add(ip);
        if (LoadBalancerExpectionList.contains(ip)){
            System.out.println("Load balancer ip added: + "+ ip);
        }else{
            System.out.println("Load balancer ip failed: + "+ ip);
        }
    }
}
