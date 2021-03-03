package com.javi.autoapp.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Data
@EnableCaching
@Configuration
public class AppConfig {

    @Value("${coinbaseWebSocketUri}")
    private String coinbaseWebSocketUri;

    @Value("${coinbaseApiUri}")
    private String coinbaseApiUri;
}
