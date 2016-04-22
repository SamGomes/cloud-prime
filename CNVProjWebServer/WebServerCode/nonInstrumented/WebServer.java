
    
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.HashMap;

import com.sun.net.httpserver.*;
 
 
 
public class WebServer {
 
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/f.html", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
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
				
				BigInteger numberToBeFactored=  new  BigInteger(map.get("n").toString());
				
		        // Factorize fact = new Factorize();
		      
		        // String response = "factorization result: "+fact.calcFactors(numberToBeFactored);

				IntFactorization fact = new IntFactorization();
		      
		        String response = "factorization result: "+fact.calcPrimeFactors(numberToBeFactored);

		        System.out.print(response+"\n");
		         
		        try{
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
