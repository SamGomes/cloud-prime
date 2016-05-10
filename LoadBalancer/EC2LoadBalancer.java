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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class EC2LoadBalancer {

    private static ConcurrentHashMap<String,Instance> runningInstances;
    private static int TIME_TO_REFRESH_INSTANCES = 5000;
    private static Timer timer = new Timer();
    private static String LoadBalancerIp;
    private static boolean isLaunchingNewInstance = false;
    private static final Object lock = new Object();

    private static BigDecimal THRESHOLD = new BigDecimal("250458758"); //Cost of 53111239897403731
    private static final String AMI_ID = "ami-83f206e3";

    private static ConcurrentHashMap<String, IMetric> machineCurrentMetric = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        LoadBalancerIp = InetAddress.getLocalHost().getHostAddress();
        server.createContext("/f.html", new MyHandler());
        //server.setExecutor(null); // creates a default executor
        server.setExecutor(new ThreadPoolExecutor(5, 20, 1200, TimeUnit.SECONDS, new ArrayBlockingQueue(100))); // creates a default executor
        createInstanceList();
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

            new Thread(new Runnable(){
                public void run() {
                    Headers responseHeaders = exchange.getResponseHeaders();
                    responseHeaders.set("Content-Type", "text/html");
                    HashMap map = queryToMap(exchange.getRequestURI().getQuery());

                    BigDecimal numberToBeFactored = new BigDecimal(map.get("n").toString());

                    String[] bestMachine = getBestMachineIp(numberToBeFactored);
                    while(bestMachine[0] == null){
                        try {
                            TimeUnit.SECONDS.sleep(3);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        bestMachine = getBestMachineIp(numberToBeFactored);
                    }
                    if (bestMachine[1] == null){
                        System.out.println("Could not find any instance to serve the request");
                    }else{
                        try{
                            Instance instance = runningInstances.get(bestMachine[0]);
                            BigInteger cost = new BigInteger(bestMachine[1]);
                            BigInteger reqTime = new BigInteger(bestMachine[2]).
                                    add(BigInteger.valueOf(System.currentTimeMillis()));

                            // update (add) current metric
                            updateInstanceMetric(instance, cost, reqTime, true); //cannot be numberToBeFactored

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
                            updateInstanceMetric(instance, cost, reqTime, false);

                            exchange.sendResponseHeaders(200, result.length());
                            OutputStream os = exchange.getResponseBody();
                            os.write(result.toString().getBytes());
                            os.close();
                        }catch(Exception e){
                            System.out.println("Socket problem");
                        }
                    }
                }
            }).start();
        }
    }

    public static void startTimer(){
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateRunningInstances();
            }
        }, TIME_TO_REFRESH_INSTANCES, TIME_TO_REFRESH_INSTANCES);
    }

    public static void updateRunningInstances(){
        runningInstances = null;
        runningInstances = EC2LBGeneralOperations.getRunningInstancesArray();
    }

    /**
     * If there's N machines are being unused it terminates N-1 instances
     *
     * @throws Exception
     */
    public static void deleteUnusedInstances() throws Exception{
        Instance currentInstance;

        // Check if any machine is not being used and terminate if its not needed
        for (Map.Entry<String,Instance> entry: runningInstances.entrySet()){
            currentInstance = entry.getValue();

            IMetric metric = machineCurrentMetric.get(currentInstance.getInstanceId());

            System.out.println("metrics: " + (metric != null ? metric.getReqList().toString() : "null"));
            ConcurrentLinkedQueue<RequestTiming> reqList;
            // the table is populated with at least one request ????
            if(metric != null){
                reqList =  metric.getReqList();
                if (reqList.isEmpty()){

                    System.out.println("Terminating instance " + currentInstance.getInstanceId());
                    EC2LBGeneralOperations.terminateInstance(currentInstance.getInstanceId(), null);

                }
            }
        }
    }

    /**
     * Get the best machine to process a request according to its current cost and estimated cost
     * for the number to factorize
     *
     * The best machine (bm) is selected from the running aws instances, according to their
     * current work load (if workload < threshold)
     * If no instance can support the request, the LB tries to choose the machine that will
     * end the processing of a request that will remove enough work load to accommodate the
     * new request.
     *
     *
     * @param numberToFactorize
     * @return instance and estimated cost for the number to be factored
     */
    public static String[] getBestMachineIp(BigDecimal numberToFactorize){
        // [ instanceId, cost, reqTime ]
        String[] results = new String[3];

        //update running runningInstances
        //updateRunningInstances();

        // estimated/direct cost for the numberToFactorize
        BigDecimal[] estimatedValues = DynamoDBGeneralOperations.estimateCostScan(numberToFactorize);
        BigDecimal estimatedCost = estimatedValues[0];
        BigDecimal estimatedTime = estimatedValues[1];
        Instance bestInstance = null;

        //if there arent machines running just start one!
        int instSize = EC2LBGeneralOperations.getActiveInstances();
        IMetric metric = null;
        long willSupportRequestIn = 0;

        if(instSize > 0){
            for(Map.Entry<String,Instance> entry: runningInstances.entrySet()){
                metric = machineCurrentMetric.get(entry.getKey());
                if (metric == null){
                    machineCurrentMetric.put(entry.getKey(),new IMetric());
                    metric = machineCurrentMetric.get(entry.getKey());
                }
                bestInstance = entry.getValue();

                // The current instance can process the request
                if(metric.getCost().add(estimatedCost).compareTo(THRESHOLD) == -1){
                    results[0] = entry.getKey();
                    results[1] = estimatedCost.toString();
                    results[2] = estimatedTime.toString();
                    return results;
                } else{
                    long timeToWaitForInstance = metric.willSupportRequest(
                            estimatedCost, estimatedTime, System.currentTimeMillis());
                    if (willSupportRequestIn > timeToWaitForInstance){
                        willSupportRequestIn = timeToWaitForInstance;
                    }
                    THRESHOLD = estimatedCost.add(new BigDecimal("1"));
                }
            }
            // One of instances will be able to support the request in the near future
            if (willSupportRequestIn > 0){
                String formatted = String.format("%02d min, %02d sec",
                        TimeUnit.MILLISECONDS.toMinutes(willSupportRequestIn),
                        TimeUnit.MILLISECONDS.toSeconds(willSupportRequestIn) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(willSupportRequestIn))
                );
                System.out.println("Waiting for "+formatted);
                try {
                    Thread.sleep(willSupportRequestIn);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                results[0] = bestInstance.getInstanceId();
                results[1] = estimatedCost.toString();
                results[2] = estimatedTime.toString();
                return results;
            }else{
                if (estimatedCost.compareTo(THRESHOLD) == -1){
                    THRESHOLD = estimatedCost.add(new BigDecimal("1"));
                }
                return results;
            }
        }/*else{
            Instance instance = null;
            if (!isLaunchingNewInstance){
                try {
                    isLaunchingNewInstance = true;
                    // launch new instance
                    Instance newInstance = EC2LBGeneralOperations.startInstance(null,null,null,"WebServer", AMI_ID);
                    machineCurrentMetric.put(newInstance.getInstanceId(),new IMetric());
                    while (!EC2LBGeneralOperations.getInstanceStatus(newInstance.getInstanceId()).equals("running")){
                        TimeUnit.SECONDS.sleep(5);
                    }
                    System.out.println("returning new instance");
                    instance = EC2LBGeneralOperations.getInstanceById(newInstance.getInstanceId());
                    //tryNewInstance(instance.getPublicIpAddress());
                    IMetric iMetric = new IMetric();
                    machineCurrentMetric.put(instance.getInstanceId(),iMetric);
                    isLaunchingNewInstance = false;

                    results[0] = instance.getInstanceId();
                    results[1] = estimatedCost.toString();
                    results[2] = estimatedTime.toString();
                    return results;

                } catch (Exception e) {
                    System.out.println("Instance still unreachable");
                }
            }else{
                // Since an instance is being launched
                // wait for it to launch and forward the requests there
                while(isLaunchingNewInstance){
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (instance != null){
                    results[0] = instance.getInstanceId();
                    results[1] = estimatedCost.toString();
                    results[2] = estimatedTime.toString();
                    return results;
                }
            }
        } */
        return results;
    }

    public static String[] launchNewInstance(BigDecimal estimatedCost, BigDecimal estimatedTime){

        String[] results = new String[3];
        synchronized (lock){
            Instance instance = null;
            if (!isLaunchingNewInstance){
                try {
                    isLaunchingNewInstance = true;
                    // launch new instance
                    Instance newInstance = EC2LBGeneralOperations.startInstance(null,null,null,"WebServer", AMI_ID);
                    machineCurrentMetric.put(newInstance.getInstanceId(),new IMetric());
                    while (!EC2LBGeneralOperations.getInstanceStatus(newInstance.getInstanceId()).equals("running")){
                        TimeUnit.SECONDS.sleep(5);
                    }
                    instance = EC2LBGeneralOperations.getInstanceById(newInstance.getInstanceId());
                    tryNewInstance(instance.getPublicIpAddress());
                    isLaunchingNewInstance = false;

                    results[0] = instance.getInstanceId();
                    results[1] = estimatedCost.toString();
                    results[2] = estimatedTime.toString();
                    return results;

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else{
                // Since an instance is being launched
                // wait for it to launch and forward the requests there
           /* while(isLaunchingNewInstance){
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (instance != null){
                    results[0] = instance.getInstanceId();
                    results[1] = estimatedCost.toString();
                    results[2] = estimatedTime.toString();
                    return results;
                }
            }
        }
        return results;
    }

    public static void updateInstanceMetric(Instance newInstance, BigInteger cost, BigInteger requestTime, boolean toAdd) throws InterruptedException{

        String instanceId = newInstance.getInstanceId();
        try {
            IMetric metricToUpdate;

            if(machineCurrentMetric.containsKey(instanceId)) {

                metricToUpdate = machineCurrentMetric.get(instanceId);
                if(metricToUpdate == null){
                    metricToUpdate = new IMetric();
                    machineCurrentMetric.put(instanceId, metricToUpdate);
                }
                if(toAdd){
                    //update cost;
                    metricToUpdate.addCost(new BigDecimal(cost));

                    //update time (adds request time)
                    metricToUpdate.addToServingQueue(new BigDecimal(cost), new BigDecimal(requestTime));
                } else {
                    //update cost;
                    metricToUpdate.subCost(new BigDecimal(cost));

                    //update time (removes request time)
                    metricToUpdate.removeFromServingQueue(new BigDecimal(cost), new BigDecimal(requestTime));
                }

            } else { // adds new instance and metric to Load Balancer list of WebServer current metric
                metricToUpdate = new IMetric();
                metricToUpdate.setCost(new BigDecimal(cost));
                metricToUpdate.addToServingQueue(new BigDecimal(cost), new BigDecimal(requestTime));

                machineCurrentMetric.put(instanceId, metricToUpdate);
            }
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
            } catch (IOException e) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                    System.out.println("Instance still unreachable");
                }
            }
        }
        return true;
    }
}

