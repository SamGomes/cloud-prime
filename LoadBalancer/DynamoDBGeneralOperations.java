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
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.Tables;

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
    private static final String INSTANCE_LOAD_TABLE_NAME = "MSS Instance Load";
    private static int ESTIMATED_COST = 1;
    private static int DIRECT_COST = 2;
    private static int DECIMAL_PlACES = 6;
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

    static void createTable(String tableName,String keyAttr,String[] attributes) throws Exception {

        System.out.print("createTable! tableName: "+tableName+"  ,keyAttr: "+keyAttr+" attributes: ");

        for(int i=0;i<attributes.length;i++){
            System.out.print(" "+attributes[i]+" ,");
        }
        System.out.print("\n");

        // Create table if it does not exist yet

        if (Tables.doesTableExist(dynamoDB, tableName)) {
            System.out.println("Table " + tableName + " is already ACTIVE");
        } else {
            // Create a table with a primary hash key named 'name', which holds a string
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                    .withKeySchema(new KeySchemaElement[]{new KeySchemaElement().withAttributeName(keyAttr).withKeyType(KeyType.HASH)})
                    .withAttributeDefinitions(new AttributeDefinition[]{new AttributeDefinition().withAttributeName(keyAttr).withAttributeType(ScalarAttributeType.S)})
                    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(new Long(1)).withWriteCapacityUnits(new Long(1)));
            TableDescription createdTableDescription = dynamoDB.createTable(createTableRequest).getTableDescription();
            System.out.println("Created Table: " + createdTableDescription);

            // Wait for it to become active

            System.out.println("Waiting for " + tableName + " to become ACTIVE...");
            Tables.awaitTableToBecomeActive(dynamoDB, tableName);
        }

    }

    static void describeTable(String tableName) throws Exception {

        System.out.println("describeTable tableName"+tableName);

        // DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
        // TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
        // System.out.println("Table Description: " + tableDescription);
    }

    static void queryTable(String tableName,String attribute,Condition condition) throws Exception {

        System.out.println("createTable! tableName: "+tableName+"  ,condition: "+condition+" attribute: "+attribute);

        HashMap scanFilter = new HashMap();

        scanFilter.put(attribute, condition);
        ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        System.out.println("Result: " + scanResult);
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

    static BigInteger estimateCost(BigInteger estimate){

        //TODO: Query with < and > operators and scan index forward

        BigInteger response = BigInteger.valueOf(0);

        Map<String, AttributeValue> lastEvaluatedKey = null;
        do {

            try{
                Condition hashKeyCondition = new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ.toString())
                        .withAttributeValueList(new AttributeValue().withS(estimate.toString()));

                Map<String, Condition> keyConditions = new HashMap<>();
                keyConditions.put("numberToBeFactored", hashKeyCondition);
                //keyConditions.put("cost", rangeKeyCondition);

                QueryRequest queryRequest = new QueryRequest()
                        .withTableName(TABLE_NAME)
                        .withKeyConditions(keyConditions)
                        .withLimit(2);

                QueryResult result = dynamoDB.query(queryRequest);
                for (Map<String, AttributeValue> item : result.getItems()) {
                    AttributeValue value = item.get(PRIMARY_KEY);
                    AttributeValue cost = item.get(COST_ATTRIBUTE);
                    System.out.println(value.toString());
                    System.out.println(cost.toString());
                }
                lastEvaluatedKey = result.getLastEvaluatedKey();
            }catch (Exception e){
                e.printStackTrace();
            }
        } while (lastEvaluatedKey != null);

        return response;
    }

    static BigInteger estimateCostScan(BigInteger estimate){

        ArrayList<BigInteger> numbersFactorized = new ArrayList<>();

        try{

            Map<String, AttributeValue> expressionAttributeValues =
                    new HashMap<>();
            expressionAttributeValues.put(":val", new AttributeValue().withS(estimate.toString()));

            ScanRequest scanRequest = new ScanRequest()
                    .withTableName(TABLE_NAME)
                    .withFilterExpression("numberToBeFactored = :val")
                    .withExpressionAttributeValues(expressionAttributeValues);

            ScanResult equalityValue = dynamoDB.scan(scanRequest);

            List<Map<String,AttributeValue>> listValues = equalityValue.getItems();

            if(listValues.size()!=0){
                return new BigInteger(listValues.get(0).get(COST_ATTRIBUTE).getS());
            }



            Map<String, AttributeValue> expressionLowerAttributeValues =
                    new HashMap<>();
            expressionLowerAttributeValues.put(":val", new AttributeValue().withS(estimate.toString()));

            Map<String, AttributeValue> expressionHigherAttributeValues =
                    new HashMap<>();
            expressionHigherAttributeValues.put(":val", new AttributeValue().withS(estimate.toString()));

            ScanRequest scanLowerRequest = new ScanRequest()
                    .withTableName(TABLE_NAME)
                    .withFilterExpression("numberToBeFactored < :val")
                    .withExpressionAttributeValues(expressionAttributeValues);

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
        BigInteger result = calculateEstimatedCost(array, estimate);
        return result;
    }

    public static BigInteger calculateEstimatedCost(BigInteger[] array, BigInteger val){

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
                    finalCost = (value.multiply(new BigDecimal(costs.get(l))).divide(new BigDecimal(l), DECIMAL_PlACES, RoundingMode.CEILING));
                } else {
                    finalCost = (value.multiply(new BigDecimal(costs.get(h))).divide(new BigDecimal(h), DECIMAL_PlACES, RoundingMode.CEILING));
                }
            } else {

                BigDecimal lower = new BigDecimal(l);
                BigDecimal higher = new BigDecimal(h);

                // Proportions
                BigDecimal lowerProportion = BigDecimal.ONE.subtract((value.subtract(lower)).divide(higher.subtract(lower), DECIMAL_PlACES, RoundingMode.CEILING));
                BigDecimal higherProportion = BigDecimal.ONE.subtract((higher.subtract(value)).divide(higher.subtract(lower), DECIMAL_PlACES, RoundingMode.CEILING));
                finalCost = (lowerProportion.multiply(new BigDecimal(costs.get(lower.toBigInteger()))).add(higherProportion.multiply(new BigDecimal(costs.get(higher.toBigInteger())))));
            }

            finalCostRounded = finalCost.setScale(0, BigDecimal.ROUND_HALF_UP);
        }catch (Exception e){
            e.printStackTrace();
        }

        return finalCostRounded.toBigInteger();
    }
}
    