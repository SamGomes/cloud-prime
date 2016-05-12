import com.amazonaws.annotation.ThreadSafe;
import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;


import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class EC2LoadBalancer {

    //--------LOAD BALANCING MODULE CONTROL VARIABLES-----------------------------------
//
//    private static ConcurrentHashMap<String,Instance> runningInstances;
//    private static ConcurrentHashMap<String,Instance> activeInstances;

    private static int TIME_TO_REFRESH_INSTANCES = 3000;

    private static int TIME_TO_SCALE_INSTANCES = 2000;
    private static Timer scaleTimer = new Timer();

    private static String LoadBalancerIp;

    private static final String AMI_ID = "ami-7a38c51a";

    private static ConcurrentHashMap<String, IMetric> machineCurrentMetric = new ConcurrentHashMap<>();


    //--------AUTO SCALLING MODULE CONTROL VARIABLES-----------------------------------

    private static int STARTINSTANCES = 1;
    private static int MINMACHINENUM = 1;
    private static int MAXMACHINENUM = 3;

    private static int MINIMUM_CPU_UTIL=40;
    private static int MAXIMUM_CPU_UTIL=60;



    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        LoadBalancerIp = InetAddress.getLocalHost().getHostAddress();
        server.createContext("/f.html", new MyHandler());
        server.setExecutor(new ThreadPoolExecutor(Integer.MAX_VALUE, Integer.MAX_VALUE, 1200, TimeUnit.SECONDS, new ArrayBlockingQueue(100))); // creates a default executor
        createInstanceList();
        for (Map.Entry<String, Instance> entry : EC2LBGeneralOperations.getRunningInstancesArray().entrySet()) {
            updateInstanceMetric(entry.getValue(), BigInteger.ZERO, true);
        }
        server.start();

       startTimer();


    }

    public static void createInstanceList(){

        try{
            EC2LBGeneralOperations.init();
            DynamoDBGeneralOperations.init();
            EC2LBGeneralOperations.addLoadBalancerToExceptionList(LoadBalancerIp);
//            runningInstances = EC2LBGeneralOperations.getRunningInstancesArray();
//            activeInstances = EC2LBGeneralOperations.getActiveInstancesArray();
//            System.out.println(activeInstances);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static HashMap queryToMap(String query){
        HashMap result = new HashMap();
        String[] params = query.split("&");
        for (int i=0; i< params.length;i++) {
            String pair[] = params[i].split("=");
            if (pair.length>1) {
                result.put(pair[0], pair[1]);
            }else{
                result.put(pair[0], "");
            }
        }
        return result;
    }

    static class MyHandler implements HttpHandler {
        public void handle(final HttpExchange exchange) throws IOException {

            new Thread(new Runnable() {
                @Override
                public void run() {

                    Headers responseHeaders = exchange.getResponseHeaders();
                    responseHeaders.set("Content-Type", "text/html");
                    HashMap map = queryToMap(exchange.getRequestURI().getQuery());

                    BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());

                    String[] bestMachine = getBestMachineIp(numberToBeFactored);
                    Instance instance = EC2LBGeneralOperations.getRunningInstancesArray().get(bestMachine[0]);
                    BigInteger cost = new BigInteger(bestMachine[1]);

                    if (instance == null){
                        System.out.println("Could not find any instance to serve the request! Something went wrong!");
                    }else{
                        try{
                            System.out.println(instance.getPublicIpAddress());
                            System.out.println("Thread id: "+Thread.currentThread().getId());

                            // update (add) current metric
                            System.out.println("update with cost: "+cost);
                            updateInstanceMetric(instance, cost, true); //cannot be numberToBeFactored

                            String url = "http://"+instance.getPublicIpAddress()+":8000/f.html?n="+numberToBeFactored;

                            HttpClient client = HttpClientBuilder.create().build();
                            HttpGet request = new HttpGet(url);

                            HttpResponse response = client.execute(request);


                            BufferedReader rd = new BufferedReader(
                                    new InputStreamReader(response.getEntity().getContent(),StandardCharsets.UTF_8));

                            StringBuilder result = new StringBuilder();
                            String line;
                            while ((line = rd.readLine()) != null) {
                                result.append(line);
                            }

                            // update (subtract) current metric
                            updateInstanceMetric(instance, cost, false);
                            try{
                                exchange.sendResponseHeaders(200, result.length());
                                OutputStream os = exchange.getResponseBody();
                                os.write(result.toString().getBytes());
                                os.close();
                            }catch(IOException e){
                                e.printStackTrace();
                            }
                        }catch(Exception e2){
                            e2.printStackTrace();
                        }
                    }
                }
            }).start();
        }


    }

    public static void startTimer(){

        // scheduling the task at interval


        scaleTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                scaleInstances();
            }
        }, TIME_TO_SCALE_INSTANCES, TIME_TO_SCALE_INSTANCES);

    }

    
    /**
     * Gets best machine to process a request according to its current cost and estimated cost
     * for the number to factorize
     *
     * @param numberToFactorize
     * @return instance and estimated cost for the number to be factored
     */
    public synchronized static String[] getBestMachineIp(BigInteger numberToFactorize) {


        // [ instanceId, cost ]
        String[] results = new String[2];

        // update running runningInstances
        EC2LBGeneralOperations.updateRunningInstances();

        // estimated/direct cost for the numberToFactorize
        BigInteger estimatedCost = DynamoDBGeneralOperations.estimateCostScan(numberToFactorize);

        System.out.println("Estimated cost: " + estimatedCost.toString());

        BigInteger tempCost = null;
        Instance bestInstance = null;

        // if there arent machines running!
        int instSize = EC2LBGeneralOperations.getRunningInstances();
        System.out.println("instSize: "+instSize);
        if(instSize>0){

            bestInstance = EC2LBGeneralOperations.getRunningInstancesArray().entrySet().iterator().next().getValue();

            // Iterates for every running instance
            // Search for the instance with the lowest number of recalcs
            for (Map.Entry<String, Instance> entry : EC2LBGeneralOperations.getRunningInstancesArray().entrySet()) {

                IMetric metric = machineCurrentMetric.get(entry.getValue().getInstanceId());
                if (metric != null) {
                    System.out.println("machine "+entry.getValue().getInstanceId()+" cost: "+metric.getCost().toString());
                    if (tempCost==null || metric.getCost().compareTo(tempCost) == -1) {
                        tempCost = metric.getCost();
                        bestInstance = entry.getValue();
                    }
                }else{
                    try {
                        updateInstanceMetric(entry.getValue(), BigInteger.ZERO, true);
                    }catch (InterruptedException e){}
                }
            }


            results[0] = bestInstance.getInstanceId();
            results[1] = estimatedCost.toString();
            return results;

        }


        return null;
    }

    /**
     * Autoscalling modules dominant method
     *
     * It is based on the average of the latest cpu measures
     * from cloutprime
     *
     */
    public static void scaleInstances(){

        try {

            if(EC2LBGeneralOperations.getActiveInstances()<STARTINSTANCES){
                System.out.println(" [INFO] Start an instance! ");
                EC2LBGeneralOperations.startInstance(AMI_ID);
            }


            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            String lowConsumptionMachineId = "";

            long offsetInMilliseconds = 1000 * 60 * 2;

            double overallCPUAverage = 0;
            for (Instance instance : EC2LBGeneralOperations.getActiveInstancesArray().values()) {
                double dpWAverage = 0;
                String name = instance.getInstanceId();

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
                        EC2LBGeneralOperations.cloudWatch.getMetricStatistics(request);
                List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                int datapointCount = 0;
                for (Datapoint dp : datapoints) {
                    datapointCount++;
                    dpWAverage += 1/datapointCount * dp.getAverage();
                }

                if (dpWAverage < 10) {
                    lowConsumptionMachineId = name;
                }
                overallCPUAverage+=dpWAverage;
                //System.out.println(" CPU utilization for instance " + name + " = " + dpWAverage);


            }

            overallCPUAverage /=EC2LBGeneralOperations.getRunningInstances();

            System.out.println("sizzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz: "+EC2LBGeneralOperations.getActiveInstancesArray().size());
            System.out.println("overall: "+overallCPUAverage+" ,get: "+EC2LBGeneralOperations.getActiveInstances()+"MAX: "+MAXMACHINENUM);
            if (overallCPUAverage > MAXIMUM_CPU_UTIL && EC2LBGeneralOperations.getActiveInstances() < MAXMACHINENUM) {
                System.out.println(" [INFO] Start an instance! ");
                EC2LBGeneralOperations.startInstance(AMI_ID);
            }

            if (overallCPUAverage < MINIMUM_CPU_UTIL && EC2LBGeneralOperations.getActiveInstances() > MINMACHINENUM) {
                System.out.println(" [INFO] Fuck an instance! " + lowConsumptionMachineId);
                EC2LBGeneralOperations.terminateInstance(lowConsumptionMachineId);
            }

           // System.out.println("You have " + overallCPUAverage + " Amazon EC2 average consumption motherfucker! and "+EC2LBGeneralOperations.getRunningInstances()+" instances.");


        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void updateInstanceMetric(Instance newInstance, BigInteger cost, boolean toAdd) throws InterruptedException{

        String instanceId = newInstance.getInstanceId();
        try {

            IMetric metricToUpdate;

            if(machineCurrentMetric.containsKey(instanceId)) {

                metricToUpdate = machineCurrentMetric.get(instanceId);
                if(toAdd){

                    //update cost;
                    metricToUpdate.addCost(cost);

                } else {

                    //update cost;
                    metricToUpdate.subCost(cost);

                }


                System.out.println("updating cost: "+metricToUpdate.getCost());

            } else { // adds new instance and metric to Load Balancer list of WebServer current metric
                metricToUpdate = new IMetric();
                metricToUpdate.setCost(cost);

                machineCurrentMetric.put(instanceId, metricToUpdate);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


}