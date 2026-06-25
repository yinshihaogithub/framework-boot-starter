package com.framework.crypto.util;

import org.springframework.util.StringUtils;

/**
 * 数据脱敏工具
 */
public class DesensitizeUtils {

    /**
     * 手机号脱敏：138****1234
     */
    public static String phone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 身份证脱敏：110***********1234
     */
    public static String idCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 3) + "***********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行卡脱敏：6222 **** **** 1234
     */
    public static String bankCard(String card) {
        if (!StringUtils.hasText(card) || card.length() < 8) {
            return card;
        }
        return card.substring(0, 4) + " **** **** " + card.substring(card.length() - 4);
    }

    /**
     * 邮箱脱敏：a***@example.com
     */
    public static String email(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * 姓名脱敏：张** / 欧阳**
     */
    public static String name(String name) {
        if (!StringUtils.hasText(name) || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
