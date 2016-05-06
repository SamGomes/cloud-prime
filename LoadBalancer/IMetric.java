import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class IMetric{


    private ConcurrentLinkedQueue<String> reqList;
    private BigInteger cost;
    private double CPUUtil;

    public IMetric(){
        reqList = new ConcurrentLinkedQueue<>();
        cost = new BigInteger("0");
        CPUUtil=0;
    }


    public ConcurrentLinkedQueue< String> getReqList() {
        return reqList;
    }

    public BigInteger getCost() {
        return cost;
    }

    public double getCPUUtil() {
        return CPUUtil;
    }

    public void addToReqList(String time) {
        System.out.println("REQLIST: "+reqList);
        this.reqList.add(time);
    }

    public void subFromReqList(String time) {
        System.out.println("REQLIST: "+reqList);
        this.reqList.remove(time);
    }

    public void setCost(BigInteger cost) {
        this.cost = cost;
    }

    public void setCPUUtil(double CPUUtil) {
        this.CPUUtil = CPUUtil;
    }


}