/* 2016-04 Extended by Samuel Gomes */


/*
 * Copyright 2010-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.model.*;

import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;


public class DynamoDBGeneralOperations {

    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */



    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     *
     */

    static AmazonDynamoDBClient dynamoDB;
    private static final String TABLE_NAME = "MSSCentralTable";
    private static final String PRIMARY_KEY = "numberToBeFactored";
    private static final String COST_ATTRIBUTE = "cost";
    private static final String INSTANCE_PRIMARY_KEY = "instanceId";
    private static int DECIMAL_PlACES = 6;
    private static LinkedHashMap<BigInteger,BigInteger> knownNumbers = new LinkedHashMap<>();
    private static int CACHESIZE = 100;



    // numberToFactorized : cost
    static HashMap<BigInteger, BigInteger> costs = new HashMap<>();

    static void init() throws Exception {

        System.out.println("init");
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        AWSCredentials credentials;

        credentials = new ProfileCredentialsProvider().getCredentials();
        dynamoDB = new AmazonDynamoDBClient(credentials);
        Region usWest2 = Region.getRegion(Regions.US_WEST_2);
        dynamoDB.setRegion(usWest2);
    }

    static ItemCollection<QueryOutcome> queryTable(String tableName,String var, String val) throws Exception {

        DynamoDB dynamoDBT = new DynamoDB(dynamoDB);


        Table table = dynamoDBT.getTable(tableName);

        System.out.println("varVal: "+var +" = "+val);

        QuerySpec spec = new QuerySpec()
                .withKeyConditionExpression(var+" = :v_id")
                .withScanIndexForward(true)
                .withValueMap(new ValueMap()
                        .withString(":v_id", val));

        ItemCollection<QueryOutcome> items = table.query(spec);

        return items;
    }

    static synchronized void insertTuple(String tableName,String[] attrAndValues) throws Exception {


        System.out.println("insertTuple! tableName: "+tableName+", attrAndValues: ");

        for(int i=0;i<attrAndValues.length;i++){
            System.out.print(" "+attrAndValues[i]+" ,");
        }
        System.out.print("\n");

        Map item = new HashMap();

        int attrSize = attrAndValues.length;

        for(int i=0; i<attrSize;i+=2){
            item.put(attrAndValues[i], new AttributeValue(attrAndValues[i+1]));
        }

        PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
        PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);
        System.out.println("Insertion result: " + putItemResult);
    }

    static synchronized Map<String,AttributeValue> getInstanceTuple(String tableName,String instance) throws Exception {

        Map<String, AttributeValue> instanceLoadTuple = null;

        Condition hashKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(instance));

        Map<String, Condition> keyConditions = new HashMap<>();
        keyConditions.put(INSTANCE_PRIMARY_KEY, hashKeyCondition);

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(tableName)
                .withKeyConditions(keyConditions)
                .withLimit(1);

        QueryResult result = dynamoDB.query(queryRequest);
        if (result.getCount() > 0){
            instanceLoadTuple = result.getItems().get(0);
        }
        return instanceLoadTuple;
    }


    static synchronized BigInteger estimateCostScan(BigInteger estimate){


        if(knownNumbers.containsKey(estimate)){
            System.out.println("i know this numbers estimate! cache: "+knownNumbers.toString());
            BigInteger knownEstimate = knownNumbers.get(estimate);
            knownNumbers.remove(estimate);
            knownNumbers.put(estimate,knownEstimate);
            if(knownNumbers.size()>CACHESIZE) {
                knownNumbers.remove(knownNumbers.keySet().iterator().next());
            }
            return knownEstimate;
        }


        ArrayList<BigInteger> numbersFactorized = new ArrayList<>();

        try{

            BigInteger numberCostTuple;

            Condition hashKeyCondition = new Condition()
                    .withComparisonOperator(ComparisonOperator.EQ.toString())
                    .withAttributeValueList(new AttributeValue().withS(estimate.toString()));

            Map<String, Condition> keyConditions = new HashMap<>();
            keyConditions.put(PRIMARY_KEY, hashKeyCondition);

            QueryRequest queryRequest = new QueryRequest()
                    .withTableName(TABLE_NAME)
                    .withKeyConditions(keyConditions)
                    .withLimit(1);

            QueryResult result = dynamoDB.query(queryRequest);
            if (result.getCount() > 0){
                numberCostTuple = new BigInteger(result.getItems().get(0).get(COST_ATTRIBUTE).getS());
                knownNumbers.put(estimate,numberCostTuple);
                return numberCostTuple;

            }

            /*
            * If the number is not present in the table,
            * search for the nearest lower and higher numbers
            * then use them to calculate the estimated cost
            * */
            Map<String, AttributeValue> expressionLowerAttributeValues =
                    new HashMap<>();
            expressionLowerAttributeValues.put(":val", new AttributeValue().withS(estimate.toString()));

            Map<String, AttributeValue> expressionHigherAttributeValues =
                    new HashMap<>();
            expressionHigherAttributeValues.put(":val", new AttributeValue().withS(estimate.toString()));

            ScanRequest scanLowerRequest = new ScanRequest()
                    .withTableName(TABLE_NAME)
                    .withFilterExpression("numberToBeFactored < :val")
                    .withExpressionAttributeValues(expressionLowerAttributeValues);

            ScanResult lowerClosestValue = dynamoDB.scan(scanLowerRequest);

            ScanRequest scanHigherRequest = new ScanRequest()
                    .withTableName(TABLE_NAME)
                    .withFilterExpression("numberToBeFactored > :val")
                    .withExpressionAttributeValues(expressionHigherAttributeValues);

            ScanResult higherClosestValue = dynamoDB.scan(scanHigherRequest);

            System.out.println("Query response size " + lowerClosestValue.getItems().size() );
            System.out.println("Query response size " + higherClosestValue.getItems().size() );

            for (Map<String, AttributeValue> item : lowerClosestValue.getItems()){
                AttributeValue value = item.get(PRIMARY_KEY);
                AttributeValue cost = item.get(COST_ATTRIBUTE);
                numbersFactorized.add(new BigInteger(value.getS()));
                costs.put(new BigInteger(value.getS()),new BigInteger(cost.getS()));
            }

            for (Map<String, AttributeValue> item : higherClosestValue.getItems()){
                AttributeValue value = item.get(PRIMARY_KEY);
                AttributeValue cost = item.get(COST_ATTRIBUTE);
                numbersFactorized.add(new BigInteger(value.getS()));
                costs.put(new BigInteger(value.getS()),new BigInteger(cost.getS()));
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        BigInteger[] array = new BigInteger[numbersFactorized.size()];
        array = numbersFactorized.toArray(array);

        BigInteger estimatedCost = calculateEstimatedCost(array, estimate, costs);
        return estimatedCost;
    }

    public static BigInteger calculateEstimatedCost(BigInteger[] array, BigInteger val, HashMap<BigInteger, BigInteger> factorizedMetric){

        // Find nearest number factored key interval
        NavigableSet<BigInteger> values = new TreeSet<BigInteger>();
        for (BigInteger x : array) { values.add(x); }
        BigInteger l = values.floor(val);
        BigInteger h = values.ceiling(val);
        //return new int[]{lower, higher};
        BigDecimal value = new BigDecimal(val);

        BigDecimal finalCost = new BigDecimal(0);
        BigDecimal finalCostRounded = new BigDecimal(0);

        try{
            if(h == null || l == null) {

                if(h == null) {
                    finalCost = (value.multiply(new BigDecimal(factorizedMetric.get(l))).divide(new BigDecimal(l), DECIMAL_PlACES, RoundingMode.CEILING));
                } else {
                    finalCost = (value.multiply(new BigDecimal(factorizedMetric.get(h))).divide(new BigDecimal(h), DECIMAL_PlACES, RoundingMode.CEILING));
                }
            } else {

                BigDecimal lower = new BigDecimal(l);
                BigDecimal higher = new BigDecimal(h);

                // Proportions
                BigDecimal lowerProportion = BigDecimal.ONE.subtract((value.subtract(lower)).divide(higher.subtract(lower), DECIMAL_PlACES, RoundingMode.CEILING));
                BigDecimal higherProportion = BigDecimal.ONE.subtract((higher.subtract(value)).divide(higher.subtract(lower), DECIMAL_PlACES, RoundingMode.CEILING));
                finalCost = (lowerProportion.multiply(new BigDecimal(factorizedMetric.get(lower.toBigInteger()))).add(higherProportion.multiply(new BigDecimal(costs.get(higher.toBigInteger())))));
            }

            finalCostRounded = finalCost.setScale(0, BigDecimal.ROUND_HALF_UP);
        }catch (Exception e){
            e.printStackTrace();
        }

        return finalCostRounded.toBigInteger();
    }
}