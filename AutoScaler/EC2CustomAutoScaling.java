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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.ArrayList;

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

public class EC2CustomAutoScaling {

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

    
    
    private static int STARTINSTANCES = 1;
    private static int MINMACHINENUM = 1;
    private static int MAXMACHINENUM = 2;
    
    
    


    public static void main(String[] args) throws Exception {

    	EC2GeneralOperations ecgo = new EC2GeneralOperations();
    	
        System.out.println("===========================================");
        System.out.println("Welcome to the AWS Java SDK!");
        System.out.println("===========================================");

        ecgo.init();

        
        //-------------------------STARTUP PROCEDURE----------------------------------
        

        DescribeInstancesResult describeInstancesRequest = ecgo.ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        ArrayList<Instance> instances = new ArrayList<Instance>();
        
        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }

        for (Instance instance : instances) {
        	String state = instance.getState().getName();
            
        	
        }
        System.out.println(ecgo.getRunningInstances());
        
        while(ecgo.getRunningInstances()<STARTINSTANCES && MAXMACHINENUM>ecgo.getRunningInstances()){
        	ecgo.startInstance(describeInstancesRequest,reservations,instances,"WebServer","ami-83f206e3");
        }
        
        while(true){
            try {

            	
            	DescribeAvailabilityZonesResult availabilityZonesResult = ecgo.ec2.describeAvailabilityZones();
                //System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() + " Availability Zones.");


                describeInstancesRequest = ecgo.ec2.describeInstances();
                reservations = describeInstancesRequest.getReservations();
                instances = new ArrayList<Instance>();
                

                float overallCPUAverage=0;

                for (Reservation reservation : reservations) {
                    instances.addAll(reservation.getInstances());
                }


                long offsetInMilliseconds = 1000 * 60 * 2;
                Dimension instanceDimension = new Dimension();
                instanceDimension.setName("InstanceId");

        
                
                String lowConsumptionMachineId="";
                
                

                for (Instance instance : instances) {
                	double dpWAverage=0;
                    String name = instance.getInstanceId();
                    String state = instance.getState().getName();
                    //System.out.println("Instance State : " + state +".");
                    
                    if (state.equals("running")) { 
                        //System.out.println("running instance id = " + name);
                        instanceDimension.setValue(name);
                        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                            .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                            .withNamespace("AWS/EC2")
                            .withPeriod(60)
                            .withMetricName("CPUUtilization")
                            .withStatistics("Average")
                            .withDimensions(instanceDimension)
                            .withEndTime(new Date());
                         GetMetricStatisticsResult getMetricStatisticsResult = 
                            ecgo.cloudWatch.getMetricStatistics(request);
                         List<Datapoint>  datapoints = getMetricStatisticsResult.getDatapoints();
                        
                         int datapointCount=0;
                        for (Datapoint dp : datapoints) {

                        	
                            datapointCount++;           
                            dpWAverage += 1/datapointCount * dp.getAverage();
                         
                       }
                        
                      
      				   System.out.println(" CPU utilization for instance " + name + " = " + dpWAverage);
                        
      		          overallCPUAverage += dpWAverage;
                     }
                     else {
                        //System.out.println("instance id = " + name);
                     }
                    
                    if(dpWAverage<10 && state.equals("running")){
                    	lowConsumptionMachineId=name;
                    }
                    
                    
                }
                
                overallCPUAverage /= ecgo.getRunningInstances();

                if(overallCPUAverage>60 && ecgo.getRunningInstances()<MAXMACHINENUM){
                    System.out.println(" [INFO] Start an instance! ");
                    ecgo.startInstance(describeInstancesRequest,reservations,instances,"WebServer","ami-83f206e3");
                }
                
                if(overallCPUAverage<30 && ecgo.getRunningInstances()>MINMACHINENUM){
                    System.out.println(" [INFO] Fuck an instance! "+lowConsumptionMachineId);
                    ecgo.terminateInstance(lowConsumptionMachineId,instances);
                }
                

                //System.out.println("You have " + overallCPUAverage + " Amazon EC2 average consumption motherfucker! and "+ecgo.getRunningInstances()+" instances.");
                
                
                    
                Thread.sleep(60000);


                
            } catch (AmazonServiceException ase) {
                    System.out.println("Caught Exception: " + ase.getMessage());
                    System.out.println("Reponse Status Code: " + ase.getStatusCode());
                    System.out.println("Error Code: " + ase.getErrorCode());
                    System.out.println("Request ID: " + ase.getRequestId());
            }
        }
    }
}
