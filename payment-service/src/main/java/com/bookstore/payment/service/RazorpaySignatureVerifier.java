package com.bookstore.payment.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class RazorpaySignatureVerifier {
    private RazorpaySignatureVerifier() {}

    public static String hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute payment signature", ex);
        }
    }

    public static boolean matches(String expected, String received) {
        if (expected == null || received == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), received.getBytes(StandardCharsets.US_ASCII));
    }
}
