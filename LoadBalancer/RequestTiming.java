
public class RequestTiming {

//    private static final long NANO_TO_MILI = 1000000;
    private long timeOfRequest;
    private long requestTime;

    public RequestTiming (long requestTime) {
        this.timeOfRequest = System.currentTimeMillis();
//        this.timeOfRequest = System.nanoTime()/NANO_TO_MILI;
        this.requestTime = requestTime;
    }

    public long getTimeOfRequest() {
        return timeOfRequest;
    }

    public void setTimeOfRequest(long date) {
        this.timeOfRequest = date;
    }

    public long getRequestTime() {
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
    public long getTimeToFinnishProcessing() {
        return (timeOfRequest + requestTime) - System.currentTimeMillis();
//        return ((timeOfRequest + requestTime) - System.nanoTime())/NANO_TO_MILI;
    }

    public String toString() {
        return "<time to finnish: " + getTimeToFinnishProcessing() + " | " + "reqtime: "+ requestTime + ">";
    }

}
