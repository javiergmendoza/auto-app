package com.javi.autoapp.client.decoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.model.WebSocketFeed;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

public class WebSocketFeedDecoder implements Decoder.Text<WebSocketFeed> {
    @Override
    public WebSocketFeed decode(String s) throws DecodeException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(s, WebSocketFeed.class);
        } catch (JsonProcessingException e) {
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
