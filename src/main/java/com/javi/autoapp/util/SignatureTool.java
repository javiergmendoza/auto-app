package com.javi.autoapp.util;

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

    private static final String POST_ORDER_REQUEST_PATH = "/orders";
    private static final String GET_ORDER_REQUEST_PATH = "/orders/client:{id}";

    public static String getRequestPath() {
        return POST_ORDER_REQUEST_PATH;
    }

    public static String getRequestPath(String id) {
        return UriComponentsBuilder.newInstance()
                .path(GET_ORDER_REQUEST_PATH)
                .buildAndExpand(id)
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