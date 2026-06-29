package com.framework.admin.support;

/**
 * Shared pagination bounds for admin query endpoints.
 */
public final class AdminPageSupport {

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_PAGE_NUM = 100_000;

    private AdminPageSupport() {
    }

    public static int safePageNum(int pageNum) {
        if (pageNum <= 0) {
            return DEFAULT_PAGE_NUM;
        }
        return Math.min(pageNum, MAX_PAGE_NUM);
    }

    public static int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
