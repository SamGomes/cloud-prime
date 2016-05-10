
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
import com.amazonaws.services.dynamodbv2.util.Tables;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;


public class DynamoDBWebServerGeneralOperations {

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
    private static final String INSTACE_LOAD_TABLE_NAME = "MSS Instance Load";
    private static final String INSTANCE_PRIMARY_KEY = "instanceId";

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

    static ItemCollection<QueryOutcome> queryTable(String tableName, String var, String val) throws Exception {

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
                .withTableName(TABLE_NAME)
                .withKeyConditions(keyConditions)
                .withLimit(1);

        QueryResult result = dynamoDB.query(queryRequest);
        if (result.getCount() > 0){
            instanceLoadTuple = result.getItems().get(0);
        }
        return instanceLoadTuple;
    }

    static int estimateCost(BigInteger estimate){

        Map<String, AttributeValue> expressionAttributeValues =
                new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withN(String.valueOf(estimate)));

        Map<String, AttributeValue> expressionHigherAttributeValues =
                new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withN(String.valueOf(estimate)));

        QueryRequest queryRequest = new QueryRequest()
                .withFilterExpression(PRIMARY_KEY+" < :val")
                .withScanIndexForward(false)
                .withExpressionAttributeValues(expressionAttributeValues)
                .withConsistentRead(true)
                .withLimit(10);

        QueryResult result = dynamoDB.query(queryRequest);

        QueryRequest queryHigherRequest = new QueryRequest()
                .withFilterExpression(PRIMARY_KEY+" > :val")
                .withScanIndexForward(false)
                .withExpressionAttributeValues(expressionHigherAttributeValues)
                .withConsistentRead(true)
                .withLimit(10);

        QueryResult higherResult = dynamoDB.query(queryHigherRequest);

        for (Map<String, AttributeValue> item : result.getItems()) {
            for (String key: item.keySet()){
                System.out.println(key);
            }
        }

        for (Map<String, AttributeValue> item : higherResult.getItems()) {
            for (String key: item.keySet()){
                System.out.println(key);
            }
        }

        return 0;
    }
}
    