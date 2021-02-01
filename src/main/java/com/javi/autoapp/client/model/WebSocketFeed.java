package com.javi.autoapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketFeed {
    public static final String TICKER = "ticker";
    public static final String DONE = "done";

    private String type;

    @JsonProperty("order_id")
    private String orderId;

    private String reason;
    private String side;
    private String price;

    @JsonProperty("product_id")
    private String productId;
}
