package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaUtilsTest {

    @Test
    void encryptsDecryptsSignsAndVerifies() {
        String[] keyPair = RsaUtils.generateKeyPair();
        String publicKey = keyPair[0];
        String privateKey = keyPair[1];
        String data = "order-1001";

        String cipherText = RsaUtils.encrypt(data, publicKey);
        String signature = RsaUtils.sign(data, privateKey);

        assertThat(RsaUtils.decrypt(cipherText, privateKey)).isEqualTo(data);
        assertThat(RsaUtils.verify(data, signature, publicKey)).isTrue();
        assertThat(RsaUtils.verify(data + "-changed", signature, publicKey)).isFalse();
    }

    @Test
    void encryptAndSignRejectNullDataOrInvalidKeys() {
        String[] keyPair = RsaUtils.generateKeyPair();
        String privateKey = keyPair[1];

        assertThatThrownBy(() -> RsaUtils.encrypt(null, keyPair[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("plainText must not be null");
        assertThatThrownBy(() -> RsaUtils.encrypt("hello", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("publicKey must not be blank");
        assertThatThrownBy(() -> RsaUtils.encrypt("hello", "not-base64"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("publicKey must be valid Base64");
        assertThatThrownBy(() -> RsaUtils.encrypt("hello", Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("publicKey must be a valid RSA public key");

        assertThatThrownBy(() -> RsaUtils.sign(null, privateKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("data must not be null");
        assertThatThrownBy(() -> RsaUtils.sign("hello", "not-base64"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("privateKey must be valid Base64");
    }

    @Test
    void decryptRejectsInvalidCipherTextOrPrivateKey() {
        String privateKey = RsaUtils.generateKeyPair()[1];

        assertThatThrownBy(() -> RsaUtils.decrypt(" ", privateKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cipherText must not be blank");
        assertThatThrownBy(() -> RsaUtils.decrypt("not-base64", privateKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cipherText must be valid Base64");
        assertThatThrownBy(() -> RsaUtils.decrypt(Base64.getEncoder().encodeToString(new byte[16]), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("privateKey must not be blank");
    }

    @Test
    void verifyReturnsFalseForInvalidInputs() {
        String[] keyPair = RsaUtils.generateKeyPair();
        String publicKey = keyPair[0];
        String privateKey = keyPair[1];
        String signature = RsaUtils.sign("order-1001", privateKey);

        assertThat(RsaUtils.verify(null, signature, publicKey)).isFalse();
        assertThat(RsaUtils.verify("order-1001", " ", publicKey)).isFalse();
        assertThat(RsaUtils.verify("order-1001", "not-base64", publicKey)).isFalse();
        assertThat(RsaUtils.verify("order-1001", signature, "not-base64")).isFalse();
    }
}
