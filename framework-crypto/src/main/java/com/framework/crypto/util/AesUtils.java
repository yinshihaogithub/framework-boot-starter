package com.framework.crypto.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES 加解密工具
 */
public class AesUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 16;
    private static final byte[] FORMAT_MAGIC = new byte[]{'A', 'E', 'S', '1'};
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 加密
     */
    public static String encrypt(String plainText, String key) {
        if (plainText == null) {
            throw new IllegalArgumentException("plainText must not be null");
        }
        SecretKeySpec keySpec = getKeySpec(key);
        try {
            byte[] iv = randomIv();
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(pack(iv, encrypted));
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 解密
     */
    public static String decrypt(String cipherText, String key) {
        SecretKeySpec keySpec = getKeySpec(key);
        byte[] payload = decodeCipherText(cipherText);
        try {
            byte[] iv = legacyIv(keySpec);
            byte[] encrypted = payload;
            if (hasFormatMagic(payload)) {
                iv = Arrays.copyOfRange(payload, FORMAT_MAGIC.length, FORMAT_MAGIC.length + IV_SIZE);
                encrypted = Arrays.copyOfRange(payload, FORMAT_MAGIC.length + IV_SIZE, payload.length);
            }
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 生成密钥
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("生成AES密钥失败", e);
        }
    }

    private static SecretKeySpec getKeySpec(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("AES key must not be blank");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("AES key must be valid Base64", e);
        }
        if (!isValidKeyLength(keyBytes.length)) {
            throw new IllegalArgumentException("AES key must decode to 16, 24 or 32 bytes");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private static byte[] decodeCipherText(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalArgumentException("cipherText must not be blank");
        }
        try {
            return Base64.getDecoder().decode(cipherText);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("cipherText must be valid Base64", e);
        }
    }

    private static boolean isValidKeyLength(int length) {
        return length == 16 || length == 24 || length == 32;
    }

    private static byte[] randomIv() {
        byte[] iv = new byte[IV_SIZE];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    private static byte[] pack(byte[] iv, byte[] encrypted) {
        byte[] payload = new byte[FORMAT_MAGIC.length + IV_SIZE + encrypted.length];
        System.arraycopy(FORMAT_MAGIC, 0, payload, 0, FORMAT_MAGIC.length);
        System.arraycopy(iv, 0, payload, FORMAT_MAGIC.length, IV_SIZE);
        System.arraycopy(encrypted, 0, payload, FORMAT_MAGIC.length + IV_SIZE, encrypted.length);
        return payload;
    }

    private static boolean hasFormatMagic(byte[] payload) {
        if (payload.length < FORMAT_MAGIC.length + IV_SIZE) {
            return false;
        }
        for (int i = 0; i < FORMAT_MAGIC.length; i++) {
            if (payload[i] != FORMAT_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] legacyIv(SecretKeySpec keySpec) {
        return Arrays.copyOf(keySpec.getEncoded(), IV_SIZE);
    }
}
