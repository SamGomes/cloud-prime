
    
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.HashMap;

import com.sun.net.httpserver.*;

 
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


public class WebServer{
 

	private static String myIP;

 	private static DynamoDBWebServerGeneralOperations dbgo;

	public static void main(String[] args) throws Exception {


		dbgo.init();

		myIP = InetAddress.getLocalHost().getHostAddress();
		
		dbgo.createTable("MSSCentralTable", "numberToBeFactored",new String[] {"cost"});

	    HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
	    server.createContext("/f.html", new MyHandler());
	    server.setExecutor(null); // creates a default executor
	    server.start();
	}
 

	private static String saveStats(String name,BigInteger numberToBeFactored,long id, InputStream ins) throws Exception {
	    String line;
	    String result;

	    BufferedReader in = new BufferedReader(
	        new InputStreamReader(ins));
	    result=name + " " +in.readLine();
	    
	    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String formatedDate = dateFormat.format(date);
	
    	line = in.readLine();
		// while(line != null){
		// 	line += in.readLine();
		// }
	 
		System.out.println("date: "+formatedDate);
		try {
			dbgo.insertTuple("MSSCentralTable", new String[]{"numberToBeFactored", String.valueOf(numberToBeFactored), "cost", line});
		}catch(Exception e){
			e.printStackTrace();
		}
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
				Headers responseHeaders = exchange.getResponseHeaders();
				responseHeaders.set("Content-Type", "text/html");
				HashMap map = queryToMap(exchange.getRequestURI().getQuery());
				BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());
				try{
				    Process pro = Runtime.getRuntime().exec("java -cp WebServerCode/instrumented/instrumentedOutput FactorizeMain "+ numberToBeFactored);
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
