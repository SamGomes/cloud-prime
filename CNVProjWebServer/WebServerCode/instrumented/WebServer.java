import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class WebServer{

    private static final String CENTRAL_TABLE_TIME_ATTRIBUTE = "timeToFactorize";
    private static final String CENTRAL_TABLE_COST_ATTRIBUTE = "cost";
    private static final float MINIMUM_CPU_TO_REGISTER_STATS = 5;
    private static final long NANO_TO_MILI = 1000000;

	public static void main(String[] args) throws Exception {
        DynamoDBWebServerGeneralOperations.init();
        DynamoDBWebServerGeneralOperations.createTable("MSSCentralTable", "numberToBeFactored",
                new String[] {CENTRAL_TABLE_COST_ATTRIBUTE, CENTRAL_TABLE_TIME_ATTRIBUTE});

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(new ThreadPoolExecutor(5, 20, 1200, TimeUnit.SECONDS, new ArrayBlockingQueue(100))); // creates a default executor
        server.createContext("/f.html", new MyHandler());
        server.start();
	}

    static class MyHandler implements HttpHandler {

        private static ArrayList knownNumbers = new ArrayList();

    	public void handle(final HttpExchange exchange) throws IOException {
            new Thread(new Runnable(){

                //@Override
                public void run() {

                    HashMap map = queryToMap(exchange.getRequestURI().getQuery());
                    BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());

                    try{
                        long startTime = System.nanoTime();
                        Process pro = Runtime.getRuntime().exec("java -cp WebServerCode/instrumented/instrumentedOutput FactorizeMain "+ numberToBeFactored);
                        pro.waitFor();
                        long endTime = System.nanoTime();

                        String response = saveStats("factorization result: ",numberToBeFactored,
                                Thread.currentThread().getId(), pro.getInputStream(),
                                (endTime-startTime)/NANO_TO_MILI, 4);

                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }catch(Exception e){
                       e.printStackTrace();
                    }
                }
            }).start();
        }


        private static String saveStats(String name,BigInteger numberToBeFactored,
                                        long id, InputStream ins, long timeToFactor, float previousCPU) throws Exception {
            String line;
            String result;

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(ins));
            result=name + " " +in.readLine();

            line = in.readLine();

            if (previousCPU <= MINIMUM_CPU_TO_REGISTER_STATS){
                try {
                    if(!knownNumbers.contains(numberToBeFactored)){
                        knownNumbers.add(numberToBeFactored);
                        DynamoDBWebServerGeneralOperations.insertTuple("MSSCentralTable",
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
    }
}
