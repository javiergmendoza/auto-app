package com.javi.autoapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ProductPriceSegment {
    private double openPrice = 0.0;
    private double highPrice = 0.0;
    private double lowPrice = Double.MAX_VALUE;
    private double changePrice = 0.0;
    private long count = 0;
    private Instant timestamp = Instant.now();
    private String timeString = timestamp.toString();

    @JsonIgnore
    public void aggregatePrice(String priceString) {
        double price;
        try {
            price = Double.parseDouble(priceString);
        } catch (Exception e) {
            log.error("Failed to parse price string.");
            return;
        }

        if (openPrice <= 0) {
            openPrice = price;
        } else {
            openPrice = ((openPrice * count) + price) / (count + 1);
        }

        count++;

        if (price > highPrice) {
            highPrice = price;
        }

        if (price < lowPrice) {
            lowPrice = price;
        }
    }
}
