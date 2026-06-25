package com.framework.crypto.util;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

/**
 * 国密算法工具（SM3 摘要 + SM4 对称加密）
 * 基于 BouncyCastle 实现
 *
 * 需要依赖：org.bouncycastle:bcprov-jdk18on
 */
public class SmCryptoUtils {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final int KEY_SIZE = 16; // SM4 密钥 128 bit = 16 byte

    // ===== SM3 摘要 =====

    /**
     * SM3 摘要（256bit，返回 64 位十六进制字符串）
     */
    public static String sm3(String input) {
        SM3Digest digest = new SM3Digest();
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        digest.update(data, 0, data.length);
        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);
        return bytesToHex(hash);
    }

    // ===== SM4 对称加密 =====

    /**
     * SM4 加密（CBC + PKCS7）
     *
     * @param plainText 明文
     * @param key       密钥（16字节，Base64 编码）
     * @param iv        向量（16字节，Base64 编码）
     * @return 密文（Base64）
     */
    public static String sm4Encrypt(String plainText, String key, String iv) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key);
            byte[] ivBytes = Base64.getDecoder().decode(iv);
            byte[] data = plainText.getBytes(StandardCharsets.UTF_8);

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new SM4Engine()), new PKCS7Padding());
            cipher.init(true, new ParametersWithIV(new KeyParameter(keyBytes), ivBytes));

            byte[] output = new byte[cipher.getOutputSize(data.length)];
            int len = cipher.processBytes(data, 0, data.length, output, 0);
            len += cipher.doFinal(output, len);

            byte[] result = new byte[len];
            System.arraycopy(output, 0, result, 0, len);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("SM4加密失败", e);
        }
    }

    /**
     * SM4 解密
     */
    public static String sm4Decrypt(String cipherTextBase64, String key, String iv) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key);
            byte[] ivBytes = Base64.getDecoder().decode(iv);
            byte[] data = Base64.getDecoder().decode(cipherTextBase64);

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new SM4Engine()), new PKCS7Padding());
            cipher.init(false, new ParametersWithIV(new KeyParameter(keyBytes), ivBytes));

            byte[] output = new byte[cipher.getOutputSize(data.length)];
            int len = cipher.processBytes(data, 0, data.length, output, 0);
            len += cipher.doFinal(output, len);

            return new String(output, 0, len, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("SM4解密失败", e);
        }
    }

    /**
     * 生成 SM4 密钥（16字节，Base64）
     */
    public static String generateSm4Key() {
        byte[] key = new byte[KEY_SIZE];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 生成 SM4 IV（16字节，Base64）
     */
    public static String generateIv() {
        byte[] iv = new byte[KEY_SIZE];
        new java.security.SecureRandom().nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
    }

    // ===== 工具方法 =====

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
