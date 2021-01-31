package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.CURRENCY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.EXPIRES;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.FUNDS;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_SETTINGS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MAX;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.MIN;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Data;
import lombok.NoArgsConstructor;

@DynamoDBTable(tableName = AUTO_APP_TABLE_NAME)
@Data
@NoArgsConstructor
public class JobSettings {

    @DynamoDBHashKey(attributeName = HASH_KEY)
    private final String id = JOB_SETTINGS_ID;

    @DynamoDBAttribute(attributeName = JOB_ID)
    private String jobId;

    @DynamoDBAttribute(attributeName = CURRENCY)
    private String currency;

    @DynamoDBAttribute(attributeName = MAX)
    private Double max;

    @DynamoDBAttribute(attributeName = MIN)
    private Double min;

    @DynamoDBAttribute(attributeName = FUNDS)
    private Double funds;

    @DynamoDBAttribute(attributeName = EXPIRES)
    private String expires;
}
