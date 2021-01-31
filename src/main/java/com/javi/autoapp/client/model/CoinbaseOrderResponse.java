package com.javi.autoapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseOrderResponse {
    private String side;
    private String price;
    private String size;

    @JsonProperty("product_id")
    private String productId;
}
