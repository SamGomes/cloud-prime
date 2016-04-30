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
        int[] result = chooseCost(nFactor);
        int estimatedCost = result[0];
        int isEstimated = result[1];

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
            Integer[] keys = costs.keySet().toArray(new Integer[costs.size()]);
//            int[] nearestNumberToFactorV = nearestK(keys, numberToFactorize, 3);
//            int nearestNumber = nearestNumberToFactorV[0];

            //TESTING PURPOSES
//            int[] lowerAndHigher = findNearestCostInterval(keys, numberToFactorize);
//            System.out.println("Lower value: " + lowerAndHigher[0]);
//            System.out.println("Higher value: " + lowerAndHigher[1]);

            return new int[]{calculateEstimatedCost(keys, numberToFactorize), ESTIMATED_COST};
        }
    }

    public static int calculateEstimatedCost(Integer[] array, int val){

        // Find nearest number factored key interval
        NavigableSet<Integer> values = new TreeSet<Integer>();
        for (int x : array) { values.add(x); }
        int lower = values.floor(val);
        int higher = values.ceiling(val);
        //return new int[]{lower, higher};

        // Proportions
        double lowerProportion = 1 - (val - lower)/((double)(higher - lower));
        double higherProportion = 1 - (higher - val)/((double) (higher - lower));

//        System.out.println("Lower: " + lower);
//        System.out.println("Higher: " + higher);
//        System.out.println("LowerPro: " + lowerProportion);
//        System.out.println("HigherPro: " + higherProportion);
//        System.out.println("Estimated Cost: " + Math.round(lowerProportion * costs.get(lower) + higherProportion * costs.get(higher)));

        return (int) Math.round(lowerProportion * costs.get(lower) + higherProportion * costs.get(higher));
    }

    private static void populateCosts() {
        int[][] madeUpCosts = {
                {1, 2},
                {3, 4},
                {6, 10},
                {9, 45},
                {12, 4},
                {25, 5},
                {75, 6},
                {101, 67},
                {893, 57},
                {1000, 46},
                {4345, 102},
                {5657453, 2067}
        };

        for (int[] metric : madeUpCosts){
            costs.put(metric[0], metric[1]);
        }
    }

    // Solution from http://stackoverflow.com/questions/18581598/method-that-searches-an-array-to-find-certain-number-of-nearest-values
//    public static int[] nearestK(Integer[] a, int val, int k) {
//        // omitted your checks for brevity
//        final int value = val; // needs to be final for the comparator, you can also make the parameter final and skip this line
//        Integer[] copy = new Integer[a.length]; // copy the array using autoboxing
//        for (int i = 0; i < a.length; i++) {
//            copy[i] = a[i];
//        }
//        Arrays.sort(copy, new Comparator<Integer>() { // sort it with a custom comparator
//            @Override
//            public int compare(Integer o1, Integer o2) {
//                int distance1 = Math.abs(value - o1);
//                int distance2 = Math.abs(value - o2);
//                return Integer.compare(distance1, distance2);
//            }
//        });
//        int[] answer = new int[k]; // pick the first k elements
//        for (int i = 0; i < answer.length; i++) {
//            answer[i] = copy[i];
//        }
//        return answer;
//    }



//    public static int[] findNearestCostInterval(Integer[] array, int val){
//        NavigableSet<Integer> values = new TreeSet<Integer>();
//        for (int x : array) { values.add(x); }
//        int lower = values.floor(val);
//        int higher = values.ceiling(val);
//        return new int[]{lower, higher};
//    }

}
