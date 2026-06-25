package com.framework.crypto.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码哈希工具（BCrypt）
 */
public class PasswordUtils {

    /**
     * 加密密码
     */
    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    /**
     * 校验密码
     */
    public static boolean verify(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}
