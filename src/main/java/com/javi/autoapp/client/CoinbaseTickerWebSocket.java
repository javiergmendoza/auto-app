package com.javi.autoapp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.decoder.WebSocketFeedDecoder;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.graphql.type.Currency;
import java.util.Collections;
import javax.websocket.ClientEndpoint;
import javax.websocket.OnOpen;
import javax.websocket.Session;

@ClientEndpoint(decoders = {WebSocketFeedDecoder.class})
public class CoinbaseTickerWebSocket {

    @OnOpen
    public void onOpen(Session session) throws JsonProcessingException {
        CoinbaseWebSocketSubscribe subscribe = new CoinbaseWebSocketSubscribe();
        subscribe.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
        subscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
        subscribe.setProductIds(Collections.singletonList(Currency.XLM.getLabel()));

        ObjectMapper mapper = new ObjectMapper();
        session.getAsyncRemote().sendText(mapper.writeValueAsString(subscribe));
    }
}
