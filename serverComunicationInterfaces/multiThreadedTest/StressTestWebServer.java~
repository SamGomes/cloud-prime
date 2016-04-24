import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.io.*;

public class StressTestWebServer {

    private BigInteger numberToFactorize;
    private int numberOfThreads;
    //--------connect to load balancer------
    private static String URL = "http://Proj-LB-681069110.us-west-2.elb.amazonaws.com/f.html?n=";
    
    //--------connect to localhost------
    //private static String URL = "http://localhost:8000/fact&";
    
    public StressTestWebServer(BigInteger factorize, int threads) {
        // TODO Auto-generated constructor stub
        this.numberToFactorize = factorize;
        this.numberOfThreads = threads;
    }

    public void runTest(){
        for (int i=0; i<numberOfThreads; i++){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    //String response = executePost(URL,String.valueOf(numberToFactorize));
                    //System.out.println("Server replied: "+ response);
                    try {
                        sendGet();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }
    
    public static void main(String args[]){
        
        Scanner inputScanner = new Scanner(System.in);
        
        System.out.println("Enter the number to factorize");
        BigInteger factorize = inputScanner.nextBigInteger();
        System.out.println("Enter the number of threads");
        int threads = inputScanner.nextInt();
        inputScanner.close();
        new StressTestWebServer(factorize, threads).runTest();
    }
    
    // HTTP GET request
    private void sendGet() throws Exception {

        long getTime = System.currentTimeMillis();

        String url = URL + numberToFactorize;
        
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        //add request header
        con.setRequestProperty("User-Agent", "");

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

       
         //print result
        long responseTime = System.currentTimeMillis();
        System.out.println(response.toString());
        System.out.println("Reply time: "+String.valueOf((responseTime - getTime)*Math.pow(10,-3)+ " seconds"));

    }
}
