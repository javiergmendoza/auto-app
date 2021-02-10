package com.javi.autoapp.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(Include.NON_EMPTY)
public class CoinbaseOrderRequest {
    public static final String BUY = "buy";
    public static final String SELL = "sell";

    private final String type = "market";

    @JsonProperty("client_oid")
    private String orderId;

    private String side;
    private String funds;
    private String size;

    @JsonProperty("product_id")
    private String productId;
}
