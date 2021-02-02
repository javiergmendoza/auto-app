package com.javi.autoapp.ddb.model;

import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.ENABLED_ID;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.HASH_KEY;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.PASSWORD;
import static com.javi.autoapp.ddb.util.AutoAppDaoConstants.TOKEN_ID;
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
    private String username;

    @DynamoDBAttribute(attributeName = PASSWORD)
    private String password;

    @DynamoDBAttribute(attributeName = TOKEN_ID)
    private String token;

    @DynamoDBAttribute(attributeName = ENABLED_ID)
    private Boolean enabled;
}
