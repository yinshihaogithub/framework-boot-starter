package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void encryptAndDecryptSupportStandardAesKeyLengths() {
        for (int length : new int[]{16, 24, 32}) {
            String key = Base64.getEncoder().encodeToString(new byte[length]);

            String cipherText = AesUtils.encrypt("hello", key);

            assertThat(AesUtils.decrypt(cipherText, key)).isEqualTo("hello");
        }
    }

    @Test
    void encryptRejectsInvalidPlainTextOrKey() {
        String key = AesUtils.generateKey();

        assertThatThrownBy(() -> AesUtils.encrypt(null, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("plainText must not be null");
        assertThatThrownBy(() -> AesUtils.encrypt("hello", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AES key must not be blank");
        assertThatThrownBy(() -> AesUtils.encrypt("hello", "not-base64"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AES key must be valid Base64");
        assertThatThrownBy(() -> AesUtils.encrypt("hello", Base64.getEncoder().encodeToString(new byte[8])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AES key must decode to 16, 24 or 32 bytes");
    }

    @Test
    void decryptRejectsInvalidCipherText() {
        String key = AesUtils.generateKey();

        assertThatThrownBy(() -> AesUtils.decrypt(" ", key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cipherText must not be blank");
        assertThatThrownBy(() -> AesUtils.decrypt("not-base64", key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cipherText must be valid Base64");
    }
}
