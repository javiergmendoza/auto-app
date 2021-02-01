package com.javi.autoapp.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

public class SignatureTool {
    public static final String KEY = System.getenv("COINBASE_KEY");
    public static final String PASSPHRASE = System.getenv("COINBASE_PASS");
    public static final String SECRET = System.getenv("COINBASE_SECRET");
    private static final String ORDER_METHOD = "POST";
    private static final String ORDER_REQUEST_PATH = "/orders";
    private static final String WEB_SOCKET_METHOD = "GET";
    private static final String WEB_SOCKET_REQUEST_PATH = "/users/self/verify";

    public static String getSignature(
            String timestamp,
            CoinbaseOrderRequest order) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        return sign(timestamp, ORDER_METHOD, ORDER_REQUEST_PATH, order);
    }

    public static String getWebSocketSignature(
            String timestamp
    ) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        return sign(timestamp, WEB_SOCKET_METHOD, WEB_SOCKET_REQUEST_PATH, null);
    }

    private static String sign(
            String timestamp,
            String method,
            String requestPath,
            CoinbaseOrderRequest order) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {
        ObjectMapper mapper = new ObjectMapper();
        String body = "";
        if (order != null) {
            body = mapper.writeValueAsString(order);
        }
        String signature = timestamp + method + requestPath + body;

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256");
        sha256_HMAC.init(keySpec);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(signature.getBytes()));
    }
}
