import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.Math.*;
import java.util.regex.*;

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
    	String showMatrix = args[2];
        
        BufferedReader dataBR = null;
        try {
            dataBR = new BufferedReader(new FileReader(new File(nLabel + "-labeled.dat")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String line = "";

        ArrayList<String[]> dataArr = new ArrayList<String[]>(); //An ArrayList is used because I don't know how many records are in the file.
        try {
            while ((line = dataBR.readLine()) != null) { // Read a single line from the file until there are no more lines to read

                String[] value = line.split(",", nMetrics); 
                String[] data = new String[nMetrics];               
                int n = Math.min(value.length, data.length);
                for (int i = 0; i < n; i++) { // For each token in the line that we've read:
                    data[i] = value[i]; // Place the token into the 'i'th "column"
                }
                dataArr.add(data); // Add the "club" info to the list of clubs.
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
        String[] classes = {};                     // probability matrix
        int totalSamples = dataArr.size();         // number of samples
        int nColumns = dataArr.get(1).length;      // number of columns

        // assuming a percentage between [0:1]
        int totalLearning = Math.round(learningPercentage * totalSamples);
        int totalValidation = totalSamples - totalLearning;

        //System.out.println(totalLearning);
/*
        try {
            // Read a CSV file with metrics from file
            Scanner scanner = new Scanner(new File(nLabel + "-labeled.dat"));
            scanner.useDelimiter(",");
            int i = 0;
            while(scanner.hasNext()){
                System.out.print(scanner.next() + "|");
                i++;
                if(i==10){

                return;    
                }
                //System.out.print(scanner.next());
            }
            scanner.close();            
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    // Array that will have containers each one with
    //                                       all the probabilities calculated
    String[] classes = {};                 // probability matrix
    int totalSamples = mLabeled[0].length;  // number of samples
    int nColumns = mLabeled.length;         // number of columns

    // assumindo que a percentagem vem em decimal [0:1]
    int totalLearning = round(learningPerc * totalSamples);
    int totalValidation = totalSamples - totalLearning;
*/
    }
}