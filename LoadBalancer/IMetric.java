import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class IMetric{


    private ConcurrentLinkedQueue<RequestTiming> reqList;
    private BigInteger cost; // AKA Recalcs
    private double CPUUtil;

    public IMetric(){
        reqList = new ConcurrentLinkedQueue<>();
        cost = new BigInteger("0");
        CPUUtil=0;
    }


    public ConcurrentLinkedQueue<RequestTiming> getReqList() {
        return reqList;
    }

    public BigInteger getCost() {
        return cost;
    }

    public double getCPUUtil() {
        return CPUUtil;
    }

    public void addToReqList(String time) {
        System.out.println("REQLIST: " + reqList);
        this.reqList.add(new RequestTiming(new BigInteger(time)));
    }

    public void subFromReqList(String time) {
        System.out.println("REQLIST: " + reqList);
        removeOldestEqualRequestTime(new BigInteger(time));
    }

    // Removes the oldest request time equal to oldestTime
    private void removeOldestEqualRequestTime(BigInteger oldestTime) {
        for (RequestTiming req : this.reqList) {
            if (req.getRequestTime().equals(oldestTime)) {
                this.reqList.remove(req);
            }
        }
    }

    public void setCost(BigInteger cost) {
        this.cost = cost;
    }

    public void addCost(BigInteger cost) {
        this.cost= this.cost.add(cost);
    }
    public void subCost(BigInteger cost) {

        this.cost=this.cost.subtract(cost);
    }

    public void setCPUUtil(double CPUUtil) {
        this.CPUUtil = CPUUtil;
    }

    public BigInteger getTimeToFinnishEveryRequestProcessing(){

        BigInteger timeToFinnish = BigInteger.ZERO;

        for(RequestTiming time : reqList){
            BigInteger temp = time.getRequestTime();
            if(temp.compareTo(timeToFinnish)==1) {
                timeToFinnish = temp;
            }
        }

        return timeToFinnish;
    }

}