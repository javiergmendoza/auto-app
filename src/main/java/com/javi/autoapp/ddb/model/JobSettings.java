package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_MAX_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CROSSED_MIN_THRESHOLD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CURRENCY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.EXPIRES;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.FROZEN;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.FUNDS;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_SETTINGS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MAX;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MAX_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MIN;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MIN_VALUE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SELL;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SIZE;

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

    @DynamoDBAttribute(attributeName = CURRENCY)
    private String currency = "";

    @DynamoDBAttribute(attributeName = SELL)
    private boolean sell = true;

    @DynamoDBAttribute(attributeName = MAX)
    private double max = 0.0;

    @DynamoDBAttribute(attributeName = MIN)
    private double min = 0.0;

    @DynamoDBAttribute(attributeName = FUNDS)
    private double funds = 0.0;

    @DynamoDBAttribute(attributeName = EXPIRES)
    private String expires = "";

    @DynamoDBAttribute(attributeName = CROSSED_MIN_THRESHOLD)
    private boolean crossedMinThreshold = false;

    @DynamoDBAttribute(attributeName = MIN_VALUE)
    private double minValue = 0.0;

    @DynamoDBAttribute(attributeName = CROSSED_MAX_THRESHOLD)
    private boolean crossedMaxThreshold = false;

    @DynamoDBAttribute(attributeName = MAX_VALUE)
    private double maxValue = 0.0;

    @DynamoDBAttribute(attributeName = SIZE)
    private double size = 0.0;

    @DynamoDBAttribute(attributeName = FROZEN)
    private boolean active = true;
}
