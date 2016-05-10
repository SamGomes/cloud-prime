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
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.*;

import java.math.BigDecimal;
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
    private static final String TIME_TO_FACTORIZE_ATTRIBUTE = "timeToFactorize";
    private static final String INSTANCE_PRIMARY_KEY = "instanceId";
    private static final String INSTANCE_LOAD_TABLE_NAME = "MSS Instance Load";
    private static int ESTIMATED_COST = 1;
    private static int DIRECT_COST = 2;
    private static int DECIMAL_PlACES = 6;

    // numberToFactorized : cost
    static HashMap<BigDecimal, BigDecimal> costs = new HashMap<>();

    // numberToFactorized : estimatedTime
    static HashMap<BigDecimal, BigDecimal> reqTimes = new HashMap<>();

    private static Table table;
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

    static void insertTuple(String tableName,String[] attrAndValues) throws Exception {


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

    static Map<String,AttributeValue> getInstanceTuple(String tableName,String instance) throws Exception {

        Map<String, AttributeValue> instanceLoadTuple = null;

        Condition hashKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(instance));

        Map<String, Condition> keyConditions = new HashMap<>();
        keyConditions.put(INSTANCE_PRIMARY_KEY, hashKeyCondition);
        //keyConditions.put("cost", rangeKeyCondition);

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


    static BigDecimal[] estimateCostScan(BigDecimal estimate){

        ArrayList<BigDecimal> numbersFactorized = new ArrayList<>();

        try{

            BigDecimal numberCostTuple;
            BigDecimal numberReqTimeTuple;

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
                for (Map<String,AttributeValue> item: result.getItems()){
                    numberCostTuple = new BigDecimal(item.get(COST_ATTRIBUTE).getS());
                    numberReqTimeTuple = new BigDecimal(item.get(TIME_TO_FACTORIZE_ATTRIBUTE).getS());
                    return new BigDecimal[]{
                            numberCostTuple,
                            numberReqTimeTuple
                    };
                }
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

            for (Map<String, AttributeValue> item : lowerClosestValue.getItems()){
                AttributeValue value = item.get(PRIMARY_KEY);
                AttributeValue cost = item.get(COST_ATTRIBUTE);
                AttributeValue reqTime = item.get(TIME_TO_FACTORIZE_ATTRIBUTE);
                numbersFactorized.add(new BigDecimal(value.getS()));
                costs.put(new BigDecimal(value.getS()),new BigDecimal(cost.getS()));
                reqTimes.put(new BigDecimal(value.getS()),new BigDecimal(reqTime.getS()));
            }

            for (Map<String, AttributeValue> item : higherClosestValue.getItems()){
                AttributeValue value = item.get(PRIMARY_KEY);
                AttributeValue cost = item.get(COST_ATTRIBUTE);
                AttributeValue reqTime = item.get(TIME_TO_FACTORIZE_ATTRIBUTE);
                numbersFactorized.add(new BigDecimal(value.getS()));
                costs.put(new BigDecimal(value.getS()),new BigDecimal(cost.getS()));
                reqTimes.put(new BigDecimal(value.getS()),new BigDecimal(reqTime.getS()));
            }
        }catch (Exception e){
            if (e.getCause() instanceof ProvisionedThroughputExceededException){
                UpdateTableRequest updateTableRequest = new UpdateTableRequest()
                        .withTableName(TABLE_NAME)
                        .withProvisionedThroughput(new ProvisionedThroughput().
                                withReadCapacityUnits((long) 6).
                                withWriteCapacityUnits((long) 6));

                dynamoDB.updateTable(updateTableRequest);
            }
            e.printStackTrace();
        }

        BigDecimal[] array = new BigDecimal[numbersFactorized.size()];
        array = numbersFactorized.toArray(array);

        BigDecimal estimatedCost = calculateEstimatedCost(array, estimate, costs);
        BigDecimal estimatedReqTime = calculateEstimatedCost(array, estimate, reqTimes);

        return  new BigDecimal[]{
                estimatedCost,
                estimatedReqTime
        };
    }

    public static BigDecimal calculateEstimatedCost(BigDecimal[] array, BigDecimal val, HashMap<BigDecimal, BigDecimal> factorizedMetric){

        // Find nearest number factored key interval
        NavigableSet<BigDecimal> values = new TreeSet<BigDecimal>();
        for (BigDecimal x : array) { values.add(x); }
        BigDecimal l = values.floor(val);
        BigDecimal h = values.ceiling(val);
        //return new int[]{lower, higher};
        BigDecimal value = val;

        BigDecimal finalCost = new BigDecimal(0);
        BigDecimal finalCostRounded = new BigDecimal(0);

        try{
            if(h == null && l == null){
                finalCostRounded = new BigDecimal("0");
            }else{
                if(h == null || l == null) {

                    if(h == null) {
                        finalCost = (value.multiply(factorizedMetric.get(l)).divide(l, DECIMAL_PlACES, RoundingMode.CEILING));
                    } else {
                        finalCost = (value.multiply(factorizedMetric.get(h)).divide(h, DECIMAL_PlACES, RoundingMode.CEILING));
                    }
                } else {

                    BigDecimal lower = l;
                    BigDecimal higher = h;

                    // Proportions
                    BigDecimal lowerProportion = BigDecimal.ONE.subtract((value.subtract(lower)).divide(higher.subtract(lower), DECIMAL_PlACES, RoundingMode.CEILING));
                    BigDecimal higherProportion = BigDecimal.ONE.subtract((higher.subtract(value)).divide(higher.subtract(lower), DECIMAL_PlACES, RoundingMode.CEILING));
                    finalCost = (lowerProportion.multiply(factorizedMetric.get(lower)).add(higherProportion.multiply(costs.get(higher))));
                }

                finalCostRounded = finalCost.setScale(0, BigDecimal.ROUND_HALF_UP);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return finalCostRounded;
    }
}
    