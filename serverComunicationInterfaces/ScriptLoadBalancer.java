import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.Scanner;

public class ScriptLoadBalancer {
    private BigInteger numberToFactorize;
    private int numberOfThreads;
    //--------connect to load balancer------
//    private static String URL = "http://Proj-LB-681069110.us-west-2.elb.amazonaws.com/f.html?n=";

    //--------connect to localhost------
    private static String URL = "http://localhost:8000/f.html?n=";
    private static int MAX_BITS = 60;
    private static int NUMBERS_INTERVAL = 5;

    public ScriptLoadBalancer(BigInteger factorize, int threads) {
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
        BigInteger factorize1;
        BigInteger factorize2;
        int maxNumberOfBits;
        int threads;

//        System.out.println("Enter the number to factorize");
//        maxNumberOfBits = inputScanner.nextBigInteger();
        System.out.println("Enter maximum number of bits for your random number to factorize");
        maxNumberOfBits = inputScanner.nextInt();
        System.out.println("Enter the number of threads");
        threads = inputScanner.nextInt();
        inputScanner.close();

        if (maxNumberOfBits > MAX_BITS) {
            System.out.println("That's too much, under 150 please!");
            return;
        }

        int portion = Math.round(maxNumberOfBits/NUMBERS_INTERVAL);
        for (int i = 0; i < NUMBERS_INTERVAL; i++) {
            maxNumberOfBits = maxNumberOfBits - portion;
            factorize1 = new BigInteger(maxNumberOfBits, new Random()).nextProbablePrime();
            factorize2 = new BigInteger(maxNumberOfBits, new Random()).nextProbablePrime();

            System.out.println("this is my prime " + factorize1.multiply(factorize2));
            new ScriptLoadBalancer(factorize1.multiply(factorize2), threads).runTest();
        }

    }

    // HTTP GET request
    private void sendGet() throws Exception {

        long getTime = System.currentTimeMillis();

        String url = URL + numberToFactorize;

        java.net.URL obj = new URL(url);
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
