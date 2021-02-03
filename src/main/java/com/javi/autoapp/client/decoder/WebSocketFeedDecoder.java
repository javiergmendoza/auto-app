package com.javi.autoapp.client.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.model.CoinbaseTicker;
import java.io.IOException;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

public class WebSocketFeedDecoder implements Decoder.Text<CoinbaseTicker> {
    @Override
    public CoinbaseTicker decode(String s) throws DecodeException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(s, CoinbaseTicker.class);
        } catch (IOException e) {
            throw new DecodeException(s, e.getMessage(), e);
        }
    }

    @Override
    public boolean willDecode(String s) {
        return (s != null);
    }

    @Override
    public void init(EndpointConfig endpointConfig) {

    }

    @Override
    public void destroy() {

    }
}
