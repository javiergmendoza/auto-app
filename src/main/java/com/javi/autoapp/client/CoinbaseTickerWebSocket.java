package com.javi.autoapp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.decoder.WebSocketFeedDecoder;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.util.SignatureTool;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import javax.websocket.ClientEndpoint;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ClientEndpoint(decoders = {WebSocketFeedDecoder.class})
public class CoinbaseTickerWebSocket {

    @OnOpen
    public void onOpen(Session session) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = SignatureTool.getWebSocketSignature(timestamp);

        CoinbaseWebSocketSubscribe authenticate = new CoinbaseWebSocketSubscribe();
        authenticate.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
        authenticate.setChannels(CoinbaseWebSocketSubscribe.FULL_CHANNEL);
        authenticate.setProductIds(Collections.singletonList(Currency.BTC.getLabel()));
        authenticate.setSignature(signature);
        authenticate.setKey(SignatureTool.KEY);
        authenticate.setPassphrase(SignatureTool.PASSPHRASE);
        authenticate.setTimestamp(timestamp);

        ObjectMapper mapper = new ObjectMapper();
        session.getAsyncRemote().sendText(mapper.writeValueAsString(authenticate));
    }
}
