package com.javi.autoapp.client;

import static com.javi.autoapp.util.CoinbasePathConstants.END_QUERY_PARAM;
import static com.javi.autoapp.util.CoinbasePathConstants.GET_ORDER_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.GET_STATS_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.GET_STATS_SLICES_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.GRANULARITY;
import static com.javi.autoapp.util.CoinbasePathConstants.GRANULARITY_QUERY_PARAM;
import static com.javi.autoapp.util.CoinbasePathConstants.POST_ORDER_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.START_QUERY_PARAM;

import com.javi.autoapp.config.AppConfig;
import com.javi.autoapp.util.SignatureTool;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CoinbaseTraderClient {
    private static final String RATE_LIMITER_NAME = "coinbaseClient";
    private static final String CB_ACCESS_PASSPHRASE = "CB-ACCESS-PASSPHRASE";
    private static final String CB_ACCESS_KEY = "CB-ACCESS-KEY";
    private static final String CB_ACCESS_SIGN = "CB-ACCESS-SIGN";
    private static final String CB_ACCESS_TIMESTAMP = "CB-ACCESS-TIMESTAMP";

    private final WebClient webClient;
    private final AppConfig appConfig;

    public CoinbaseTraderClient(AppConfig appConfig) {
        this.appConfig = appConfig;
        webClient = WebClient
                .builder()
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                }).build();
    }

    @RateLimiter(name = RATE_LIMITER_NAME)
    public Mono<ClientResponse> trade(
            String timestamp,
            String signature,
            String body) {
        return webClient.post()
                .uri(appConfig.getCoinbaseApiUri() + POST_ORDER_REQUEST_PATH)
                .headers(httpHeaders -> {
                    httpHeaders.set(CB_ACCESS_SIGN, signature);
                    httpHeaders.set(CB_ACCESS_TIMESTAMP, timestamp);
                    httpHeaders.set(CB_ACCESS_PASSPHRASE, SignatureTool.PASSPHRASE);
                    httpHeaders.set(CB_ACCESS_KEY, SignatureTool.KEY);
                }).body(Mono.just(body), String.class).exchange();
    }

    @RateLimiter(name = RATE_LIMITER_NAME)
    public Mono<ClientResponse> getOrderStatus(
            String timestamp,
            String signature,
            String id) {
        return webClient.get()
                .uri(appConfig.getCoinbaseApiUri() + GET_ORDER_REQUEST_PATH, id)
                .headers(httpHeaders -> {
                    httpHeaders.set(CB_ACCESS_SIGN, signature);
                    httpHeaders.set(CB_ACCESS_TIMESTAMP, timestamp);
                    httpHeaders.set(CB_ACCESS_PASSPHRASE, SignatureTool.PASSPHRASE);
                    httpHeaders.set(CB_ACCESS_KEY, SignatureTool.KEY);
                }).exchange();
    }

    @RateLimiter(name = RATE_LIMITER_NAME)
    public Mono<ClientResponse> getDayTradeStatus(
            String timestamp,
            String signature,
            String productId) {
        return webClient.get()
                .uri(appConfig.getCoinbaseApiUri() + GET_STATS_REQUEST_PATH, productId)
                .headers(httpHeaders -> {
                    httpHeaders.set(CB_ACCESS_SIGN, signature);
                    httpHeaders.set(CB_ACCESS_TIMESTAMP, timestamp);
                    httpHeaders.set(CB_ACCESS_PASSPHRASE, SignatureTool.PASSPHRASE);
                    httpHeaders.set(CB_ACCESS_KEY, SignatureTool.KEY);
                }).exchange();
    }

    @RateLimiter(name = RATE_LIMITER_NAME)
    public Mono<ClientResponse> getDayTradeStatusSlices(
            String start,
            String end,
            String timestamp,
            String signature,
            String productId) {
        String uri = UriComponentsBuilder.fromUriString(appConfig.getCoinbaseApiUri())
                .path(GET_STATS_SLICES_REQUEST_PATH)
                .queryParam(START_QUERY_PARAM, start)
                .queryParam(END_QUERY_PARAM, end)
                .queryParam(GRANULARITY_QUERY_PARAM, GRANULARITY)
                .buildAndExpand(productId)
                .toUriString();
        log.info("Status slices URI: {}", uri);
        return webClient.get()
                .uri(uri)
                .headers(httpHeaders -> {
                    httpHeaders.set(CB_ACCESS_SIGN, signature);
                    httpHeaders.set(CB_ACCESS_TIMESTAMP, timestamp);
                    httpHeaders.set(CB_ACCESS_PASSPHRASE, SignatureTool.PASSPHRASE);
                    httpHeaders.set(CB_ACCESS_KEY, SignatureTool.KEY);
                }).exchange();
    }
}
