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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EC2LoadBalancer {

    private static ConcurrentHashMap<String,Instance> instances;
    private static int TIME_TO_REFRESH_INSTANCES = 20000;
    private static int THREAD_SLEEP_TIME = 20 * 1000; //Time in milliseconds
    private static Timer timer = new Timer();
    private static String LoadBalancerIp;

    private static final BigDecimal THRESHOLD = new BigDecimal("2300777"); //TODO: set a meaningful value

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
                    HashMap<Instance, BigInteger> bestMachine = getBestMachineIp(numberToBeFactored);
                    Instance instance = (Instance) bestMachine.keySet().toArray()[0];
                    BigInteger metric = bestMachine.get(instance);

                    if (instance == null){
                        System.out.println("Could not find any instance to serve the request");
                    }else{
                        try{
                            System.out.println(instance.getPublicIpAddress());
                            System.out.println("Thread id: "+Thread.currentThread().getId());

                            // update (add) current metric
                            updateInstanceMetric(instance, numberToBeFactored,metric, true); //cannot be numberToBeFactored

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
                            updateInstanceMetric(instance,numberToBeFactored, metric, false);

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
            }
        }, TIME_TO_REFRESH_INSTANCES, TIME_TO_REFRESH_INSTANCES);
    }

    public static void updateRunningInstances(){
        instances = null;
        instances = EC2LBGeneralOperations.getRunningInstancesArray();
        System.out.println("Running instances:  "+instances.size());
    }

    public static HashMap<Instance, BigInteger> getBestMachineIp(BigInteger costEstimation){
        Instance result = null;
        float currentCPULoad;
        HashMap<Instance, BigInteger> finalResult = new HashMap<>();
        updateRunningInstances(); //update running instances

        BigInteger response = DynamoDBGeneralOperations.estimateCostScan(costEstimation);
        System.out.println("Estimated cost "+response.toString());

        for (Map.Entry<String,Instance> entry: instances.entrySet()){
            try {

                currentCPULoad = DynamoDBGeneralOperations.getInstanceCPU(entry.getValue().getInstanceId());
                BigDecimal cpuLoad = new BigDecimal(currentCPULoad,
                        new MathContext(3, RoundingMode.HALF_EVEN));
                if(cpuLoad.add(new BigDecimal(costEstimation)).compareTo(THRESHOLD) == -1){
                    finalResult.put(entry.getValue(), response);
                    return finalResult;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /*
        *  There are no instances available to process the request.
        *  Another instance is launched and the request is sent
        * */
        if (result == null){
            try {
                // launch instance
                Instance newInstance = EC2LBGeneralOperations.startInstance(null,null,null,"WebServer", AMI_ID);
                while (!EC2LBGeneralOperations.getInstanceStatus(newInstance.getInstanceId()).equals("running")){
                    TimeUnit.SECONDS.sleep(5);
                }
                System.out.println("returning new instance");
                Instance instance = EC2LBGeneralOperations.getInstanceById(newInstance.getInstanceId());
                tryNewInstance(instance.getPublicIpAddress());
                finalResult.put(instance, response);
                return finalResult;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        finalResult.put(result, response);
        return finalResult;
    }

    public static void updateInstanceMetric(Instance newInstance, BigInteger num, BigInteger response, boolean toAdd) throws InterruptedException{

        String instanceId = newInstance.getInstanceId();
        try {

            ItemCollection<QueryOutcome> reqTimes = (DynamoDBGeneralOperations.queryTable("MSSCentralTable", "numberToBeFactored", num.toString()));
            String reqTime="NA";
            if(reqTimes.iterator().hasNext()) {
                reqTime = reqTimes.iterator().next().get("timeToFactorize").toString();
                System.out.println("reqTime: " + reqTime);

            }

            IMetric metricToUpdate;


            if(machineCurrentMetric.containsKey(instanceId)) {

                metricToUpdate = machineCurrentMetric.get(instanceId);
                if(toAdd){
                    //update cost;
                    metricToUpdate.setCost(metricToUpdate.getCost().add(response));

                    //update time
                    machineCurrentMetric.get(instanceId).addToReqList(reqTime);

                } else {
                    //update cost;
                    metricToUpdate.setCost(metricToUpdate.getCost().subtract(response));

                    //update time
                    machineCurrentMetric.get(instanceId).subFromReqList(reqTime);
                }

            }else{
                metricToUpdate = new IMetric();
                metricToUpdate.setCost(response);
                metricToUpdate.addToReqList(reqTime.toString());

                machineCurrentMetric.put(instanceId, metricToUpdate);
            }

            System.out.println((toAdd ? "Added " : "Subtracted ") + response + " pair key-value: <" + instanceId + ":" + ">");
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

