package com.javi.autoapp.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import com.javi.autoapp.client.model.CoinbaseOrderResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SignatureTool {
    private static final String ORDER_METHOD = "POST";
    private static final String ORDER_REQUEST_PATH = "/orders";
    private static final String WEB_SOCKET_METHOD = "GET";
    private static final String WEB_SOCKET_REQUEST_PATH = "/users/self/verify";

    public String getSignature(
            String secret,
            String timestamp,
            CoinbaseOrderRequest order) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        return sign(secret, timestamp, ORDER_METHOD, ORDER_REQUEST_PATH, null);
    }

    public String getWebSocketSignature(
            String secret,
            String timestamp
    ) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        return sign(secret, timestamp, WEB_SOCKET_METHOD, WEB_SOCKET_REQUEST_PATH, null);
    }

    private String sign(
            String secret,
            String timestamp,
            String method,
            String requestPath,
            CoinbaseOrderResponse order) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {
        ObjectMapper mapper = new ObjectMapper();
        String body = "";
        if (order != null) {
            body = mapper.writeValueAsString(order);
        }
        String signature = timestamp + method + requestPath + body;

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
        sha256_HMAC.init(keySpec);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(signature.getBytes()));
    }
}
