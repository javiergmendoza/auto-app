package com.javi.autoapp.client;

import com.javi.autoapp.client.decoder.WebSocketFeedDecoder;
import javax.websocket.ClientEndpoint;

@ClientEndpoint(decoders = {WebSocketFeedDecoder.class})
public class CoinbaseTickerWebSocket {
}
