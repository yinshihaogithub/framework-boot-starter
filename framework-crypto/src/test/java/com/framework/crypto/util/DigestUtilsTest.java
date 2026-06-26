package com.framework.crypto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigestUtilsTest {

    @Test
    void createsKnownDigests() {
        assertThat(DigestUtils.md5("hello"))
                .isEqualTo("5d41402abc4b2a76b9719d911017c592");
        assertThat(DigestUtils.sha256("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(DigestUtils.sha512("hello"))
                .hasSize(128)
                .startsWith("9b71d224bd62f378");
    }
}
