import java.io.*;
import java.util.*;

/*
  1º Choose lines randomly to serve as training data from 
         [1,2,3,4]-labeled.dat. The amount of line sis defined 
        by learningPerc 
  2º Learn probabilities of each class from the training data, to build
        the estimated probability matrix
  3º Apply Naive Bayes classifier using the matrix, to the rest of the
        lines which serve as validation data
  4º Calculate the error, false positives and false negatives
  5º Apply classifier to [1,2,3,4]-unlabeled.dat
*/
public class NaiveBayesAlgorithm {

    private static final int nMetrics = 6;

    public static void main(String[] args) {

      // args = [nLabel, learningPercentage, showMatrix]
      if (args.length < 3) {
        return;
      }

      String nLabel = args[0];
      float learningPercentage = Float.parseFloat(args[1]);
      boolean showMatrix = Boolean.parseBoolean(args[2]);;
        
        BufferedReader dataBR = null;
        try {
            dataBR = new BufferedReader(new FileReader(new File(nLabel + "-labeled.dat")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String line = "";

        ArrayList<String[]> dataArr = new ArrayList<String[]>(); //An ArrayList is used because I don't know how many records are in the file.
        try {
            dataBR.readLine();
            while ((line = dataBR.readLine()) != null) { // Read a single line from the file until there are no more lines to read

                String[] value = line.split(",", nMetrics); 
                String[] data = new String[nMetrics];               
                int n = Math.min(value.length, data.length);
                for (int i = 0; i < n; i++) { // For each token in the line that we've read:???
                    data[i] = value[i]; // Place the token into the 'i'th "column"??
                }
                dataArr.add(data); // Add a specific group of metrics in the list of metrics
            }

        /*
            for (int i = 0; i < dataArr.size(); i++) {
                for (int x = 0; x < dataArr.get(i).length; x++) {
                    System.out.printf("dataArr[%d][%d]: ", i, x);
                    System.out.println(dataArr.get(i)[x]);
                }
            }
        */  
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Array that will have containers each one with
        //                                       all the probabilities calculated
        List<Map<String, float[]>> classes = new ArrayList<>();                     // probability matrix
        int totalSamples = dataArr.size();         // number of samples
        int nColumns = dataArr.get(1).length;      // number of columns

        // assuming a percentage between [0:1]
        int totalLearning = Math.round(learningPercentage * totalSamples);
        int totalValidation = totalSamples - totalLearning;

        System.out.println(nColumns);

        if(totalLearning == 0){
            System.out.println("STOP! This does not work without learning samples!");
            return;
        }
        if(totalLearning > totalSamples){
            System.out.println("STOP! This should not be even possible -.-");
            return;
        }

        //get random integers, the indexes for the random learning samples
        Set<Integer> learningRows = new LinkedHashSet<Integer>();
        Random randomGenerator = new Random();
        for (int i = 0; i < totalLearning; i++) {

            Integer randomIndex = randomGenerator.nextInt(totalSamples);
            while (learningRows.contains(randomIndex)){
                randomIndex = randomGenerator.nextInt(totalSamples);
            }
            learningRows.add(randomIndex);
        }

        /*
        % Iterate through every column and create a container
        %      Key            Value  
        %  -------------------------------
        % |     IP     |   p2p  | not p2p |
        % |            |   (1)  |   (2)   |
        %  -------------------------------
        % |192.168.1.0 |   0.2  |   0.3   |
        % |192.168.2.0 |   ""   |    ""   |
        % |192.168.3.0 |   ""   |    ""   |
        % |192.168.4.0 |   ""   |    ""   |
        % |192.168.5.0 |   ""   |    ""   |
        %  -------------------------------
        %
        %  Key represents the possible column different entries
        %  Value is a vector where (1) is the p2p probability and
        %                          (2) is the not p2p probability
        */


        Map<String, float[]> metricClass;
        for (int column = 0;  column < nColumns-1; column++){
            metricClass = new HashMap<String, float[]>();
           int peer2peer = 0;
           int notpeer2peer = 0;
            for (int row = 0;  row < totalSamples; row++){
               //if class does not contain certain subclass, ex: IP
               if (!metricClass.containsKey(dataArr.get(row)[column])) {
                   metricClass.put(dataArr.get(row)[column], new float[]{0,0}); // [p2p, not p2p]
                   //System.out.println(dataArr.get(row)[column]);
               }
               
               if (learningRows.contains(row)) {
                   float[] subClass = metricClass.get(dataArr.get(row)[column]);
                   if (dataArr.get(row)[nColumns-1].equals("p2p")) { // if p2p
                        subClass[0] = subClass[0] + 1;
                        metricClass.put(dataArr.get(row)[column], subClass);
                        peer2peer = peer2peer + 1;
                   } else {
                        subClass[1] = subClass[1] + 1;
                        metricClass.put(dataArr.get(row)[column], subClass);
                        notpeer2peer = notpeer2peer + 1;
                   }
               }
           }
           
           //calculate probability of being 'p2p' or 'not p2p' for each class
            int nMetricsInGroup = metricClass.keySet().size();
           String[] keysClass = metricClass.keySet().toArray(new String[nMetricsInGroup]);
            float[] metricBinaryResult;
           for (int subClassRow = 0; subClassRow < keysClass.length; subClassRow++){

               metricBinaryResult = metricClass.get(keysClass[subClassRow]);
               if(peer2peer != 0){
                    metricBinaryResult[0] = metricBinaryResult[0] / peer2peer;
               } else {
                   metricBinaryResult[0] = -1;
               }
               if(notpeer2peer != 0){
                    metricBinaryResult[1] = metricBinaryResult[1] / notpeer2peer;
               } else {
                   metricBinaryResult[1] = -1;
               }
               metricClass.put(keysClass[subClassRow], metricBinaryResult);
           }   
           classes.add(metricClass);
        }

        // Printing Probability Matrix
        if(showMatrix){
            if(totalLearning > 0) {
                System.out.println("- Naive Bayes Learning");
                String[] titles = {"IP", "Number of Connections", "Avg Bandwidth", "Avg Packet Size", "Time"};
                String[] matrixKeys;
                int n = 0;
                int nMetricsInGroupTemp;
                //for (int n = 0; n < nColumns; n++){
                for (Map<String, float[]> metricClassTemp : classes){
                    nMetricsInGroupTemp = metricClassTemp.keySet().size();
                    String[] matrixKeysTemp = metricClassTemp.keySet().toArray(new String[nMetricsInGroupTemp]);
                    System.out.println("----------------------" + nMetricsInGroupTemp + "-------------------------");
                    System.out.println("| " + titles[n] +" |        P2P     |       not P2P       |");
                    for (int nkey = 0; nkey < nMetricsInGroupTemp; nkey++){
                        float[] vector = metricClassTemp.get(matrixKeysTemp[nkey]);
                        System.out.println("|   " + matrixKeysTemp[nkey] + "      |       " + vector[0] + "       |       " + vector[1]);
                    }
                    n++;
                }
            }
        }
    }
}