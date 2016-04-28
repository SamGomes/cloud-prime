import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class EstimatorTrial {

    // HashMap<numberFactorized , cost>
    static HashMap<Integer, Integer> costs = new HashMap<>();
    private static int ESTIMATED_COST = 1;
    private static int DIRECT_COST = 2;

    public static void main(String[] args) throws Exception {

        // populate array of costs
        populateCosts();

        // print all costs


        // ask for a number
        System.out.println("Give me a number to be factored:");

        Scanner scan = new Scanner(System.in);
        int nFactor = scan.nextInt();

        //choose the estimated cost
        int estimatedCost = chooseCost(nFactor)[0];
        int isEstimated = chooseCost(nFactor)[1];

        //print cost choosen and why
        printCost(nFactor, estimatedCost, isEstimated);

        //test more numbers

    }

    private static void printCost(int numberToFactor, int estimatedC, int isEstimate){
        System.out.println("Number to factor: " + String.valueOf(numberToFactor));
        System.out.println("Cost: " + String.valueOf(estimatedC));
        System.out.println("This is " + (isEstimate == ESTIMATED_COST ? "an ESTIMATED" : "a DIRECT") + " cost.");
    }

    private static int[] chooseCost(int numberToFactorize){
        // if we know the cost of the number to factorize we return it
        if(costs.containsKey(numberToFactorize)){
            return new int[]{costs.get(numberToFactorize), DIRECT_COST};

        } else { // gets nearest value cost
            int nearestNumberToFactor = (nearestK(costs.keySet().toArray(new Integer[costs.size()]), numberToFactorize, 1))[0];
            return new int[]{costs.get(nearestNumberToFactor), ESTIMATED_COST};
        }
    }

    private static void populateCosts() {
        int[][] madeUpCosts = {
                {1, 2}, {25, 5}, {3, 8}, {4345, 35},
                {5657453, 267}, {6, 4}, {75, 6}, {893, 57},
                {9, 45}, {101, 67}, {1000, 46}, {12, 4}
        };

        for (int[] metric : madeUpCosts){
            costs.put(metric[0], metric[1]);
        }
    }

    // Solution from http://stackoverflow.com/questions/18581598/method-that-searches-an-array-to-find-certain-number-of-nearest-values
    public static int[] nearestK(Integer[] a, int val, int k) {
        // omitted your checks for brevity
        final int value = val; // needs to be final for the comparator, you can also make the parameter final and skip this line
        Integer[] copy = new Integer[a.length]; // copy the array using autoboxing
        for (int i = 0; i < a.length; i++) {
            copy[i] = a[i];
        }
        Arrays.sort(copy, new Comparator<Integer>() { // sort it with a custom comparator
            @Override
            public int compare(Integer o1, Integer o2) {
                int distance1 = Math.abs(value - o1);
                int distance2 = Math.abs(value - o2);
                return Integer.compare(distance1, distance2);
            }
        });
        int[] answer = new int[k]; // pick the first k elements
        for (int i = 0; i < answer.length; i++) {
            answer[i] = copy[i];
        }
        return answer;
    }
}
