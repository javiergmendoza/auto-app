package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CURRENCY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CURRENT_FUNDS_USD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CURRENT_VALUE_USD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.GAINS_LOSSES;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_STATUS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PRICE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SIZE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.STARTING_FUNDS_USD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.STATUS;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.graphql.type.Status;
import lombok.Data;
import lombok.NoArgsConstructor;

@DynamoDBTable(tableName = AUTO_APP_TABLE_NAME)
@Data
@NoArgsConstructor
public class JobStatus {

    @DynamoDBHashKey(attributeName = HASH_KEY)
    private String id = JOB_STATUS_ID;

    @DynamoDBRangeKey(attributeName = JOB_ID)
    private String jobId;

    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName = STATUS)
    private Status status = Status.PENDING;

    @DynamoDBAttribute(attributeName = GAINS_LOSSES)
    private double gainsLosses = 0.0;

    @DynamoDBAttribute(attributeName = CURRENT_VALUE_USD)
    private double currentValueUsd = 0.0;

    @DynamoDBAttribute(attributeName = CURRENT_FUNDS_USD)
    private double currentFundsUsd = 0.0;

    @DynamoDBAttribute(attributeName = STARTING_FUNDS_USD)
    private double startingFundsUsd = 0.0;

    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName = CURRENCY)
    private Currency currency;

    @DynamoDBAttribute(attributeName = SIZE)
    private double size = 0.0;

    @DynamoDBAttribute(attributeName = PRICE)
    private double price = 0.0;
}
