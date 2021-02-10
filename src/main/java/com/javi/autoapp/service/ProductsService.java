package com.javi.autoapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseStatsResponse;
import com.javi.autoapp.client.model.CoinbaseTicker;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.util.SignatureTool;
import io.netty.handler.codec.http.HttpMethod;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@CacheConfig(cacheNames = {"coinbaseApi"})
public class ProductsService implements MessageHandler.Whole<CoinbaseTicker> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Session userSession;
    private final Set<String> activeFeeds;
    private final Map<String, String> pricesMap;
    private final CoinbaseTraderClient coinbaseTraderClient;

    public ProductsService(
            CacheManager cacheManager,
            Session session,
            CoinbaseTraderClient coinbaseTraderClient,
            AutoAppDao autoAppDao
    ) {
        this.coinbaseTraderClient = coinbaseTraderClient;
        this.activeFeeds = Collections.synchronizedSet(new HashSet<>());
        activeFeeds.add(Currency.BTC.getLabel());
        this.pricesMap = new ConcurrentHashMap<>();

        userSession = session;
        userSession.addMessageHandler(this);
    }

    public Optional<String> getPrice(String productId) {
        return Optional.ofNullable(pricesMap.get(productId));
    }

    public Set<String> getActiveFeeds() {
        return activeFeeds;
    }

    @Override
    public void onMessage(CoinbaseTicker message) {
        if (message.getProductId() == null || message.getPrice() == null) {
            return;
        }
        activeFeeds.add(message.getProductId());
        pricesMap.put(message.getProductId(), message.getPrice());
    }

    public void updateSubscribedCurrencies(Set<String> productIds) throws JsonProcessingException {
        if (productIds.equals(activeFeeds)) {
            return; // Nothing to update
        }

        // Only unsubscribe if there is anything to unsubscribe to
        if (!activeFeeds.isEmpty()) {
            log.info("Clearing currency ticker feeds.");
            CoinbaseWebSocketSubscribe unsubscribe = new CoinbaseWebSocketSubscribe();
            unsubscribe.setType(CoinbaseWebSocketSubscribe.UNSUBSCRIBE);
            unsubscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
            unsubscribe.setProductIds(new ArrayList<>(activeFeeds));
            userSession.getAsyncRemote().sendText(mapper.writeValueAsString(unsubscribe));
        }

        // Subscribe to required product IDs
        if (!productIds.isEmpty()) {
            log.info("Subscribing to the following currency feeds: {}", productIds.toString());
            CoinbaseWebSocketSubscribe subscribe = new CoinbaseWebSocketSubscribe();
            subscribe.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
            subscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
            subscribe.setProductIds(new ArrayList<>(productIds));
            userSession.getAsyncRemote().sendText(mapper.writeValueAsString(subscribe));
        } else {
            CoinbaseWebSocketSubscribe subscribe = new CoinbaseWebSocketSubscribe();
            subscribe.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
            subscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
            subscribe.setProductIds(Collections.singletonList(Currency.BTC.getLabel()));
            userSession.getAsyncRemote().sendText(mapper.writeValueAsString(subscribe));
        }

        // Clear to prevent storing stale data
        pricesMap.clear();
        activeFeeds.clear();
    }

    @Cacheable
    public CoinbaseStatsResponse getProductStats(String productId)
            throws InvalidKeyException, NoSuchAlgorithmException {
        log.info("Cache miss. Pulling 24 hour stats for {}", productId);
        ClientResponse response = updateCurrencyStats(productId).block();
        if (response.statusCode().isError()) {
            response.bodyToMono(String.class).subscribe(error -> log.error("Failed to get order status. Error: {}", error));
            return null;
        } else {
            return response.bodyToMono(CoinbaseStatsResponse.class).block();
        }
    }

    private Mono<ClientResponse> updateCurrencyStats(String productId)
            throws NoSuchAlgorithmException, InvalidKeyException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = SignatureTool.getSignature(
                timestamp,
                HttpMethod.GET.name(),
                SignatureTool.getStatsRequestPath(productId));
        return coinbaseTraderClient.getDayTradeStatus(timestamp, signature, productId);
    }
}
