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
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.Tables;


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
    
     

    static void init() throws Exception {

        System.out.println("init");
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
         AWSCredentials credentials = null;
         
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

        // HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        
        // scanFilter.put(attribute, condition);
        // ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
        // ScanResult scanResult = dynamoDB.scan(scanRequest);
        // System.out.println("Result: " + scanResult);
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
}
    