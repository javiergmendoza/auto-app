package com.javi.autoapp.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class CoinbaseWebSocketSubscribe {
    public static final List<String> TICKER_CHANNEL = Collections.singletonList("ticker");
    public static final List<String> FULL_CHANNEL = Collections.singletonList("full");
    public static final String SUBSCRIBE = "subscribe";
    public static final String UNSUBSCRIBE = "unsubscribe";

    private List<String> channels;
    private String type;
    private String signature;
    private String key;
    private String passphrase;
    private String timestamp;

    @JsonProperty("product_ids")
    private List<String> productIds;
}
