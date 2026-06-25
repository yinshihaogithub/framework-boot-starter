package com.framework.crypto.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 摘要算法工具（MD5 / SHA-256 / SM3）
 */
public class DigestUtils {

    public static String md5(String input) {
        return digest(input, "MD5");
    }

    public static String sha256(String input) {
        return digest(input, "SHA-256");
    }

    public static String sha512(String input) {
        return digest(input, "SHA-512");
    }

    private static String digest(String input, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(algorithm + "摘要失败", e);
        }
    }
}
