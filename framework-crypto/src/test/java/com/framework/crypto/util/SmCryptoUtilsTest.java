package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmCryptoUtilsTest {

    @Test
    void createsSm3DigestAndSm4RoundTrip() {
        String key = SmCryptoUtils.generateSm4Key();
        String iv = SmCryptoUtils.generateIv();
        String plainText = "sensitive-sm4-data";

        String cipherText = SmCryptoUtils.sm4Encrypt(plainText, key, iv);

        assertThat(SmCryptoUtils.sm3("hello")).hasSize(64);
        assertThat(SmCryptoUtils.sm4Decrypt(cipherText, key, iv)).isEqualTo(plainText);
    }
}
