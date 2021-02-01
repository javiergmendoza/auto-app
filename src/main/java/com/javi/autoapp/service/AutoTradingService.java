package com.javi.autoapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.client.model.WebSocketFeed;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.AutoAppSettings;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.util.SignatureTool;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AutoTradingService implements MessageHandler.Whole<WebSocketFeed> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Session userSession;
    private final CoinbaseTraderClient coinbaseTraderClient;
    private final SignatureTool signatureTool;
    private final AutoAppDao autoAppDao;
    private final String key;
    private final String passphrase;
    private final String secret;
    private boolean authenticated = false;

    public AutoTradingService(
            Session session,
            CoinbaseTraderClient coinbaseTraderClient,
            SignatureTool signatureTool,
            AutoAppDao dao) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        this.coinbaseTraderClient = coinbaseTraderClient;
        this.signatureTool = signatureTool;

        autoAppDao = dao;

        //AutoAppSettings settings = autoAppDao.getAutoAppSettings();
        AutoAppSettings settings = new AutoAppSettings();
        settings.setKey("434b6341e2d77e0199c15de2bd44feb9");
        settings.setSecret("SthTiOWse6R2qORb9Qo0SY5Ic1xnVN4jF0JTTf8QQWSMaJMIeLBbgNVZNMWcjnMqlmmw9IPl2u+0yiYm86P7Cw==");
        settings.setPassphrase("test");

        key = settings.getKey();
        passphrase = settings.getPassphrase();
        secret = settings.getSecret();

        authenticateWebSocket();

        userSession = session;
        userSession.addMessageHandler(this);
    }

    @SneakyThrows
    @Override
    public void onMessage(WebSocketFeed message) {
        if (!authenticated) {
            authenticateWebSocket();
        }
        if (message.getType().equals(WebSocketFeed.TICKER)) {
            handleTicker(message);
        } else if (message.getType().equals(WebSocketFeed.DONE)) {
            handleOrderDone(message);
        }
    }

    public void buy() {
//        String timestamp = String.valueOf(Instant.now().getEpochSecond());
//
//        CoinbaseOrderRequest order = new CoinbaseOrderRequest();
//        order.setSide(CoinbaseOrderRequest.BUY);
//        order.setFunds(autoAppDao.get);
//        order.setProductId(currency.getLabel());
//
//        String signature = signatureTool.getSignature(secret, timestamp, order);
//        coinbaseTraderClient.trade(
//                passphrase,
//                key,
//                timestamp,
//                signature,
//                mapper.writeValueAsString(order)).subscribe(clientResponse -> {
//            if (clientResponse.statusCode().isError()) {
//                clientResponse.bodyToMono(String.class).subscribe(log::error);
//            } else {
//                clientResponse.bodyToMono(CoinbaseOrderResponse.class).subscribe(resp -> {
//                    log.info(resp.getSide() + " - " + resp.getProductId() + ": " + resp.getPrice());
//                });
//            }
//        });
    }

    public void sell() {
        // Do nothing
    }

    public void change(Currency currency) throws JsonProcessingException {
        updateSubscription(Collections.singletonList(currency.getLabel()));
    }

    private void handleTicker(WebSocketFeed message) {
//        List<JobSettings> jobSettings = autoAppDao.getAllJobSettings();
//
//        AtomicBoolean bustCache = new AtomicBoolean(false);
//        jobSettings.forEach(job -> {
//            if (Instant.parse(job.getExpires()).isBefore(Instant.now())) {
//                // Update job status
//                JobStatus jobStatus = autoAppDao.getJobStatus(job.getJobId());
//                jobStatus.setStatus(Status.FINISHED);
//                autoAppDao.updateJobStatus(jobStatus);
//
//                // Remove from job settings
//                autoAppDao.deleteJob(job);
//
//                bustCache.set(true);
//            }
//        });

        log.info("ticker - {}: {}", message.getProductId(), message.getPrice());
    }

    private void handleOrderDone(WebSocketFeed message) {
        log.info("done - {}: {}, {}, {}, {}", message.getOrderId(), message.getReason(), message.getSide(), message.getPrice(), message.getProductId());
    }

    private void updateSubscription(List<String> currencies) throws JsonProcessingException {
        log.info("Updating to monitor: {}", currencies.toString());
        CoinbaseWebSocketSubscribe unsubscribe = new CoinbaseWebSocketSubscribe();
        unsubscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
        unsubscribe.setType(CoinbaseWebSocketSubscribe.UNSUBSCRIBE);
        userSession.getAsyncRemote().sendText(mapper.writeValueAsString(unsubscribe));

        CoinbaseWebSocketSubscribe subscribe = new CoinbaseWebSocketSubscribe();
        unsubscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
        subscribe.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
        subscribe.setProductIds(currencies);
        userSession.getAsyncRemote().sendText(mapper.writeValueAsString(subscribe));
    }

    private void authenticateWebSocket() throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        log.info("secret: {}", secret);
        log.info("timestamp: {}", timestamp);
        String signature = signatureTool.getWebSocketSignature(secret, timestamp);

        CoinbaseWebSocketSubscribe authenticate = new CoinbaseWebSocketSubscribe();
        authenticate.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
        authenticate.setChannels(CoinbaseWebSocketSubscribe.FULL_CHANNEL);
        authenticate.setSignature(signature);
        authenticate.setKey(key);
        authenticate.setPassphrase(passphrase);
        authenticate.setTimestamp(timestamp);

        log.info(mapper.writeValueAsString(authenticate));
        userSession.getAsyncRemote().sendText(mapper.writeValueAsString(authenticate));
    }
}
