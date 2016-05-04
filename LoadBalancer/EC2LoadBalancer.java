import com.amazonaws.services.dynamodbv2.model.AttributeValue;
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
import java.util.concurrent.TimeUnit;

public class EC2LoadBalancer {

	private static HashMap<String,Instance> instances;
	private static int next = 0;
    private static int TIME_TO_REFRESH_INSTANCES = 20000;
    private static int THREAD_SLEEP_TIME = 20 * 1000; //Time in milliseconds
    private static Timer timer = new Timer();
    private static String LoadBalancerIp;
    private static final BigDecimal THRESHOLD = new BigDecimal("2300"); //TODO: set a meaningful value
    private static final String INSTANCE_LOAD_TABLE_NAME = "MSSInstanceLoad";
    private static final String AMI_ID = "ami-83f206e3";

    private static ArrayList<BigInteger> pendingRequests = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        LoadBalancerIp = InetAddress.getLocalHost().getHostAddress();
        server.createContext("/f.html", new MyHandler());
        server.setExecutor(null); // creates a default executor
        createInstanceList();
        server.start();
        startTimer();
    }

    public static void createInstanceList(){

    	try{
        	EC2LBGeneralOperations.init();
            DynamoDBGeneralOperations.init();
            EC2LBGeneralOperations.addLoadBalancerToExceptionList(LoadBalancerIp);
        	//instances = EC2LBGeneralOperations.getInstances();
            instances = EC2LBGeneralOperations.getRunningInstancesArray();
        	next = 0;
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
                    Instance instance = getBestMachineIp(numberToBeFactored);
                    if (instance == null){
                        System.out.println("Could not find any instance to serve the request");
                    }else{
                        try{
                            System.out.println(instance.getInstanceId());
                            String url = "http://"+instance.getPublicIpAddress()+":8000/f.html?n="+numberToBeFactored;

                            //TODO: method that update the instance load
                            //Map<String, AttributeValue> instanceLoad = DynamoDBWebServerGeneralOperations.getInstanceTuple(INSTANCE_LOAD_TABLE_NAME,instance.getInstanceId());
                            //int currentLoad = Integer.parseInt(instanceLoad.get(instance.getInstanceId()).getS());
                            //DynamoDBWebServerGeneralOperations.updateInstanceLoad(INSTANCE_LOAD_TABLE_NAME, currentLoad+0);

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

                            exchange.sendResponseHeaders(200, result.length());
                            OutputStream os = exchange.getResponseBody();
                            os.write(result.toString().getBytes());
                            os.close();
                        }catch(Exception e){}
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
        System.out.println("Running instances: "+instances.size());
    }

    public static Instance getBestMachineIp(BigInteger costEstimation){
        Instance result = null;
        float currentCPULoad = 0;
        updateRunningInstances(); //update running instances

        //BigInteger response = DynamoDBGeneralOperations.estimateCostScan(costEstimation); //This function returns the result of the scan request
        BigInteger response = DynamoDBGeneralOperations.estimateCost(costEstimation); //TODO: return the result with a query request
        System.out.println("Estimated cost "+response.toString());

        for (Map.Entry<String,Instance> entry: instances.entrySet()){
            String instanceLoad;
            try {
                //instanceLoad = DynamoDBGeneralOperations.getInstanceTuple(INSTANCE_LOAD_TABLE_NAME,entry.getValue().getInstanceId());
                //int currentLoad = Integer.parseInt(instanceLoad.get(entry.getKey()).getS());
                //BigInteger currentLoad = new BigInteger(instanceLoad.get(entry.getKey()).getS());
                currentCPULoad = DynamoDBGeneralOperations.getInstanceCPU(entry.getValue().getInstanceId());
                BigDecimal cpuLoad = new BigDecimal(currentCPULoad,
                        new MathContext(3, RoundingMode.HALF_EVEN));
                if(cpuLoad.add(new BigDecimal(costEstimation)).compareTo(THRESHOLD) == -1){
                    return entry.getValue();
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
                String state = newInstance.getState().getName();
                while (!state.equals("running")){
                    TimeUnit.SECONDS.sleep(5);
                    System.out.println("waiting....");
                    System.out.println("Current state: " + state);
                    state = newInstance.getState().getName();
                }
                System.out.println("returning new instance");
                return newInstance;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return result;
    }
}

