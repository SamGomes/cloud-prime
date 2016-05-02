import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.*;


import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.methods.HttpGet;
import com.amazonaws.services.ec2.model.Instance;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.*;

public class EC2LoadBalancer {

	private static HashMap<String,Instance> instances;
	private static int next = 0;
    private static int TIME_TO_REFRESH_INSTANCES = 5000;
    private static Timer timer = new Timer();
    private static String LoadBalancerIp;
    private static final double THRESHOLD = 0.5; //TODO: set a meaningful value
 
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
        	System.out.println(instances.toString());
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

			  try{
				  	BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());
                    String machineIp = getBestMachineIp(numberToBeFactored); //TODO: call cost estimator
                    String url = "http://"+machineIp+":8000/f.html?n="+numberToBeFactored;

			        //String url = "http://"+instances.get(next).getPublicIpAddress()+":8000/f.html?n="+map.get("n");
			        /*System.out.print("Next id: "+next+"\n");
			        if (next >= (instances.size()-1)){
			            next = 0;
			        }else{
			            next += 1;
			        } */

                    //String test = DynamoDBWebServerGeneralOperations.queryTable("MSSCentralTable","numberToBeFactored",numberToBeFactored,"EQ").get(0).get("cost").getS();

                    //System.out.print("test: "+test+"\n");


					//System.out.print(url+"\n");
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

    public static String getBestMachineIp(BigInteger costEstimation){
        String result = "none";
        updateRunningInstances(); //update running instances

        DynamoDBGeneralOperations.estimateCost(costEstimation);

        /*HashMap<String, Double> instanceLoad = getRunningInstancesLoad(instances); //TODO: get the current load of instances from MSS

        for (Map.Entry<String,Double> entry: instanceLoad.entrySet()){
            if (entry.getValue() + costEstimation < THRESHOLD){ //instance can process the request
                //TODO: update instance load
                return instances.get(entry.getKey()).getPublicIpAddress(); // return instance public ip
            } else {
                //continue to check if other instances can process the request
            }
        }
        // if result = "none" -> put the request on hold
        // or launch another instance (?)
        */
        return result;
    }
}
