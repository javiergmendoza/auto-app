package com.javi.autoapp.config;

import com.javi.autoapp.client.CoinbaseTickerWebSocket;
import java.io.IOException;
import java.net.URI;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
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

    @Bean
    public Session startWebSocket() throws IOException, DeploymentException {
        WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
        return webSocketContainer.connectToServer(
                CoinbaseTickerWebSocket.class,
                URI.create(coinbaseWebSocketUri)
        );
    }
}
