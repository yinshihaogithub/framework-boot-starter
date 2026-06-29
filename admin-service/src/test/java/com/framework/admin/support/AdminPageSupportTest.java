package com.framework.admin.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageSupportTest {

    @Test
    void normalizesInvalidAndHugePageNumbers() {
        assertThat(AdminPageSupport.safePageNum(0)).isEqualTo(AdminPageSupport.DEFAULT_PAGE_NUM);
        assertThat(AdminPageSupport.safePageNum(-1)).isEqualTo(AdminPageSupport.DEFAULT_PAGE_NUM);
        assertThat(AdminPageSupport.safePageNum(Integer.MAX_VALUE)).isEqualTo(AdminPageSupport.MAX_PAGE_NUM);
    }

    @Test
    void normalizesInvalidAndHugePageSizes() {
        assertThat(AdminPageSupport.safePageSize(0)).isEqualTo(AdminPageSupport.DEFAULT_PAGE_SIZE);
        assertThat(AdminPageSupport.safePageSize(-1)).isEqualTo(AdminPageSupport.DEFAULT_PAGE_SIZE);
        assertThat(AdminPageSupport.safePageSize(Integer.MAX_VALUE)).isEqualTo(AdminPageSupport.MAX_PAGE_SIZE);
    }
}
