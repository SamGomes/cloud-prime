import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;


public class WebServer{
 

	private static String myIP;
    private static final String MSS_CENTRAL_TABLE = "MSSCentralTable";
    private static final String CENTRAL_TABLE_TIME_ATTRIBUTE = "timeToFactorize";
    private static final String CENTRAL_TABLE_COST_ATTRIBUTE = "cost";
    private static final long NANO_TO_MILI = 1000000;
    private static String instanceId;
    private static ArrayList knownNumbers = new ArrayList();

 	private static DynamoDBWebServerGeneralOperations dbgo;

    private static HttpServer server;
	public static void main(String[] args) throws Exception {
		dbgo.init();
		myIP = InetAddress.getLocalHost().getHostAddress();
		dbgo.createTable(MSS_CENTRAL_TABLE, "numberToBeFactored",
                new String[] {CENTRAL_TABLE_COST_ATTRIBUTE, CENTRAL_TABLE_TIME_ATTRIBUTE});

        //instanceId = getInstanceId();
	    server = HttpServer.create(new InetSocketAddress(8000), 0);
	    server.createContext("/f.html", new MyHandler());
	    server.setExecutor(null); // creates a default executor
	    server.start();
	}
 

	private static String saveStats(String name,BigInteger numberToBeFactored,
                                    long id, InputStream ins, long timeToFactor, float previousCPU) throws Exception {
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
        if (previousCPU <= 5){
            try {
                if(!knownNumbers.contains(numberToBeFactored)) {
                    knownNumbers.add(numberToBeFactored);
                    dbgo.insertTuple(MSS_CENTRAL_TABLE,
                            new String[]{"numberToBeFactored", String.valueOf(numberToBeFactored),
                                    CENTRAL_TABLE_COST_ATTRIBUTE, line, CENTRAL_TABLE_TIME_ATTRIBUTE, String.valueOf(timeToFactor)});
                }

            }catch(Exception e){
                e.printStackTrace();
            }
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
				HashMap map = queryToMap(exchange.getRequestURI().getQuery());
				BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());
                OutputStream os = null;
				try{
                    long startTime = System.nanoTime();

                    Process pro = Runtime.getRuntime().exec("java -cp WebServerCode/instrumented/instrumentedOutput FactorizeMain " + numberToBeFactored);
                    pro.waitFor();

                    long endTime = System.nanoTime();

                    String response = saveStats("factorization result: ",numberToBeFactored,
                            Thread.currentThread().getId(), pro.getInputStream(),
                            (endTime-startTime)/NANO_TO_MILI, 4);
                    System.out.print(response+"\n");

                    try{
                    synchronized(this)(
                        exchange.sendResponseHeaders(200, response.length());
                        os = exchange.getResponseBody();
                        os.write(response.getBytes());


                        os.flush();
                        os.close();
                    }catch(IOException e){e.printStackTrace();}

		        }catch(Exception e){
                    e.printStackTrace();
                }finally {

                }
            }
		}).start();

        }
    }

}
