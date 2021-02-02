package com.javi.autoapp.config;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"test"})
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
    public DynamoDB dynamoDb(AmazonDynamoDB amazonDynamoDb) {
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

