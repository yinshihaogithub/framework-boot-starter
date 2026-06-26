package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesensitizeUtilsTest {

    @Test
    void masksCommonSensitiveValues() {
        assertThat(DesensitizeUtils.phone("13812345678")).isEqualTo("138****5678");
        assertThat(DesensitizeUtils.idCard("110101199001011234")).isEqualTo("110***********1234");
        assertThat(DesensitizeUtils.bankCard("6222123456781234")).isEqualTo("6222 **** **** 1234");
        assertThat(DesensitizeUtils.email("zhangsan@example.com")).isEqualTo("z***@example.com");
        assertThat(DesensitizeUtils.name("张三丰")).isEqualTo("张*丰");
        assertThat(DesensitizeUtils.name("张三")).isEqualTo("张*");
    }

    @Test
    void leavesInvalidOrTooShortValuesUnchanged() {
        assertThat(DesensitizeUtils.phone("123456")).isEqualTo("123456");
        assertThat(DesensitizeUtils.email("a@example.com")).isEqualTo("a@example.com");
        assertThat(DesensitizeUtils.name("张")).isEqualTo("张");
        assertThat(DesensitizeUtils.bankCard(null)).isNull();
    }
}
