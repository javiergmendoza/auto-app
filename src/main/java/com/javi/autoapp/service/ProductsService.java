package com.javi.autoapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseTicker;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.model.ProductPriceSegment;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProductsService implements MessageHandler.Whole<CoinbaseTicker> {
    public static final int MAX_SEGMENTS = 5;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Session userSession;
    private final Set<String> activeFeeds;
    private final Map<String, String> pricesMap;
    private final Map<String, Deque<ProductPriceSegment>> priceSegments15Map;

    public ProductsService(
            Session session,
            CoinbaseTraderClient coinbaseTraderClient
    ) {
        this.activeFeeds = Collections.synchronizedSet(new HashSet<>());
        activeFeeds.add(Currency.BTC.getLabel());
        this.priceSegments15Map = new ConcurrentHashMap<>();
        this.pricesMap = new ConcurrentHashMap<>();

        userSession = session;
        userSession.addMessageHandler(this);
    }

    public Optional<String> getPrice(String productId) {
        return Optional.ofNullable(pricesMap.get(productId));
    }

    public Optional<Deque<ProductPriceSegment>> getPriceSegment15(String productId) {
        return Optional.ofNullable(priceSegments15Map.get(productId));
    }

    public Set<String> getActiveFeeds() {
        return activeFeeds;
    }

    public void clearStaleData() {
        activeFeeds.clear();
    }

    @Override
    public void onMessage(CoinbaseTicker message) {
        if (message.getProductId() == null || message.getPrice() == null) {
            return;
        }

        activeFeeds.add(message.getProductId());

        pricesMap.put(message.getProductId(), message.getPrice());

        Deque<ProductPriceSegment> priceSegments15Queue = priceSegments15Map.get(message.getProductId());
        if (priceSegments15Queue == null) {
            priceSegments15Queue = new ConcurrentLinkedDeque<>();
        }

        ProductPriceSegment fistPriceSegment = priceSegments15Queue.pollFirst();
        if (fistPriceSegment == null || Instant.now().minus(Duration.ofMinutes(15)).isAfter(fistPriceSegment.getTimestamp())) {
            log.info("Creating a new 15 minute price segment for {}", message.getProductId());
            if (fistPriceSegment != null) {
                priceSegments15Queue.addFirst(fistPriceSegment);
            }
            ProductPriceSegment newPriceSegment = new ProductPriceSegment();
            newPriceSegment.aggregatePrice(message.getPrice());
            priceSegments15Queue.addFirst(newPriceSegment);
        } else {
            fistPriceSegment.aggregatePrice(message.getPrice());
            priceSegments15Queue.addFirst(fistPriceSegment);
        }

        if (priceSegments15Queue.size() > MAX_SEGMENTS) {
            priceSegments15Queue.removeLast();
        }
        priceSegments15Map.put(message.getProductId(), priceSegments15Queue);
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
            productIds.forEach(priceSegments15Map::remove);
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
    }
}
