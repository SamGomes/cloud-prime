import java.math.BigInteger;



public class FactorizeMain {
	public static void main(String[] numberToBeFactored){
		Factorize fact = new Factorize();
		System.out.println(fact.calcFactors(new BigInteger(numberToBeFactored[0])));
	}
}
