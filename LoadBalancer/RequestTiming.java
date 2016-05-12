import java.math.BigInteger;

public class RequestTiming {

//    private static final long NANO_TO_MILI = 1000000;
    private BigInteger timeOfRequest;
    private BigInteger requestTime;

    public RequestTiming (BigInteger requestTime) {
        this.timeOfRequest = BigInteger.valueOf(System.currentTimeMillis());
//        this.timeOfRequest = System.nanoTime()/NANO_TO_MILI;
        this.requestTime = requestTime;
    }

    public BigInteger getTimeOfRequest() {
        return timeOfRequest;
    }

    public void setTimeOfRequest(BigInteger date) {
        this.timeOfRequest = date;
    }

    public BigInteger getRequestTime() {
        return requestTime;
    }
/*
    public void setSpentTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public void getElapsedTime() {
    }
*/

    /**
     * If (timeOfRequest + requestTime) > System.currentTimeMillis()
     * then the request didn't finnish being processed
     * @return long time remaining to process this request
     */
    public BigInteger getTimeToFinnishProcessing() {
        return (timeOfRequest.add(requestTime)).subtract(BigInteger.valueOf(System.currentTimeMillis()));
//        return ((timeOfRequest + requestTime) - System.nanoTime())/NANO_TO_MILI;
    }

    public String toString() {
        return "<time to finnish: " + getTimeToFinnishProcessing() + " | " + "reqtime: "+ requestTime + ">";
    }

}
