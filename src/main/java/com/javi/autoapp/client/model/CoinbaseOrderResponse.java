package com.javi.autoapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseOrderResponse {

    @JsonProperty("executed_value")
    private String executedValue;

    @JsonProperty("filled_size")
    private String filledSize;
    private boolean settled;

    @JsonProperty("fill_fees")
    private String fillFees;
}
