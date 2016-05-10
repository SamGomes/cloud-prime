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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class WebServer{


    private static final String INSTANCE_LOAD_TABLE_NAME = "MSSInstanceLoad";
    private static final String CENTRAL_TABLE_TIME_ATTRIBUTE = "timeToFactorize";
    private static final String CENTRAL_TABLE_COST_ATTRIBUTE = "cost";
    private static final float MINIMUM_CPU_TO_REGISTER_STATS = 5;
    private static final long NANO_TO_MILI = 1000000;
    private static String instanceId;

 	private static DynamoDBWebServerGeneralOperations dbgo;

	public static void main(String[] args) throws Exception {
        DynamoDBWebServerGeneralOperations.init();
        DynamoDBWebServerGeneralOperations.createTable("MSSCentralTable", "numberToBeFactored",
                new String[] {CENTRAL_TABLE_COST_ATTRIBUTE, CENTRAL_TABLE_TIME_ATTRIBUTE});

        //instanceId = getInstanceId();
	    HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
	    server.createContext("/f.html", new MyHandler());
	    //server.setExecutor(null); // creates a default executor
        server.setExecutor(new ThreadPoolExecutor(5, 20, 1200, TimeUnit.SECONDS, new ArrayBlockingQueue(100))); // creates a default executor
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
                DynamoDBWebServerGeneralOperations.insertTuple("MSSCentralTable",
                        new String[]{"numberToBeFactored", String.valueOf(numberToBeFactored),
                                CENTRAL_TABLE_COST_ATTRIBUTE, line, CENTRAL_TABLE_TIME_ATTRIBUTE, String.valueOf(timeToFactor)});

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
                    Headers responseHeaders = exchange.getResponseHeaders();
                    responseHeaders.set("Content-Type", "text/html");
                    HashMap map = queryToMap(exchange.getRequestURI().getQuery());
                    BigInteger numberToBeFactored = new BigInteger(map.get("n").toString());
                    try{
                        //float cpuBeforeFactorization = getInstanceCPU(instanceId);
                        long startTime = System.nanoTime();
                        Process pro = Runtime.getRuntime().exec("java -cp WebServerCode/instrumented/instrumentedOutput FactorizeMain "+ numberToBeFactored);
                        pro.waitFor();
                        long endTime = System.nanoTime();

                        String response = saveStats("factorization result: ",numberToBeFactored,
                                Thread.currentThread().getId(), pro.getInputStream(),
                                (endTime-startTime)/NANO_TO_MILI, 4);
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

    public static String getInstanceId(){
        String EC2Id = "";
        String inputLine;
        try{
            URL EC2MetaData = new URL("http://169.254.169.254/latest/meta-data/instance-id");
            URLConnection EC2MD = EC2MetaData.openConnection();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            EC2MD.getInputStream()));
            while ((inputLine = in.readLine()) != null)
            {
                EC2Id = inputLine;
            }
            in.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        System.out.println(EC2Id);
        return EC2Id;
    }


    static float getInstanceCPU(String instanceId){
        double dpWAverage=0;
        float overallCPUAverage=0;
        long offsetInMilliseconds = 1000 * 10;
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");
        String id = instanceId;

        instanceDimension.setValue(id);
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                .withNamespace("AWS/EC2")
                .withPeriod(new Integer(60))
                .withMetricName("CPUUtilization")
                .withStatistics(new String[]{"Average"})
                .withDimensions(new Dimension[]{instanceDimension})
                .withEndTime(new Date());
        GetMetricStatisticsResult getMetricStatisticsResult =
                EC2WSGeneralOperations.cloudWatch.getMetricStatistics(request);
        List datapoints = getMetricStatisticsResult.getDatapoints();

        int datapointCount=0;
        Iterator it = datapoints.iterator();
        while (it.hasNext()) {
            datapointCount++;
            dpWAverage += 1/datapointCount * ((Datapoint) it.next()).getAverage().doubleValue();
        }

        System.out.println(" CPU utilization for instance " + id + " = " + dpWAverage);
        overallCPUAverage += dpWAverage;

        return overallCPUAverage;
    }
}
