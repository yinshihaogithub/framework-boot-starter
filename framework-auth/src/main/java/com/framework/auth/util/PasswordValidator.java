package com.framework.auth.util;

import java.util.regex.Pattern;

/**
 * 密码强度校验工具
 */
public final class PasswordValidator {

    private PasswordValidator() {}

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;

    /** 至少8位，包含大小写字母+数字+特殊字符 */
    private static final Pattern STRONG_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+\\-=\\[\\]{}|])[A-Za-z\\d@$!%*?&#^()_+\\-=\\[\\]{}|]{8,64}$"
    );

    /** 中等：至少8位，包含字母+数字 */
    private static final Pattern MEDIUM_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&#^()_+\\-=\\[\\]{}|]{8,64}$"
    );

    /**
     * 校验密码强度（强密码策略）
     * 要求：至少8位，包含大写字母、小写字母、数字、特殊字符
     *
     * @return 校验失败返回错误信息，成功返回 null
     */
    public static String validateStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "密码长度至少8位";
        }
        if (password.length() > MAX_LENGTH) {
            return "密码长度不能超过64位";
        }
        if (!password.matches(".*[a-z].*")) {
            return "密码必须包含小写字母";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "密码必须包含大写字母";
        }
        if (!password.matches(".*\\d.*")) {
            return "密码必须包含数字";
        }
        if (!password.matches(".*[@$!%*?&#^()_+\\-=\\[\\]{}|].*")) {
            return "密码必须包含特殊字符";
        }
        return null;
    }

    /**
     * 校验密码强度（中等策略）
     * 要求：至少8位，包含字母和数字
     */
    public static String validateMedium(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "密码长度至少8位";
        }
        if (password.length() > MAX_LENGTH) {
            return "密码长度不能超过64位";
        }
        if (!MEDIUM_PATTERN.matcher(password).matches()) {
            return "密码必须包含字母和数字";
        }
        return null;
    }

    /**
     * 判断是否为强密码
     */
    public static boolean isStrong(String password) {
        return hasValidLength(password) && STRONG_PATTERN.matcher(password).matches();
    }

    /**
     * 判断是否为中等密码
     */
    public static boolean isMedium(String password) {
        return hasValidLength(password) && MEDIUM_PATTERN.matcher(password).matches();
    }

    /**
     * 评估密码强度等级
     *
     * @return 0=弱, 1=中, 2=强
     */
    public static int strengthLevel(String password) {
        if (!hasValidLength(password)) {
            return 0;
        }
        if (isStrong(password)) {
            return 2;
        }
        if (isMedium(password)) {
            return 1;
        }
        return 0;
    }

    private static boolean hasValidLength(String password) {
        return password != null && password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
    }
}
