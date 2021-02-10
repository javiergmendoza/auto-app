package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.ACTIVE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_HIGH_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_LOW_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_YIELD_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.EXPIRES;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.FUNDS;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.INCREASE_FUNDS_BY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.INIT;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_SETTINGS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MAX_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MAX_YIELD_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MIN_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PERCENTAGE_YIELD_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PENDING;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PRECISION_FROM_CENT;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PRODUCT_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SELL;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SIZE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.STARTING_FUNDS_USD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.TOTAL_PERCENTAGE_YIELD_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.TRADE_NOW;

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

    @DynamoDBAttribute(attributeName = FUNDS)
    private double funds = 0.0;

    @DynamoDBAttribute(attributeName = STARTING_FUNDS_USD)
    private double startingFundsUsd = 0.0;

    @DynamoDBAttribute(attributeName = EXPIRES)
    private String expires = "";

    @DynamoDBAttribute(attributeName = MAX_VALUE)
    private double maxValue = 0.0;

    @DynamoDBAttribute(attributeName = CROSSED_HIGH_THRESHOLD)
    private boolean crossedHighThreshold = false;

    @DynamoDBAttribute(attributeName = MIN_VALUE)
    private double minValue = 0.0;

    @DynamoDBAttribute(attributeName = CROSSED_LOW_THRESHOLD)
    private boolean crossedLowThreshold = false;

    @DynamoDBAttribute(attributeName = MAX_YIELD_VALUE)
    private double maxYieldValue = 0.0;

    @DynamoDBAttribute(attributeName = CROSSED_YIELD_THRESHOLD)
    private boolean crossedPercentageYieldThreshold = false;

    @DynamoDBAttribute(attributeName = SIZE)
    private double size = 0.0;

    @DynamoDBAttribute(attributeName = INCREASE_FUNDS_BY)
    private double increaseFundsBy = 0.0;

    @DynamoDBAttribute(attributeName = ACTIVE)
    private boolean active = true;

    @DynamoDBAttribute(attributeName = PENDING)
    private boolean pending = false;

    @DynamoDBAttribute(attributeName = TRADE_NOW)
    private boolean tradeNow = false;

    @DynamoDBAttribute(attributeName = INIT)
    private boolean init = true;
}
