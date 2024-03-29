package com.javi.autoapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseTicker {
    private String price;

    @JsonProperty("product_id")
    private String productId;
}
