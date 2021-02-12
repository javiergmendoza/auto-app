package com.javi.autoapp.util;

public interface CoinbasePathConstants {
    String POST_ORDER_REQUEST_PATH = "/orders";
    String GET_ORDER_REQUEST_PATH = "/orders/client:{id}";
    String GET_STATS_REQUEST_PATH = "/products/{productId}/stats";
    String GET_STATS_SLICES_REQUEST_PATH = "/products/{productId}/candles";
    String START_QUERY_PARAM = "start";
    String END_QUERY_PARAM = "end";
    String GRANULARITY_QUERY_PARAM = "granularity";
    String GRANULARITY = "3600";
}
