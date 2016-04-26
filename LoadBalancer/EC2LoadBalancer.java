import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.methods.HttpGet;
import com.amazonaws.services.ec2.model.Instance;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

import com.sun.net.httpserver.*;

public class EC2LoadBalancer {

	private static ArrayList<Instance> instances;
	private static int next = 0;
    private static int TIME_TO_REFRESH_INSTANCES = 5000;
    private static Timer timer = new Timer();
 
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/f.html", new MyHandler());
        server.setExecutor(null); // creates a default executor
        createInstanceList();
        server.start();
        startTimer();
    }

    public static void createInstanceList(){
    	
    	try{
        	EC2GeneralOperations.init();
        	instances = EC2GeneralOperations.getInstances();
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
			        String url = "http://"+instances.get(next).getPublicIpAddress()+":8000/f.html?n="+map.get("n");
			        System.out.print("Next id: "+next+"\n");
			        if (next >= (instances.size()-1)){
			            next = 0;
			        }else{
			            next += 1;
			        }

					System.out.print(url+"\n");
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
        instances = EC2GeneralOperations.getRunningInstancesArray();
        System.out.println("Running instances: "+instances.size());
    }
 
}
