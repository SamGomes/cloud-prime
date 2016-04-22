
    
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import com.amazonaws.services.ec2.model.Instance;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.*;
 
 
 
public class EC2LoadBalancer {

	private static ArrayList<Instance> instances;
	private static int next = 0;
 
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/f.html", new MyHandler());
        server.setExecutor(null); // creates a default executor
        createInstanceList();
        server.start();
    }

    public static void createInstanceList(){
    	EC2GeneralOperations ecgo = new EC2GeneralOperations();
    	
    	try{
        	ecgo.init();
        	instances = ecgo.getInstances();
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
				
				InputStream inpStream = exchange.getRequestBody();
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

				
					//String url = "http://52.11.195.145:8000/f.html?n="+map.get("n");
					
					
					System.out.print(url+"\n");
					
					//System.out.println("Thread Id: "+Thread.currentThread().getId());
	
					
					HttpClient client = HttpClientBuilder.create().build();
					HttpGet request = new HttpGet(url);
	
					
					HttpResponse response = client.execute(request);
	
					System.out.println("Response Code : " 
				                + response.getStatusLine().getStatusCode());
	
					BufferedReader rd = new BufferedReader(
						new InputStreamReader(response.getEntity().getContent(),StandardCharsets.UTF_8));
	
					StringBuffer result = new StringBuffer();
					String line = "";
					//result.append("~(º~º)-|_200_| ");
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
 
}
