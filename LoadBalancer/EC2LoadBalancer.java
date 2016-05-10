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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class EC2LoadBalancer {

    private static ConcurrentHashMap<String,Instance> runningInstances;
    private static int TIME_TO_REFRESH_INSTANCES = 20000;
    private static BigInteger MINIMUM_TIME_TO_WAIT = new BigInteger("10000");
    private static int THREAD_SLEEP_TIME = 20 * 1000; //Time in milliseconds
    private static Timer timer = new Timer();

    private static String LoadBalancerIp;

    private static final String INSTANCE_LOAD_TABLE_NAME = "MSSInstanceLoad";
    private static final String AMI_ID = "ami-83f206e3";
    private static ExecutorService executor;

    private static ConcurrentHashMap<String, IMetric> machineCurrentMetric = new ConcurrentHashMap<>();


    private static int STARTINSTANCES = 1;
    private static int MINMACHINENUM = 1;
    private static int MAXMACHINENUM = 2;

    public static void main(String[] args) throws Exception {



        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        LoadBalancerIp = InetAddress.getLocalHost().getHostAddress();
        server.createContext("/f.html", new MyHandler());
        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor); // creates a default executor
        createInstanceList();
        for (Map.Entry<String, Instance> entry : runningInstances.entrySet()) {
            updateInstanceMetric(entry.getValue(), BigInteger.ZERO, BigInteger.ZERO, true);
        }
        server.start();
        startTimer();
    }

    public static void createInstanceList(){

        try{
            EC2LBGeneralOperations.init();
            DynamoDBGeneralOperations.init();
            EC2LBGeneralOperations.addLoadBalancerToExceptionList(LoadBalancerIp);
            runningInstances = EC2LBGeneralOperations.getRunningInstancesArray();
        }catch(Exception e){

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


            new Thread(new Runnable(){

                //@Override
                public void run() {
                    Headers responseHeaders = exchange.getResponseHeaders();
                    responseHeaders.set("Content-Type", "text/html");
                    HashMap map = queryToMap(exchange.getRequestURI().getQuery());

                    BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());

                    String[] bestMachine = getBestMachineIp(numberToBeFactored);
                    Instance instance = runningInstances.get(bestMachine[0]);
                    BigInteger cost = new BigInteger(bestMachine[1]);
                    BigInteger reqTime = new BigInteger(bestMachine[2]);

                    if (instance == null){
                        System.out.println("Could not find any instance to serve the request");
                    }else{
                        try{
                            System.out.println(instance.getPublicIpAddress());
                            System.out.println("Thread id: "+Thread.currentThread().getId());

                            // update (add) current metric
                            System.out.println("update with cost: "+cost);
                            updateInstanceMetric(instance, cost, reqTime, true); //cannot be numberToBeFactored

                            String url = "http://"+instance.getPublicIpAddress()+":8000/f.html?n="+numberToBeFactored;

                            HttpClient client = HttpClientBuilder.create().build();
                            HttpGet request = new HttpGet(url);

                            HttpResponse response = client.execute(request);
                            System.out.println("Response Code : "
                                    + response.getStatusLine().getStatusCode());

                            BufferedReader rd = new BufferedReader(
                                    new InputStreamReader(response.getEntity().getContent(),StandardCharsets.UTF_8));

                            StringBuilder result = new StringBuilder();
                            String line;
                            while ((line = rd.readLine()) != null) {
                                result.append(line);
                            }

                            // update (subtract) current metric
                            updateInstanceMetric(instance, cost, reqTime, false);

                            exchange.sendResponseHeaders(200, result.length());
                            OutputStream os = exchange.getResponseBody();
                            os.write(result.toString().getBytes());
                            os.close();
                        }catch(IOException e){
                            try {
                                updateInstanceMetric(instance, cost, reqTime, false);
                            }catch(InterruptedException ei){
                                ei.printStackTrace();
                            }
                            e.printStackTrace();
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

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                scaleInstances();
                updateRunningInstances();
            }
        }, TIME_TO_REFRESH_INSTANCES, TIME_TO_REFRESH_INSTANCES);

    }

    public static void updateRunningInstances(){
        runningInstances = null;
        runningInstances = EC2LBGeneralOperations.getRunningInstancesArray();
        EC2LBGeneralOperations.updateRunningInstances();
        System.out.println("Running instances:  "+runningInstances.size());
    }



    /**
     * Gets best machine to process a request according to its current cost and estimated cost
     * for the number to factorize
     *
     * @param numberToFactorize
     * @return instance and estimated cost for the number to be factored
     */
    public static String[] getBestMachineIp(BigInteger numberToFactorize) {


        // [ instanceId, cost, reqTime ]
        String[] results = new String[3];

        //update running runningInstances
        updateRunningInstances();

        // estimated/direct cost for the numberToFactorize
        BigInteger[] estimatedValues = DynamoDBGeneralOperations.estimateCostScan(numberToFactorize);
        BigInteger estimatedTime = estimatedValues[0];
        BigInteger estimatedCost = estimatedValues[1];

        System.out.println("Estimated cost: " + estimatedCost.toString());
        System.out.println("Estimated time: " + estimatedTime.toString());

        BigInteger tempCost = null;
        Instance bestInstance = null;

        //if there arent machines running just start one!
        int instSize = EC2LBGeneralOperations.getActiveInstances();
        if(instSize>0){

            bestInstance = runningInstances.entrySet().iterator().next().getValue();

            // Iterates for every running instance
            // Search for the instance with the lowest number of recalcs
            for (Map.Entry<String, Instance> entry : runningInstances.entrySet()) {

                IMetric metric = machineCurrentMetric.get(entry.getValue().getInstanceId());
                if (metric != null) {
                    System.out.println("machine "+entry.getValue().getInstanceId()+" cost: "+metric.getCost().toString());
                    if (tempCost==null || metric.getCost().compareTo(tempCost) == -1) {
                        tempCost = metric.getCost();
                        bestInstance = entry.getValue();
                    }
                }else{
                    try {
                        updateInstanceMetric(entry.getValue(), BigInteger.ZERO, BigInteger.ZERO, true);
                    }catch (InterruptedException e){}
                }
            }

//            System.out.println("instSize: "+instSize);
//            if(instSize>=MAX_NUM_INSTANCES){
//
                results[0] = bestInstance.getInstanceId();
                results[1] = estimatedCost.toString();
                results[2] = estimatedTime.toString();
                return results;
//            }

            /*
            * se tempCost e instanceId == null begin new instance ???
            *
            * */


//            if (bestInstance != null) {
//
//                String bestInstanceId = bestInstance.getInstanceId();
//                IMetric metric = machineCurrentMetric.get(bestInstanceId);
//
//
//                // the table is populated with at least one request ????
//                if (metric != null) {
//
//                    ConcurrentLinkedQueue<RequestTiming> reqList = metric.getReqList();
//
//                    results[0] = bestInstanceId;
//                    results[1] = estimatedCost.toString();
//                    results[2] = estimatedTime.toString();
//
//                    // If instance has no requests it processes
//                    if (reqList.isEmpty()) {
//                        return results;
//                    }
//
//                    BigInteger timeToFinnish = metric.getTimeToFinnishEveryRequestProcessing();
//
//
//                    if (timeToFinnish.compareTo(MINIMUM_TIME_TO_WAIT)==-1) {
//                        try {
//                            //Thread.sleep(timeToFinnish.longValue());
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//
//                        return results;
//                    }else
//                        return getBestMachineIp(numberToFactorize);
//
//                } else {
//
//                    results[0] = bestInstance.getInstanceId();
//                    results[1] = estimatedCost.toString();
//                    results[2] = estimatedTime.toString();
//                    return results;
//
//                }
//            }
        }


          return null;
    }

    public static void scaleInstances(){

        try {

            if(EC2LBGeneralOperations.getRunningInstances()<STARTINSTANCES){
                System.out.println(" [INFO] Start an instance! ");
                EC2LBGeneralOperations.startInstance(null,null,null,"WebServer",AMI_ID);
            }


            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            String lowConsumptionMachineId = "";

            long offsetInMilliseconds = 1000 * 60 * 2;

            double overallCPUAverage = 0;
            for (Instance instance : runningInstances.values()) {
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
                System.out.println(" CPU utilization for instance " + name + " = " + dpWAverage);


            }

            overallCPUAverage /=EC2LBGeneralOperations.getActiveInstances();

            if (overallCPUAverage > 60 && EC2LBGeneralOperations.getRunningInstances() < MAXMACHINENUM) {
                System.out.println(" [INFO] Start an instance! ");
                EC2LBGeneralOperations.startInstance(null, null, null, "WebServer", AMI_ID);
            }

            if (overallCPUAverage < 30 && EC2LBGeneralOperations.getRunningInstances() > MINMACHINENUM) {
                System.out.println(" [INFO] Fuck an instance! " + lowConsumptionMachineId);
                EC2LBGeneralOperations.terminateInstance(lowConsumptionMachineId, null);
            }

            System.out.println("You have " + overallCPUAverage + " Amazon EC2 average consumption motherfucker! and "+EC2LBGeneralOperations.getRunningInstances()+" instances.");


        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void updateInstanceMetric(Instance newInstance, BigInteger cost, BigInteger requestTime, boolean toAdd) throws InterruptedException{

        String instanceId = newInstance.getInstanceId();
        try {



            String reqTime = requestTime.toString();
            IMetric metricToUpdate;

            if(machineCurrentMetric.containsKey(instanceId)) {

                metricToUpdate = machineCurrentMetric.get(instanceId);
                if(toAdd){
                    //update cost;
                    metricToUpdate.addCost(cost);

                    //update time (adds request time)
                    metricToUpdate.addToReqList(reqTime);

                } else {
                    //update cost;
                    metricToUpdate.subCost(cost);

                    //update time (removes request time)
                    metricToUpdate.subFromReqList(reqTime);
                }


                System.out.println("updating cost: "+metricToUpdate.getCost());

            } else { // adds new instance and metric to Load Balancer list of WebServer current metric
                metricToUpdate = new IMetric();
                metricToUpdate.setCost(cost);
                metricToUpdate.addToReqList(reqTime);

                machineCurrentMetric.put(instanceId, metricToUpdate);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


}

