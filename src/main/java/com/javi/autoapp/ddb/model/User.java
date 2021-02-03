package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.USERNAME;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.USER_TABLE_NAME;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Data;
import lombok.NoArgsConstructor;

@DynamoDBTable(tableName = USER_TABLE_NAME)
@Data
@NoArgsConstructor
public class User {

    @DynamoDBHashKey(attributeName = HASH_KEY)
    private String id;

    @DynamoDBAttribute(attributeName = USERNAME)
    private String username;
}
