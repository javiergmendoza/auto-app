package com.javi.autoapp.client;

import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import com.javi.autoapp.client.model.CoinbaseOrderResponse;
import com.javi.autoapp.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CoinbaseTraderClient {
    private static final String CB_ACCESS_PASSPHRASE = "CB-ACCESS-PASSPHRASE";
    private static final String CB_ACCESS_KEY = "CB-ACCESS-KEY";
    private static final String CB_ACCESS_SIGN = "CB-ACCESS-SIGN";
    private static final String CB_ACCESS_TIMESTAMP = "CB-ACCESS-TIMESTAMP";

    private final WebClient webClient;

    public CoinbaseTraderClient(AppConfig appConfig) {
        webClient = WebClient
                .builder()
                .baseUrl(appConfig.getCoinbaseApiUri())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<ClientResponse> trade(
            String passphrase,
            String key,
            String timestamp,
            String signature,
            String order) {
        return webClient.post()
                .header(CB_ACCESS_SIGN, signature)
                .header(CB_ACCESS_TIMESTAMP, timestamp)
                .header(CB_ACCESS_PASSPHRASE, passphrase)
                .header(CB_ACCESS_KEY, key)
                .body(Mono.just(order), String.class)
                .exchange();
    }
}
