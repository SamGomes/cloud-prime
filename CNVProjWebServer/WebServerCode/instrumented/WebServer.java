
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class WebServer{
 

	private static String myIP;
    private static final String MSS_CENTRAL_TABLE = "MSSCentralTable";
    private static final String CENTRAL_TABLE_TIME_ATTRIBUTE = "timeToFactorize";
    private static final String CENTRAL_TABLE_COST_ATTRIBUTE = "cost";
    public static final long NANO_TO_MILI = 1000000;


    private static HttpServer server;
	public static void main(String[] args) throws Exception {
		DynamoDBWebServerGeneralOperations.init();
		myIP = InetAddress.getLocalHost().getHostAddress();
		DynamoDBWebServerGeneralOperations.createTable(MSS_CENTRAL_TABLE, "numberToBeFactored",
                new String[] {CENTRAL_TABLE_COST_ATTRIBUTE, CENTRAL_TABLE_TIME_ATTRIBUTE});

        //instanceId = getInstanceId();
	    server = HttpServer.create(new InetSocketAddress(8000), 1000);
	    server.setExecutor(new ThreadPoolExecutor(5, 20, 1200, TimeUnit.SECONDS, new ArrayBlockingQueue(100))); // creates a default executor
		server.createContext("/f.html", new MyHandler());
	    server.start();
	}
 



}

class MyHandler implements HttpHandler {

	private static String myIP;
	private static final String MSS_CENTRAL_TABLE = "MSSCentralTable";
	private static final String CENTRAL_TABLE_TIME_ATTRIBUTE = "timeToFactorize";
	private static final String CENTRAL_TABLE_COST_ATTRIBUTE = "cost";
	private static ArrayList knownNumbers = new ArrayList();
	private static int requestNum = 0;


	public void handle(final HttpExchange exchange) throws IOException {

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
					(endTime-startTime)/WebServer.NANO_TO_MILI, 4);
			System.out.print(response+"\n");


			exchange.sendResponseHeaders(200, response.length());
			os = exchange.getResponseBody();
			os.write(response.getBytes());

			os.close();


		}catch(Exception e){
			e.printStackTrace();
		}finally {

		}
	}


	public String saveStats(String name,BigInteger numberToBeFactored,
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

		if (previousCPU <= 5){
			try {
				if(!knownNumbers.contains(numberToBeFactored)) {
					knownNumbers.add(numberToBeFactored);
					DynamoDBWebServerGeneralOperations.insertTuple(MSS_CENTRAL_TABLE,
							new String[]{"numberToBeFactored", String.valueOf(numberToBeFactored),
									CENTRAL_TABLE_COST_ATTRIBUTE, line, CENTRAL_TABLE_TIME_ATTRIBUTE, String.valueOf(timeToFactor)});
				}

			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return result;
	}

	public HashMap queryToMap(String query){
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

}
