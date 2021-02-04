package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.ACTIVE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_FLOOR;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_YIELD_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.EXPIRES;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.FUNDS;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.INIT;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_SETTINGS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PERCENTAGE_YIELD_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MAX_PERCENTAGE_YIELD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.FLOOR;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MIN_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PENDING;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PRECISION_FROM_CENT;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PRODUCT_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SELL;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SIZE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.STARTING_FUNDS_USD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.TOTAL_PERCENTAGE_YIELD_THRESHOLD;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@DynamoDBTable(tableName = AUTO_APP_TABLE_NAME)
@Data
@NoArgsConstructor
public class JobSettings {

    @DynamoDBHashKey(attributeName = HASH_KEY)
    private String id = JOB_SETTINGS_ID;

    @DynamoDBRangeKey(attributeName = JOB_ID)
    private String jobId = UUID.randomUUID().toString();

    @DynamoDBAttribute(attributeName = PRODUCT_ID)
    private String productId = "";

    @DynamoDBAttribute(attributeName = SELL)
    private boolean sell = false;

    @DynamoDBAttribute(attributeName = PRECISION_FROM_CENT)
    private int precision = 0;

    @DynamoDBAttribute(attributeName = PERCENTAGE_YIELD_THRESHOLD)
    private double percentageYieldThreshold = 1.1;

    @DynamoDBAttribute(attributeName = TOTAL_PERCENTAGE_YIELD_THRESHOLD)
    private double totalPercentageYieldThreshold = 10.0;

    @DynamoDBAttribute(attributeName = FLOOR)
    private double floor = 0.0;

    @DynamoDBAttribute(attributeName = FUNDS)
    private double funds = 0.0;

    @DynamoDBAttribute(attributeName = STARTING_FUNDS_USD)
    private double startingFundsUsd = 0.0;

    @DynamoDBAttribute(attributeName = EXPIRES)
    private String expires = "";

    @DynamoDBAttribute(attributeName = MAX_PERCENTAGE_YIELD)
    private double maxPercentageYield = 0.0;

    @DynamoDBAttribute(attributeName = CROSSED_YIELD_THRESHOLD)
    private boolean crossedYieldThreshold = false;

    @DynamoDBAttribute(attributeName = MIN_VALUE)
    private double minValue = 0.0;

    @DynamoDBAttribute(attributeName = CROSSED_FLOOR)
    private boolean crossedFloor = false;

    @DynamoDBAttribute(attributeName = SIZE)
    private double size = 0.0;

    @DynamoDBAttribute(attributeName = ACTIVE)
    private boolean active = true;

    @DynamoDBAttribute(attributeName = PENDING)
    private boolean pending = false;

    @DynamoDBAttribute(attributeName = INIT)
    private boolean init = true;
}
