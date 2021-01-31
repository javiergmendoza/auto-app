package com.javi.autoapp.config;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_SETTINGS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PASSPHRASE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SECRET;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"development"})
public class DynamoDbConfigDevelop extends DynamoDbConfig {

    public DynamoDbConfigDevelop(AppConfig appConfig) {
        super(appConfig);
    }

    @Override
    @Bean(destroyMethod = "shutdown")
    public AmazonDynamoDB amazonDynamoDb() {
        return AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(endpointConfiguration()).build();
    }

    @Override
    @Bean(destroyMethod = "shutdown")
    public DynamoDB dynamoDb(AmazonDynamoDB amazonDynamoDb) throws InterruptedException {
        DynamoDB dynamoDB = new DynamoDB(amazonDynamoDb);
        CreateTableRequest request = new CreateTableRequest().withTableName(AUTO_APP_TABLE_NAME);
        Table table = dynamoDB.createTable(request);
        table.waitForActive();
        Item item = new Item()
                .withPrimaryKey(HASH_KEY, AUTO_APP_SETTINGS_ID)
                .withString(JOB_ID, AUTO_APP_SETTINGS_ID)
                .withString(PASSPHRASE, "test")
                .withString(KEY, "aea523c6d624c4b851ee8dd3ccba8a7f")
                .withString(SECRET, "5VDKJbiCVp8YOKHscjtnTLSDL06OwdUswaVIsZuCqZ79XdbGgZ+nCWx6ptJzaEGnk9KiyYuRVhyCzQRnoUBJvw==");
        table.putItem(item);
        return new DynamoDB(amazonDynamoDb);
    }

    @Override
    @Bean
    public DynamoDBMapper dynamoDbMapper(AmazonDynamoDB amazonDynamoDb) {
        return new DynamoDBMapper(amazonDynamoDb);
    }

    private AwsClientBuilder.EndpointConfiguration endpointConfiguration() {
        return new AwsClientBuilder.EndpointConfiguration(getAppConfig().getDynamoDbEndpoint(),
                getAppConfig().getAwsRegion());
    }
}

