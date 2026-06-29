package com.framework.auth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidatorTest {

    @Test
    void validatesStrongPasswordWithUnifiedLengthBounds() {
        assertThat(PasswordValidator.validateStrong(null)).isEqualTo("密码长度至少8位");
        assertThat(PasswordValidator.validateStrong("Aa1!234")).isEqualTo("密码长度至少8位");
        assertThat(PasswordValidator.validateStrong("Aa1!" + "a".repeat(61))).isEqualTo("密码长度不能超过64位");

        String password64Chars = "Aa1!" + "a".repeat(60);
        assertThat(PasswordValidator.validateStrong(password64Chars)).isNull();
        assertThat(PasswordValidator.isStrong(password64Chars)).isTrue();
        assertThat(PasswordValidator.strengthLevel(password64Chars)).isEqualTo(2);
    }

    @Test
    void validatesMediumPasswordWithUnifiedLengthBounds() {
        assertThat(PasswordValidator.validateMedium(null)).isEqualTo("密码长度至少8位");
        assertThat(PasswordValidator.validateMedium("abc1234")).isEqualTo("密码长度至少8位");
        assertThat(PasswordValidator.validateMedium("abc12345" + "a".repeat(57))).isEqualTo("密码长度不能超过64位");

        String password64Chars = "abc12345" + "a".repeat(56);
        assertThat(PasswordValidator.validateMedium(password64Chars)).isNull();
        assertThat(PasswordValidator.isMedium(password64Chars)).isTrue();
        assertThat(PasswordValidator.strengthLevel(password64Chars)).isEqualTo(1);
    }

    @Test
    void rejectsOverlongPasswordsAcrossBooleanAndLevelApis() {
        String strongButTooLong = "Aa1!" + "a".repeat(61);
        String mediumButTooLong = "abc12345" + "a".repeat(57);

        assertThat(PasswordValidator.isStrong(strongButTooLong)).isFalse();
        assertThat(PasswordValidator.isMedium(mediumButTooLong)).isFalse();
        assertThat(PasswordValidator.strengthLevel(strongButTooLong)).isZero();
        assertThat(PasswordValidator.strengthLevel(mediumButTooLong)).isZero();
    }
}
