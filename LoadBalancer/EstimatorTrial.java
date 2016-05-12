import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.NavigableSet;
import java.util.Scanner;
import java.util.TreeSet;

/**
 * Estimator trial tests cost estimation given key-value pairs
 * of number to be factored and its costs of computation
 */
public class EstimatorTrial {

    static HashMap<BigInteger, BigInteger> costs = new HashMap<>();
    private static int ESTIMATED_COST = 1;
    private static int DIRECT_COST = 2;
    private static int DECIMAL_HOUSES = 6;

    public static void main(String[] args) throws Exception {

        // populate array of costs
        populateCosts();

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

        //print cost chosen and why
        printCost(nFactor, estimatedCost, isEstimated);


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
            result.put(calculateEstimatedCost(keys, numberToFactorize), ESTIMATED_COST);
            return result;
        }
    }

    public static BigInteger calculateEstimatedCost(BigInteger[] array, BigInteger val){

        populateCosts();
        // Find nearest number factored key interval
        NavigableSet<BigInteger> values = new TreeSet<BigInteger>();
        for (BigInteger x : array) { values.add(x); }
        BigInteger l = values.floor(val);
        BigInteger h = values.ceiling(val);
        BigDecimal value = new BigDecimal(val);

        BigDecimal finalCost = new BigDecimal(0);

        System.out.println("Lower: " + l);
        System.out.println("Higher: " + h);
        System.out.println("Lower cost: "+ costs.get(l));
        System.out.println("Higher cost: "+ costs.get(h));

        try{
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
        }catch (Exception e){
            e.printStackTrace();
        }

        return finalCost.toBigInteger();
    }

    private static void populateCosts() {
        BigInteger[] madeUpFactors = {
                new BigInteger("1"),
                new BigInteger("49"),
                new BigInteger("76"),
                new BigInteger("1000"),
                new BigInteger("1025"),
                new BigInteger("1050"),
                new BigInteger("1075"),
                new BigInteger("1500"),
                new BigInteger("2000"),
                new BigInteger("2500"),
                new BigInteger("5362334"),
                new BigInteger("48765433")
        };

        BigInteger[] madeUpCosts = {
                new BigInteger("0"),
                new BigInteger("8"),
                new BigInteger("9"),
                new BigInteger("36"),
                new BigInteger("33"),
                new BigInteger("36"),
                new BigInteger("33"),
                new BigInteger("43"),
                new BigInteger("50"),
                new BigInteger("55"),
                new BigInteger("2315"),
                new BigInteger("6982")
        };

        for (int i = 0; i < madeUpCosts.length; i++){
            costs.put(madeUpFactors[i], madeUpCosts[i]);
        }
    }
}
