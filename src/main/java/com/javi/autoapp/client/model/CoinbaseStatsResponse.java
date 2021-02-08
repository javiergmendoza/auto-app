package com.javi.autoapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStatsResponse {
    private String open;
    private String high;
    private String low;
    private String volume;
    private String last;
    @JsonProperty("volume_30day")
    private String volume30Day;
}
