

import BIT.highBIT.*;
import java.io.*;
import java.util.*;



public class FactInstr {

    static DynamoDBGeneralOperations dbgo;



    private static PrintStream out = null;
    private static int i_count = 0, b_count = 0, m_count = 0;
    private static HashMap methodStack = new HashMap();
    
    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */
    public static void main(String argv[]) {
        File file_in = new File(argv[0]);
        String infilenames[] = file_in.list();
        
        for (int i = 0; i < infilenames.length; i++) {
            String infilename = infilenames[i];
            if (infilename.endsWith(".class")) {
				// create class info object
				ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);
				
                // loop through all the routines
                // see java.util.Enumeration for more information on Enumeration class
                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();
					routine.addBefore("FactInstr", "mcount", new Integer(1));
                    routine.addBefore("FactInstr", "putInStack", routine.getMethodName());
                    
                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("FactInstr", "count", new Integer(bb.size()));
                    }
                }
                ci.addAfter("FactInstr", "printICount", ci.getClassName());
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
    
    public static synchronized void printICount(String foo) {
    	//System.out.println("invocation stack info: ");
    	for (Iterator it = methodStack.entrySet().iterator(); it.hasNext();) {
    		Map.Entry pairs = (Map.Entry) it.next();
    		//System.out.println("found method in stack: "+pairs.getKey() + ", ocurred " + pairs.getValue()+" times.");
    		if(pairs.getKey().equals("recCalcFactors")){
    			System.out.println(pairs.getValue());
                return;
    		}

    	}
        System.out.println(0);
        //System.out.println("general results: "+i_count + " instructions in " + b_count + " basic blocks were executed in " + m_count + " methods.");
    }
    

    public static synchronized void count(int incr) {
        i_count += incr;
        b_count++;
    }

    public static synchronized void mcount(int incr) {
		m_count++;
    }

    public static synchronized void putInStack(String methodName) {
    	Integer i = (Integer) methodStack.get(methodName);
    	if (i==null){
    		i= new Integer(0);
    	}
    	Integer inc = new Integer(i.intValue()+1);
    	
        methodStack.put(methodName, inc);

        
    }
}

