package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_SETTINGS_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.AUTO_APP_TABLE_NAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.JOB_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PASSPHRASE;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.SECRET;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Data;
import lombok.NoArgsConstructor;

@DynamoDBTable(tableName = AUTO_APP_TABLE_NAME)
@Data
@NoArgsConstructor
public class AutoAppSettings {

    @DynamoDBHashKey(attributeName = HASH_KEY)
    private String id = AUTO_APP_SETTINGS_ID;

    @DynamoDBRangeKey(attributeName = JOB_ID)
    private String jobId = AUTO_APP_SETTINGS_ID;

    @DynamoDBAttribute(attributeName = KEY)
    private String key;

    @DynamoDBAttribute(attributeName = PASSPHRASE)
    private String passphrase;

    @DynamoDBAttribute(attributeName = SECRET)
    private String secret;
}
