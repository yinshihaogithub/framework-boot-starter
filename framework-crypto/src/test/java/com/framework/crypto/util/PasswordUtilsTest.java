package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtilsTest {

    @Test
    void hashesWithSaltAndVerifiesPassword() {
        String firstHash = PasswordUtils.hash("user123456");
        String secondHash = PasswordUtils.hash("user123456");

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(PasswordUtils.verify("user123456", firstHash)).isTrue();
        assertThat(PasswordUtils.verify("bad-password", firstHash)).isFalse();
        assertThat(PasswordUtils.verify("user123456", "not-a-bcrypt-hash")).isFalse();
    }
}
