import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class IMetric{


    private ConcurrentLinkedQueue<RequestTiming> reqList;
    private BigDecimal cost; // AKA Recalcs
    private ConcurrentHashMap<BigDecimal, BigDecimal> servingQueue;
    private double CPUUtil;

    public IMetric(){
        reqList = new ConcurrentLinkedQueue<>();
        servingQueue = new ConcurrentHashMap<>();
        cost = new BigDecimal("0");
        CPUUtil=0;
    }


    public ConcurrentLinkedQueue<RequestTiming> getReqList() {
        return reqList;
    }

    public BigDecimal getCost() {
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

    public synchronized void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public synchronized void addCost(BigDecimal cost) {
        this.cost = this.cost.add(cost);
    }
    public synchronized void subCost(BigDecimal cost) {
        this.cost = this.cost.subtract(cost);
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

    public void addToServingQueue(BigDecimal cost, BigDecimal time){
        if (servingQueue.contains(time)){
            servingQueue.put(time,servingQueue.get(time).add(cost));
            servingQueue.remove(time, servingQueue.get(time));
        } else{
            servingQueue.put(time, cost);
        }
    }

    public void removeFromServingQueue(BigDecimal cost, BigDecimal time){
        if (servingQueue.contains(time)){
            servingQueue.put(time.subtract(time),servingQueue.get(time).subtract(cost));
            servingQueue.remove(time, servingQueue.get(time));
        }
        servingQueue.remove(time, servingQueue.get(time));
    }

    public synchronized long willSupportRequest(BigDecimal cost, BigDecimal timeToProcess, long timeOfWait){

        BigDecimal minTimeToWait = new BigDecimal("0");
        BigDecimal timeToWait = BigDecimal.valueOf(timeOfWait);

        for(Map.Entry<BigDecimal,BigDecimal> entry: servingQueue.entrySet()){
            if(cost.compareTo(entry.getValue()) >= 0 && entry.getKey().compareTo(timeToWait) <= 0){
                if (minTimeToWait.compareTo(entry.getKey()) > 0){
                    minTimeToWait = entry.getKey();
                }
            }
        }
        return timeOfWait;
    }
}