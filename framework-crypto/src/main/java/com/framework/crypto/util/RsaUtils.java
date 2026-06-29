package com.framework.crypto.util;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 非对称加解密工具
 * 用于接口数据传输加密、数字签名
 */
public class RsaUtils {

    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * 生成密钥对
     *
     * @return [0]=公钥(Base64), [1]=私钥(Base64)
     */
    public static String[] generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE, new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return new String[]{publicKey, privateKey};
        } catch (Exception e) {
            throw new RuntimeException("生成RSA密钥对失败", e);
        }
    }

    /**
     * 公钥加密
     */
    public static String encrypt(String plainText, String publicKeyBase64) {
        requireData(plainText, "plainText");
        PublicKey publicKey = decodePublicKey(publicKeyBase64);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("RSA加密失败", e);
        }
    }

    /**
     * 私钥解密
     */
    public static String decrypt(String cipherTextBase64, String privateKeyBase64) {
        byte[] cipherBytes = decodeBase64(cipherTextBase64, "cipherText");
        PrivateKey privateKey = decodePrivateKey(privateKeyBase64);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA解密失败", e);
        }
    }

    /**
     * 私钥签名
     */
    public static String sign(String data, String privateKeyBase64) {
        requireData(data, "data");
        PrivateKey privateKey = decodePrivateKey(privateKeyBase64);
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new RuntimeException("RSA签名失败", e);
        }
    }

    /**
     * 公钥验签
     */
    public static boolean verify(String data, String signBase64, String publicKeyBase64) {
        if (data == null || signBase64 == null || signBase64.isBlank()
                || publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            return false;
        }
        try {
            PublicKey publicKey = decodePublicKey(publicKeyBase64);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(decodeBase64(signBase64, "signature"));
        } catch (Exception e) {
            return false;
        }
    }

    private static PublicKey decodePublicKey(String base64) {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodeBase64(base64, "publicKey"));
            return KeyFactory.getInstance(ALGORITHM).generatePublic(spec);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("publicKey must be a valid RSA public key", e);
        }
    }

    private static PrivateKey decodePrivateKey(String base64) {
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodeBase64(base64, "privateKey"));
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(spec);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("privateKey must be a valid RSA private key", e);
        }
    }

    private static void requireData(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    private static byte[] decodeBase64(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " must be valid Base64", e);
        }
    }
}
