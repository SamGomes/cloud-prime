import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;

public class EstimatorTrial {

    // HashMap<numberFactorized , cost>
    static HashMap<BigInteger, BigInteger> costs = new HashMap<>();
    private static int ESTIMATED_COST = 1;
    private static int DIRECT_COST = 2;
    private static int DECIMAL_HOUSES = 6;

    public static void main(String[] args) throws Exception {

        // populate array of costs
        populateCosts();

        // print all costs


        // ask for a number
        System.out.println("Give me a number to be factored:");

        Scanner scan = new Scanner(System.in);
        BigInteger nFactor = scan.nextBigInteger();

        //choose the estimated cost
        HashMap<BigInteger,Integer> finalResult = chooseCost(nFactor);
        BigInteger[] resK = finalResult.keySet().toArray(new BigInteger[]{});
        Integer[] resV = finalResult.values().toArray(new Integer[]{});
        BigInteger estimatedCost = resK[0];
        int isEstimated = resV[0];

        //print cost choosen and why
        printCost(nFactor, estimatedCost, isEstimated);

        //test more numbers

    }

    private static void printCost(BigInteger numberToFactor, BigInteger estimatedC, int isEstimate){
        System.out.println("Number to factor: " + String.valueOf(numberToFactor));
        System.out.println("Cost: " + String.valueOf(estimatedC));
        System.out.println("This is " + (isEstimate == ESTIMATED_COST ? "an ESTIMATED" : "a DIRECT") + " cost.");
    }

    private static HashMap<BigInteger,Integer> chooseCost(BigInteger numberToFactorize){

        HashMap<BigInteger,Integer> result = new HashMap<BigInteger,Integer>();

        // if we know the cost of the number to factorize we return it
        if(costs.containsKey(numberToFactorize)){
            result.put(costs.get(numberToFactorize), DIRECT_COST);
            return result;
        } else { // gets nearest value cost
            BigInteger[] keys = costs.keySet().toArray(new BigInteger[costs.size()]);
//            int[] nearestNumberToFactorV = nearestK(keys, numberToFactorize, 3);
//            int nearestNumber = nearestNumberToFactorV[0];

            //TESTING PURPOSES
//            int[] lowerAndHigher = findNearestCostInterval(keys, numberToFactorize);
//            System.out.println("Lower value: " + lowerAndHigher[0]);
//            System.out.println("Higher value: " + lowerAndHigher[1]);
            result.put(calculateEstimatedCost(keys, numberToFactorize), ESTIMATED_COST);
            return result;
        }
    }

    public static BigInteger calculateEstimatedCost(BigInteger[] array, BigInteger val){

        // Find nearest number factored key interval
        NavigableSet<BigInteger> values = new TreeSet<BigInteger>();
        for (BigInteger x : array) { values.add(x); }
        BigInteger l = values.floor(val);
        BigInteger h = values.ceiling(val);
        //return new int[]{lower, higher};
        BigDecimal value = new BigDecimal(val);

        BigDecimal finalCost;

        System.out.println("Lower: " + l);
        System.out.println("Higher: " + h);

        if(h == null || l == null) {

            if(h == null) {
                finalCost = (value.multiply(new BigDecimal(costs.get(l))).divide(new BigDecimal(l), DECIMAL_HOUSES, RoundingMode.CEILING));
            } else {
                finalCost = (value.multiply(new BigDecimal(costs.get(h))).divide(new BigDecimal(h), DECIMAL_HOUSES, RoundingMode.CEILING));
            }
        } else {

            BigDecimal lower = new BigDecimal(l);
            BigDecimal higher = new BigDecimal(h);

            // Proportions
            BigDecimal lowerProportion = BigDecimal.ONE.subtract((value.subtract(lower)).divide(higher.subtract(lower), DECIMAL_HOUSES, RoundingMode.CEILING));
            BigDecimal higherProportion = BigDecimal.ONE.subtract((higher.subtract(value)).divide(higher.subtract(lower), DECIMAL_HOUSES, RoundingMode.CEILING));
            finalCost = (lowerProportion.multiply(new BigDecimal(costs.get(lower.toBigInteger()))).add(higherProportion.multiply(new BigDecimal(costs.get(higher.toBigInteger())))));

            System.out.println("LowerPro: " + lowerProportion);
            System.out.println("HigherPro: " + higherProportion);
        }

        System.out.println(finalCost);

        return finalCost.toBigInteger();
    }
//
//    public static int calculate3SimpleRuleEstimatedCost(BigInteger[] array, BigInteger val){
//
//    }

    private static void populateCosts() {
        BigInteger[] madeUpFactors = {
                new BigInteger("1"),
                new BigInteger("49"),
                new BigInteger("76"),
                new BigInteger("5362334"),
                new BigInteger("48765433")
//                new BigInteger("3"),
//                new BigInteger("6"),
//                new BigInteger("9"),
//                new BigInteger("12"),
//                new BigInteger("25"),
//                new BigInteger("49"),
//                new BigInteger("75"),
//                new BigInteger("101"),
//                new BigInteger("893"),
//                new BigInteger("1000"),
//                new BigInteger("4345"),
////                BigInteger.valueOf(5657453)
//                new BigInteger("5657453869890899797999999999999999999999999999979")
        };

        BigInteger[] madeUpCosts = {
                new BigInteger("0"),
                new BigInteger("8"),
                new BigInteger("9"),
                new BigInteger("2315"),
                new BigInteger("6982")
//                new BigInteger("4"),
//                new BigInteger("10"),
//                new BigInteger("45"),
//                new BigInteger("4"),
//                new BigInteger("5"),
//                new BigInteger("8"),
//                new BigInteger("6"),
//                new BigInteger("67"),
//                new BigInteger("57"),
//                new BigInteger("46"),
//                new BigInteger("102"),
//                new BigInteger("2067")
        };

//        for (int[] metric : madeUpCosts){
        for (int i = 0; i < madeUpCosts.length; i++){
            costs.put(madeUpFactors[i], madeUpCosts[i]);
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
