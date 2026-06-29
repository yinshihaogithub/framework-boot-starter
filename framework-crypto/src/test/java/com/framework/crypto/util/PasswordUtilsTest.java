package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rejectsBlankPasswordWhenHashing() {
        assertThatThrownBy(() -> PasswordUtils.hash(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("plainPassword must not be blank");
        assertThatThrownBy(() -> PasswordUtils.hash(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("plainPassword must not be blank");
        assertThatThrownBy(() -> PasswordUtils.hash("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("plainPassword must not be blank");
    }

    @Test
    void returnsFalseWhenVerifyingBlankInputs() {
        String hashedPassword = PasswordUtils.hash("user123456");

        assertThat(PasswordUtils.verify(null, hashedPassword)).isFalse();
        assertThat(PasswordUtils.verify("", hashedPassword)).isFalse();
        assertThat(PasswordUtils.verify("   ", hashedPassword)).isFalse();
        assertThat(PasswordUtils.verify("user123456", null)).isFalse();
        assertThat(PasswordUtils.verify("user123456", "")).isFalse();
        assertThat(PasswordUtils.verify("user123456", "   ")).isFalse();
    }
}
