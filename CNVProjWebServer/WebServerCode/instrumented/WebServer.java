
    
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.HashMap;

import com.sun.net.httpserver.*;

 



public class WebServer {
 

	private static String myIP;

 	private static DynamoDBGeneralOperations dbgo;

	public static void main(String[] args) throws Exception {
	
		//dbgo = new DynamoDBGeneralOperations();

		//dbgo.init();

		myIP = InetAddress.getLocalHost().getHostAddress();
		
		//dbgo.createTable("123.78.65.43", "date",new String[] {"numberToBeFactored","ThreadId","reCalcFactorsInfo"});

	    HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
	    server.createContext("/f.html", new MyHandler());
	    server.setExecutor(null); // creates a default executor
	    server.start();
	}
 

	private static String saveStats(String name,BigInteger numberToBeFactored,long id, InputStream ins) throws Exception {
	   

		

	    String line = null;
	    String result ="";
	    BufferedReader in = new BufferedReader(
	        new InputStreamReader(ins));
	    result=name + " " +in.readLine();
	    
  
	
	    	 line = in.readLine();
//	    	 while(line != null){
//	    	 	line += in.readLine();
//	    	 }
//	    	 
	    	 System.out.println("line: "+line);
	 		//dbgo.insertTuple("123.78.65.43",new String[] {"date","12345","numberToBeFactored",String.valueOf(numberToBeFactored),"ThreadId",String.valueOf(id),"reCalcFactorsInfo",line});

	
	    return result;
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
				
				BigInteger numberToBeFactored= new  BigInteger(map.get("n").toString());

				try{


				    Process pro = Runtime.getRuntime().exec("java -cp WebServerCode/instrumented/instrumentedOutput FactorizeMain "+ numberToBeFactored);
				    //Process pro = Runtime.getRuntime().exec("java -cp WebServerCode/instrumented/instrumentedOutput MainIntFactorization "+ numberToBeFactored);

				    pro.waitFor();


			        String response = saveStats("factorization result: ",numberToBeFactored,Thread.currentThread().getId(), pro.getInputStream());

			        System.out.print(response+"\n");
			        
			
			        exchange.sendResponseHeaders(200, response.length());
			        OutputStream os = exchange.getResponseBody();
			        os.write(response.getBytes());
			        os.close();
		        }catch(Exception e){}
			        
				
			}
		}).start();

        }
    }
 
}
