
import java.math.BigInteger;
import java.util.ArrayList;


public class Factorize {
	private BigInteger numberToBeFactored;
	private ArrayList result = new ArrayList();

	
	public ArrayList calcFactors(BigInteger numberToBeFactored){
		this.numberToBeFactored = (numberToBeFactored).add(BigInteger.ZERO);
		
		BigInteger inc;
		inc=(BigInteger.valueOf(2)).add(BigInteger.ZERO);
		while(inc.compareTo(numberToBeFactored.divide(inc))==-1 || inc.compareTo(numberToBeFactored.divide(inc))==0 ){
			
			recCalcFactors(inc);
			
			inc = inc.add(BigInteger.ONE);
				
		}
		if (this.numberToBeFactored.compareTo(BigInteger.valueOf(1)) == 1) {
			result.add(this.numberToBeFactored);
	    }
		return result;
	}
	
	public void recCalcFactors(BigInteger inc){
		
		
		if(numberToBeFactored.remainder(inc).compareTo(BigInteger.ZERO)==0){
		
			result.add(inc);
			numberToBeFactored=(numberToBeFactored.divide(inc)).add(BigInteger.ZERO);
			
			recCalcFactors(inc);
		
		
		}
	}
	

}
