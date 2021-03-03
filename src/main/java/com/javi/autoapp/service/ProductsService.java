package com.javi.autoapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTickerWebSocket;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseStatsResponse;
import com.javi.autoapp.client.model.CoinbaseTicker;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.config.AppConfig;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.model.ProductPriceSegment;
import com.javi.autoapp.util.SignatureTool;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ProductsService implements MessageHandler.Whole<CoinbaseTicker> {
    public static final int MIN_SEGMENTS = 5;
    public static final int MAX_SEGMENTS = 35;

    private static final double CHANGE_LOW_THRESHOLD = 0.995;
    private static final double CHANGE_HIGH_THRESHOLD = 1.005;

    private final ObjectMapper mapper = new ObjectMapper();
    private final CoinbaseTraderClient coinbaseTraderClient;
    private final AppConfig appConfig;
    private Session session;
    private final Set<String> activeFeeds;
    private final Map<String, String> pricesMap;
    private final Map<String, String> previousPricesMap;
    private final Map<String, Deque<ProductPriceSegment>> priceSegments15Map;

    public ProductsService(AppConfig appConfig, CoinbaseTraderClient coinbaseTraderClient) {
        this.appConfig = appConfig;
        this.coinbaseTraderClient = coinbaseTraderClient;
        this.activeFeeds = Collections.synchronizedSet(new HashSet<>());
        activeFeeds.add(Currency.BTC.getLabel());
        this.priceSegments15Map = new ConcurrentHashMap<>();
        this.pricesMap = new ConcurrentHashMap<>();
        this.previousPricesMap = new ConcurrentHashMap<>();
    }

    public OptionalDouble getPrice(String productId) {
        String priceString = pricesMap.get(productId);

        if (priceString == null) {
            return OptionalDouble.empty();
        }

        try {
            String previousPriceString = previousPricesMap.get(productId);
            if (previousPriceString == null) {
                previousPricesMap.put(productId, priceString);
                return OptionalDouble.of(Double.parseDouble(priceString));
            } else {
                double previousPrice = Double.parseDouble(previousPriceString);
                double price = Double.parseDouble(priceString);
                double priceDifference = price / previousPrice;
                if (priceDifference > CHANGE_LOW_THRESHOLD && priceDifference < CHANGE_HIGH_THRESHOLD) {
                    return OptionalDouble.of(previousPrice);
                } else {
                    return OptionalDouble.of(price);
                }
            }
        } catch (Exception e) {
            return OptionalDouble.empty();
        }
    }

    public OptionalDouble getMid(String productId) {
        Deque<ProductPriceSegment> priceSegment = priceSegments15Map.get(productId);
        if (priceSegment != null && priceSegment.size() >= MIN_SEGMENTS) {
            return priceSegment.stream().mapToDouble(ProductPriceSegment::getOpenPrice).average();
        } else {
            return OptionalDouble.empty();
        }
    }

    public OptionalDouble getChange(String productId) {
        Deque<ProductPriceSegment> priceSegment = priceSegments15Map.get(productId);
        if (priceSegment != null && priceSegment.size() >= MIN_SEGMENTS) {
            return priceSegment.stream().mapToDouble(ProductPriceSegment::getChangePrice).average();
        } else {
            return OptionalDouble.empty();
        }
    }

    public OptionalDouble getMax(String productId) {
        Deque<ProductPriceSegment> priceSegment = priceSegments15Map.get(productId);
        if (priceSegment != null && priceSegment.size() >= MIN_SEGMENTS) {
            return priceSegment.stream().mapToDouble(ProductPriceSegment::getHighPrice).max();
        } else {
            return OptionalDouble.empty();
        }
    }

    public OptionalDouble getMin(String productId) {
        Deque<ProductPriceSegment> priceSegment = priceSegments15Map.get(productId);
        if (priceSegment != null && priceSegment.size() >= MIN_SEGMENTS) {
            return priceSegment.stream().mapToDouble(ProductPriceSegment::getLowPrice).min();
        } else {
            return OptionalDouble.empty();
        }
    }

    public Set<String> getActiveFeeds() {
        return activeFeeds;
    }

    public void clearActiveFeeds() {
        activeFeeds.clear();
    }

    public void clearStaleData() {
        activeFeeds.clear();
        priceSegments15Map.clear();
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
                ProductPriceSegment previousPriceSegment = priceSegments15Queue.pollFirst();
                if (previousPriceSegment != null) {
                    fistPriceSegment.setChangePrice(fistPriceSegment.getOpenPrice() - previousPriceSegment.getOpenPrice());
                    priceSegments15Queue.addFirst(previousPriceSegment);
                }
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

    public void updateSubscribedCurrencies(Set<String> productIds)
            throws IOException, InterruptedException, DeploymentException {
        if (productIds.equals(activeFeeds) || productIds.isEmpty()) {
            return; // Nothing to update
        }

        // Create new websocket session
        if (session != null) {
            session.close();
        }
        WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
        session = webSocketContainer.connectToServer(
                CoinbaseTickerWebSocket.class,
                URI.create(appConfig.getCoinbaseWebSocketUri())
        );
        session.addMessageHandler(this);
        while(!session.isOpen()) {
            log.info("Waiting for websocket to open...");
            Thread.sleep(1000);
        }

        // Subscribe to required product IDs
        productIds.forEach(priceSegments15Map::remove);
        log.info("Subscribing to the following currency feeds: {}", productIds.toString());
        CoinbaseWebSocketSubscribe subscribe = new CoinbaseWebSocketSubscribe();
        subscribe.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
        subscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
        subscribe.setProductIds(new ArrayList<>(productIds));
        session.getAsyncRemote().sendText(mapper.writeValueAsString(subscribe));

        // Remove any stale data for unsubscribed feeds
        Set<String> currentActiveFeeds = new HashSet<>(activeFeeds);
        currentActiveFeeds.removeAll(productIds);
        log.info("Clearing stale data for productIds: {}", mapper.writeValueAsString(currentActiveFeeds));
        priceSegments15Map.keySet().removeAll(currentActiveFeeds);
    }

    public CoinbaseStatsResponse getProductStats(String productId)
            throws InvalidKeyException, NoSuchAlgorithmException {
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
