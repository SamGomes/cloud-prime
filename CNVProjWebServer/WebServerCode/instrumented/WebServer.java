
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * WebServer class responsible to factorize numbers requested to the Load Balancer
 */
public class WebServer{

	private static String myIP;
    private static final String MSS_CENTRAL_TABLE = "MSSCentralTable";
    private static final String CENTRAL_TABLE_COST_ATTRIBUTE = "cost";
	private static final String CENTRAL_TABLE_NUMBER_TO_FACTORIZE_ATTRIBUTE = "numberToBeFactored";
    public static final long NANO_TO_MILI = 1000000;
    private static HttpServer server;

	public static void main(String[] args) throws Exception {
		DynamoDBWebServerGeneralOperations.init();
		myIP = InetAddress.getLocalHost().getHostAddress();
		DynamoDBWebServerGeneralOperations.createTable(
				MSS_CENTRAL_TABLE,
				CENTRAL_TABLE_NUMBER_TO_FACTORIZE_ATTRIBUTE,
                new String[] {CENTRAL_TABLE_COST_ATTRIBUTE}
		);

	    server = HttpServer.create(new InetSocketAddress(8000), 1000);
	    server.setExecutor(new ThreadPoolExecutor(Integer.MAX_VALUE, Integer.MAX_VALUE, 1200, TimeUnit.SECONDS, new ArrayBlockingQueue(100))); // creates a default executor
		server.createContext("/f.html", new MyHandler());
	    server.start();
	}
}

class MyHandler implements HttpHandler {

	private static final String MSS_CENTRAL_TABLE = "MSSCentralTable";
	private static final String CENTRAL_TABLE_COST_ATTRIBUTE = "cost";
	private static final String CENTRAL_TABLE_NUMBER_TO_FACTORIZE_ATTRIBUTE = "numberToBeFactored";
	private static ConcurrentLinkedQueue knownNumbers = new ConcurrentLinkedQueue();
	private static int CACHESIZE = 100;


	public void handle(final HttpExchange exchange) throws IOException {

		HashMap map = queryToMap(exchange.getRequestURI().getQuery());
		BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());
		OutputStream os = null;

		try {
			Process pro = Runtime.getRuntime().exec("java -cp WebServerCode/instrumented/instrumentedOutput FactorizeMain " + numberToBeFactored);
			pro.waitFor();

			String response = saveStats(numberToBeFactored, pro.getInputStream());
			System.out.print(response+"\n");

			exchange.sendResponseHeaders(200, response.length());
			os = exchange.getResponseBody();
			os.write(response.getBytes());

			os.close();

		} catch(Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * Saves new number to factorize on DynamoDB and in known numbers array, which serves as a cache
	 * If the number already exists on known numbers array doesn't access the database
	 *
	 * @param numberToBeFactored number to factorize
	 * @param ins input stream containing the factorization result
	 * @return result of factorization
     * @throws Exception
     */
	public String saveStats(BigInteger numberToBeFactored, InputStream ins) throws Exception {
		String line;
		String result;

		BufferedReader in = new BufferedReader(
				new InputStreamReader(ins));
		result = "factorization result: " + in.readLine();

		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String formatedDate = dateFormat.format(date);

		//get first line (instrumentation output - cost)
		line = in.readLine();

		try{

			synchronized(this) {

				System.out.println("Known Numbers: " + knownNumbers.toString());
				if (knownNumbers.size() > CACHESIZE) {
					knownNumbers.remove(knownNumbers.iterator().next());
				}

				if (!knownNumbers.contains(numberToBeFactored)) {
					knownNumbers.add(numberToBeFactored);

					// Inserts number to factorize in MSS Central Table on DynamoDB
					DynamoDBWebServerGeneralOperations.insertTuple(
							MSS_CENTRAL_TABLE,
							new String[]{
									CENTRAL_TABLE_NUMBER_TO_FACTORIZE_ATTRIBUTE,
									String.valueOf(numberToBeFactored),
									CENTRAL_TABLE_COST_ATTRIBUTE,
									line
							}
					);

				} else {
					knownNumbers.remove(numberToBeFactored);
					knownNumbers.add(numberToBeFactored);
				}
			}


		}catch(Exception e){
			e.printStackTrace();
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
