package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_MAX_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_MIN_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CURRENCY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CURRENT_FUNDS_USD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.GAINS_LOSSES;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_STATUS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MAX_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MIN_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SIZE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.STARTING_FUNDS_USD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.STATUS;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
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
    private final String id = JOB_STATUS_ID;

    @DynamoDBAttribute(attributeName = JOB_ID)
    private String jobId;

    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName = STATUS)
    private Status status;

    @DynamoDBAttribute(attributeName = GAINS_LOSSES)
    private double gainsLosses;

    @DynamoDBAttribute(attributeName = CURRENT_FUNDS_USD)
    private double currentFundsUsd;

    @DynamoDBAttribute(attributeName = STARTING_FUNDS_USD)
    private double startingFundsUsd;

    @DynamoDBAttribute(attributeName = CROSSED_MIN_THRESHOLD)
    private boolean crossedMinThreshold;

    @DynamoDBAttribute(attributeName = MIN_VALUE)
    private Double minValue;

    @DynamoDBAttribute(attributeName = CROSSED_MAX_THRESHOLD)
    private boolean crossedMaxThreshold;

    @DynamoDBAttribute(attributeName = MAX_VALUE)
    private Double maxValue;

    @DynamoDBAttribute(attributeName = CURRENCY)
    private Currency currency;

    @DynamoDBAttribute(attributeName = SIZE)
    private Double size;
}
