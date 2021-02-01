package com.javi.autoapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.client.model.WebSocketFeed;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.util.SignatureTool;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTradingService implements MessageHandler.Whole<WebSocketFeed> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Session userSession;
    private final CoinbaseTraderClient coinbaseTraderClient;
    private final AutoAppDao autoAppDao;

    public void subscribe(Currency currency) throws JsonProcessingException {
        CoinbaseWebSocketSubscribe subscribe = new CoinbaseWebSocketSubscribe();
        subscribe.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
        subscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
        subscribe.setProductIds(Collections.singletonList(currency.getLabel()));

        userSession.getAsyncRemote().sendText(mapper.writeValueAsString(subscribe));
    }

    public void initBuy(CoinbaseOrderRequest order)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = SignatureTool.getSignature(timestamp, order);
        coinbaseTraderClient.trade(timestamp, signature, mapper.writeValueAsString(order)).subscribe(resp -> {
            if (resp.statusCode().isError()) {
                resp.bodyToMono(String.class).subscribe(error -> {
                    log.error("Order responded with error status. Error: {}", error);
                });
            }
        });
    }

    @Override
    public void onMessage(WebSocketFeed message) {
        if (message.getType().equals(WebSocketFeed.TICKER)) {
            handleTicker(message);
        } else {
            handleOrderDone(message);
        }
    }

    private void handleTicker(WebSocketFeed message) {
        autoAppDao.getAllJobSettings().stream().filter(JobSettings::isActive).forEach(job -> {
            if (job.isSell()) {
                try {
                    crest(message, job);
                } catch (Exception e) {
                    log.error("Failed to handle crest period. Exception: {}", e.getMessage());
                }
            } else {
                try {
                    trough(message, job);
                } catch (Exception e) {
                    log.error("Failed to handle trough period. Exception: {}", e.getMessage());
                }
            }
        });
    }

    private void handleOrderDone(WebSocketFeed message) {

    }

    private void trough(WebSocketFeed message, JobSettings job)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        double price = Double.parseDouble(message.getPrice());

        if (job.isCrossedMinThreshold() && price > job.getMinValue()) {
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setSide(CoinbaseOrderRequest.BUY);
            request.setFunds(String.valueOf(job.getFunds()));
            request.setProductId(job.getCurrency());

            // Update job to hold until sell is complete
            job.setActive(true);
            autoAppDao.startOrUpdateJob(job);

            trade(request);
        }

        // Set bottom value
        if (price < job.getMin()) {
            job.setCrossedMinThreshold(true);
            job.setMinValue(price);
        }
    }

    private void crest(WebSocketFeed message, JobSettings job)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        double price = Double.parseDouble(message.getPrice());

        if (job.isCrossedMaxThreshold() && price < job.getMaxValue()) {
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setSide(CoinbaseOrderRequest.SELL);
            request.setSize(String.valueOf(job.getSize()));
            request.setProductId(job.getCurrency());

            // Update job to hold until sell is complete
            job.setActive(true);
            autoAppDao.startOrUpdateJob(job);

            trade(request);
        }

        // Set top value
        if (price > job.getMax()) {
            job.setCrossedMaxThreshold(true);
            job.setMaxValue(price);
        }
    }

    private void trade(CoinbaseOrderRequest order)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = SignatureTool.getSignature(timestamp, order);
        coinbaseTraderClient.trade(timestamp, signature, mapper.writeValueAsString(order));
    }
}
