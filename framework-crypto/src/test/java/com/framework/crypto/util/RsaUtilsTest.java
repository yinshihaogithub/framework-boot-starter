package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
