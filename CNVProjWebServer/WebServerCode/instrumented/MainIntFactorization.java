import java.math.BigInteger;
import java.util.ArrayList;
import java.io.IOException;


public class MainIntFactorization{
   public static void main(String[] args){
    IntFactorization fact = new IntFactorization();
    System.out.println(fact.calcPrimeFactors(new BigInteger(args[0])));
  }
}

   