package com.javi.autoapp.util;

import static com.javi.autoapp.util.CoinbasePathConstants.END_QUERY_PARAM;
import static com.javi.autoapp.util.CoinbasePathConstants.GET_ORDER_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.GET_STATS_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.GET_STATS_SLICES_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.GRANULARITY;
import static com.javi.autoapp.util.CoinbasePathConstants.GRANULARITY_QUERY_PARAM;
import static com.javi.autoapp.util.CoinbasePathConstants.POST_ORDER_REQUEST_PATH;
import static com.javi.autoapp.util.CoinbasePathConstants.START_QUERY_PARAM;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.web.util.UriComponentsBuilder;

public class SignatureTool {
    public static final String KEY = System.getProperty("COINBASE_KEY");
    public static final String PASSPHRASE = System.getProperty("COINBASE_PASS");
    public static final String SECRET = System.getProperty("COINBASE_SECRET");

    public static String getOrdersRequestPath() {
        return POST_ORDER_REQUEST_PATH;
    }

    public static String getOrdersRequestPath(String id) {
        return UriComponentsBuilder.newInstance()
                .path(GET_ORDER_REQUEST_PATH)
                .buildAndExpand(id)
                .toUriString();
    }

    public static String getStatsRequestPath(String productId) {
        return UriComponentsBuilder.newInstance()
                .path(GET_STATS_REQUEST_PATH)
                .buildAndExpand(productId)
                .toUriString();
    }

    public static String getStatsSlicesRequestPath(String start, String end, String productId) {
        return UriComponentsBuilder.newInstance()
                .path(GET_STATS_SLICES_REQUEST_PATH)
                .queryParam(START_QUERY_PARAM, start)
                .queryParam(END_QUERY_PARAM, end)
                .queryParam(GRANULARITY_QUERY_PARAM, GRANULARITY)
                .buildAndExpand(productId)
                .toUriString();
    }

    public static String getSignature(
            String timestamp,
            String method,
            String requestPath) throws InvalidKeyException, NoSuchAlgorithmException {
        return getSignature(timestamp, method, requestPath, null);
    }

    public static String getSignature(
            String timestamp,
            String method,
            String requestPath,
            String body) throws NoSuchAlgorithmException, InvalidKeyException {
        String signature = timestamp + method + requestPath;
        if (body != null) {
            signature += body;
        }
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256");
        sha256_HMAC.init(keySpec);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(signature.getBytes()));
    }
}