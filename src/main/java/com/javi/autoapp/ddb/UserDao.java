package com.javi.autoapp.ddb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.javi.autoapp.ddb.model.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDao {
    private final DynamoDBMapper mapper;

    public Optional<User> findByUsername(String username) {
        User user = new User();
        user.setUsername(username);
        return Optional.ofNullable(mapper.load(user));
    }

    public void save(User user) {
        mapper.save(user);
    }
}
