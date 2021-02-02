package com.javi.autoapp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.decoder.WebSocketFeedDecoder;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.graphql.type.Currency;
import java.io.IOException;
import java.util.Collections;
import javax.websocket.ClientEndpoint;
import javax.websocket.OnOpen;
import javax.websocket.Session;

@ClientEndpoint(decoders = {WebSocketFeedDecoder.class})
public class CoinbaseTickerWebSocket {

    @OnOpen
    public void onOpen(Session session) throws IOException {
        CoinbaseWebSocketSubscribe authenticate = new CoinbaseWebSocketSubscribe();
        authenticate.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
        authenticate.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
        authenticate.setProductIds(Collections.singletonList(Currency.BTC.getLabel()));

        ObjectMapper mapper = new ObjectMapper();
        session.getAsyncRemote().sendText(mapper.writeValueAsString(authenticate));
    }
}
