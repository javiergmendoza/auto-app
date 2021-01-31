package com.javi.autoapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.javi.autoapp.client.CoinbaseTickerWebSocket;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@EnableCaching
@Configuration
public class AppConfig {

    @Value("${coinbaseWebSocketUri}")
    private String coinbaseWebSocketUri;

    @Value("${coinbaseApiUri}")
    private String coinbaseApiUri;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.dynamodb.endpoint:null}")
    private String dynamoDbEndpoint;

    @Bean
    public Session startWebSocket() throws IOException, DeploymentException {
        WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
        return webSocketContainer.connectToServer(
                CoinbaseTickerWebSocket.class,
                URI.create(coinbaseWebSocketUri)
        );
    }

    @Bean
    public Caffeine caffeineConfig() {
        return Caffeine.newBuilder()
                .weakKeys()
                .expireAfterWrite(60, TimeUnit.MINUTES);
    }

    @Bean
    public CacheManager cacheManager(Caffeine caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager("autoAppConfigDao");
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }
}
