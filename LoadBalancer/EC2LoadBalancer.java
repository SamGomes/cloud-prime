import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class EC2LoadBalancer {

    private static ConcurrentHashMap<String,Instance> instances;
    private static int TIME_TO_REFRESH_INSTANCES = 20000;
    private static int MINIMUM_TIME_TO_WAIT = 7000;
    private static int THREAD_SLEEP_TIME = 20 * 1000; //Time in milliseconds
    private static Timer timer = new Timer();
    private static String LoadBalancerIp;

    private static final BigDecimal THRESHOLD = new BigDecimal("20000"); //TODO: set a meaningful value

    private static final String INSTANCE_LOAD_TABLE_NAME = "MSSInstanceLoad";
    private static final String AMI_ID = "ami-83f206e3";
    private static ExecutorService executor;

    private static ArrayList<BigInteger> pendingRequests = new ArrayList<>();

    private static ConcurrentHashMap<String, IMetric> machineCurrentMetric = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        LoadBalancerIp = InetAddress.getLocalHost().getHostAddress();
        server.createContext("/f.html", new MyHandler());
        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor); // creates a default executor
        createInstanceList();
        server.start();
        startTimer();
    }

    public static void createInstanceList(){

        try{
            EC2LBGeneralOperations.init();
            DynamoDBGeneralOperations.init();
            EC2LBGeneralOperations.addLoadBalancerToExceptionList(LoadBalancerIp);
            instances = EC2LBGeneralOperations.getRunningInstancesArray();
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
//                    HashMap<Instance, BigInteger> bestMachine = getBestMachineIp(numberToBeFactored);
                    String[] bestMachine = getBestMachineIp(numberToBeFactored);
                    Instance instance = instances.get(bestMachine[0]);
                    BigInteger cost = new BigInteger(bestMachine[1]);
                    BigInteger reqTime = new BigInteger(bestMachine[2]);

                    if (instance == null){
                        System.out.println("Could not find any instance to serve the request");
                    }else{
                        try{
                            System.out.println(instance.getPublicIpAddress());
                            System.out.println("Thread id: "+Thread.currentThread().getId());

                            // update (add) current metric
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
                        }catch(Exception e){
                            e.printStackTrace();
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

                updateRunningInstances();

                try {
                    deleteUnusedInstances();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }, TIME_TO_REFRESH_INSTANCES, TIME_TO_REFRESH_INSTANCES);
    }

    public static void updateRunningInstances(){
        instances = null;
        instances = EC2LBGeneralOperations.getRunningInstancesArray();
        System.out.println("Running instances:  "+instances.size());
    }

    /**
     * If there's N machines are being unused it terminates N-1 instances
     *
     * @throws Exception
     */
    public static void deleteUnusedInstances() throws Exception{
        Instance instance = null;
        Instance currentInstance;

        // Check if any machine is not being used and terminate if its not needed
        for (Map.Entry<String,Instance> entry: instances.entrySet()){
            currentInstance = entry.getValue();

            IMetric metric = machineCurrentMetric.get(currentInstance.getInstanceId());

            System.out.println("metrics: " + (metric != null ? metric.getReqList().toString() : "null"));
            ConcurrentLinkedQueue<RequestTiming> reqList;
            // the table is populated with at least one request ????
            if(metric != null){
                reqList =  metric.getReqList();
                if (reqList.isEmpty()){
                    if(instance == null) {
                        instance = entry.getValue();
                    } else {
                        System.out.println("Terminating instance " + currentInstance.getInstanceId());
                        EC2LBGeneralOperations.terminateInstance(currentInstance.getInstanceId(), null);
                    }
                }
            }
        }
    }


    /**
     * Gets best machine to process a request according to its current cost and estimated cost
     * for the number to factorize
     *
     * @param numberToFactorize
     * @return instance and estimated cost for the number to be factored
     */
//    public static HashMap<Instance, BigInteger> getBestMachineIp(BigInteger numberToFactorize){
    public static String[] getBestMachineIp(BigInteger numberToFactorize) {
        Instance result = null;
        float currentCPULoad;
        HashMap<Instance, BigInteger> finalResult = new HashMap<>();

        // [ instanceId, cost, reqTime ]
        String[] results = new String[3];

        //update running instances
        updateRunningInstances();

        // estimated/direct cost for the numberToFactorize
        BigInteger[] estimatedValues = DynamoDBGeneralOperations.estimateCostScan(numberToFactorize);
        BigInteger estimatedTime = estimatedValues[0];
        BigInteger estimatedCost = estimatedValues[1];

        System.out.println("Estimated cost: " + estimatedCost.toString());
        System.out.println("Estimated time: " + estimatedTime.toString());

        BigInteger tempCost = estimatedCost;
        Instance bestInstance = null;

        // Iterates for every running instance
        // Search for the instance with the lowest number of recalcs
        for (Map.Entry<String, Instance> entry : instances.entrySet()) {

            IMetric metric = machineCurrentMetric.get(entry.getValue().getInstanceId());

            if (metric != null) {
                if (metric.getCost().compareTo(tempCost) == -1) {
                    tempCost = metric.getCost();
                    bestInstance = entry.getValue();
                }
            } else {
                bestInstance = entry.getValue();
            }
        }

        /*
        * se tempCost e instanceId == null begin new instance ???
        *
        * */

        if (bestInstance != null) {

            System.out.println("->>>> e que é mesmo!!!!!!!!!!!!!");
            String bestInstanceId = bestInstance.getInstanceId();
            IMetric metric = machineCurrentMetric.get(bestInstanceId);

            System.out.println("metrics: " + (metric != null ? metric.getReqList().toString() : "null"));

            // the table is populated with at least one request ????
            if (metric != null) {

                System.out.println("->>>> AS METRICAS ESTAO BEM :)");

                ConcurrentLinkedQueue<RequestTiming> reqList = metric.getReqList();

                results[0] = bestInstanceId;
                results[1] = tempCost.toString();
                results[2] = estimatedTime.toString();

                // If instance has no requests it processes
                if (reqList.isEmpty()) {
                    //                finalResult.put(bestInstance, estimatedTime);
                    System.out.println("->>>> A instance ta vazia :)");
                    return results;
                }

                long timeToFinnish = metric.getTimeToFinnishEveryRequestProcessing();

                // if the machine takes more than 7 seconds to process each value it looks for other machine
                System.out.println("This is the time " + bestInstanceId + " has to process its requests: " + timeToFinnish);

                if (timeToFinnish < MINIMUM_TIME_TO_WAIT) {
                    try {
                        Thread.sleep(timeToFinnish);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //                finalResult.put(bestInstance, estimatedTime);
                    return results;
                }

            } else {
                // We are processing the first request

                System.out.println("->>>> AS METRICAS ESTAO A NULL??? WTF?");

                results[0] = bestInstance.getInstanceId();
                results[1] = tempCost.toString();
                results[2] = estimatedTime.toString();
                return results;

            }
        }

        /*
        *  There are no instances available to process the request.
        *  Another instance is launched and the request is sent
        * */
//        if (bestInstance == null) {
            try {
                // launch new instance
                Instance newInstance = EC2LBGeneralOperations.startInstance(null,null,null,"WebServer", AMI_ID);
                while (!EC2LBGeneralOperations.getInstanceStatus(newInstance.getInstanceId()).equals("running")){
                    TimeUnit.SECONDS.sleep(5);
                }
                System.out.println("returning new instance");
                Instance instance = EC2LBGeneralOperations.getInstanceById(newInstance.getInstanceId());
                tryNewInstance(instance.getPublicIpAddress());

                results[0] = instance.getInstanceId();
                results[1] = tempCost.toString();
                results[2] = estimatedTime.toString();
                return results;

            } catch (Exception e) {
                e.printStackTrace();
            }
//            return null;
//        }
        return null;
    }

    public static void updateInstanceMetric(Instance newInstance, BigInteger cost, BigInteger requestTime, boolean toAdd) throws InterruptedException{

        String instanceId = newInstance.getInstanceId();
        try {

//            ItemCollection<QueryOutcome> reqTimes = (DynamoDBGeneralOperations.queryTable("MSSCentralTable", "numberToBeFactored", num.toString()));
//            String reqTime="NA";
//
//            // Gets direct request time if exists in DynamoDB
//            if(reqTimes.iterator().hasNext()) {
//                reqTime = reqTimes.iterator().next().get("timeToFactorize").toString();
//                System.out.println("reqTime: " + reqTime);
//            }

            String reqTime = requestTime.toString();
            IMetric metricToUpdate;

            if(machineCurrentMetric.containsKey(instanceId)) {

                metricToUpdate = machineCurrentMetric.get(instanceId);
                if(toAdd){
                    //update cost;
                    metricToUpdate.addCost(cost);
//                    metricToUpdate.addCost(metricToUpdate.getCost().add(response));

                    //update time (adds request time)
                    machineCurrentMetric.get(instanceId).addToReqList(reqTime);

                } else {
                    //update cost;
                    metricToUpdate.subCost(cost);

                    //update time (subtracts request time)
                    machineCurrentMetric.get(instanceId).subFromReqList(reqTime);
                }

            } else { // adds new instance and metric to Load Balancer list of WebServer current metric
                metricToUpdate = new IMetric();
                metricToUpdate.setCost(cost);
                metricToUpdate.addToReqList(reqTime);

                machineCurrentMetric.put(instanceId, metricToUpdate);
            }

//            System.out.println((toAdd ? "Added " : "Subtracted ") + response + " pair key-value: <" + instanceId + ":" + ">");
            System.out.println((toAdd ? "Added " : "Subtracted ") + reqTime + " to list of reqTimes of instance #" + instanceId);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static boolean tryNewInstance(String instancePublicAddress){

        HttpClient client = HttpClientBuilder.create().build();
        String url = "http://"+instancePublicAddress+":8000/f.html?n=2";
        HttpGet request = new HttpGet(url);
        HttpResponse response;
        int statusCode = 404;

        // While the response code is not 200 keep sending requests
        // This will ensure the web server is already running when we forward the request
        while(statusCode != 200){
            try {
                response = client.execute(request);
                statusCode = response.getStatusLine().getStatusCode();
                System.out.println("Response: "+statusCode);
            } catch (IOException e) {
                try {
                    Thread.sleep(5000);
                    System.out.println("New instance unreachable");
                } catch (InterruptedException e1) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }
}

