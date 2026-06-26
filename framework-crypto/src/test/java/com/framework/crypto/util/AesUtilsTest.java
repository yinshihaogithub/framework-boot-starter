package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesUtilsTest {

    @Test
    void encryptUsesRandomIvAndDecrypts() {
        String key = AesUtils.generateKey();
        String plainText = "sensitive-order-1001";

        String firstCipherText = AesUtils.encrypt(plainText, key);
        String secondCipherText = AesUtils.encrypt(plainText, key);

        assertThat(firstCipherText).isNotEqualTo(secondCipherText);
        assertThat(AesUtils.decrypt(firstCipherText, key)).isEqualTo(plainText);
        assertThat(AesUtils.decrypt(secondCipherText, key)).isEqualTo(plainText);
    }
}
