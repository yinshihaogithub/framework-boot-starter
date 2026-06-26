package com.framework.log.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具
 * 序列化参数时对敏感字段自动脱敏
 */
public class LogDesensitizeUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 需要脱敏的字段名（不区分大小写匹配） */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "pwd", "passwd", "secret", "token", "accesstoken",
            "refreshtoken", "apikey", "privatekey"
    );

    /** 需要部分脱敏的字段（手机/身份证/银行卡/邮箱） */
    private static final Set<String> PARTIAL_FIELDS = Set.of(
            "phone", "mobile", "telephone", "idcard", "bankcard", "email"
    );

    /** 手机号脱敏正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");
    /** 身份证脱敏正则 */
    private static final Pattern IDCARD_PATTERN = Pattern.compile("(\\d{3})\\d{11}(\\d{4})");
    /** 邮箱脱敏正则 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9])[a-zA-Z0-9._-]*@([a-zA-Z0-9.-]+)");
    /** key=value 敏感参数，覆盖 query string / form-urlencoded 日志 */
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(^|[?&\\s,;])([^=\\s&;,?]+)=([^&\\s;,]+)");

    /**
     * 对 JSON 字符串中的敏感字段进行脱敏
     *
     * @param json 原始 JSON 字符串
     * @return 脱敏后的 JSON 字符串
     */
    public static String desensitize(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        try {
            var root = MAPPER.readTree(json);
            desensitizeNode(root);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // 非 JSON 格式，做正则替换
            return desensitizeByRegex(json);
        }
    }

    /**
     * 递归脱敏 JSON 节点
     */
    private static void desensitizeNode(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode element : node) {
                desensitizeNode(element);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        // 不直接修改不可变节点，需要转换为可变节点
        if (!(node instanceof ObjectNode objectNode)) {
            return;
        }
        var fields = new java.util.ArrayList<String>();
        objectNode.fieldNames().forEachRemaining(fields::add);

        for (String fieldName : fields) {
            com.fasterxml.jackson.databind.JsonNode value = objectNode.get(fieldName);
            String lowerName = fieldName.toLowerCase();

            if (SENSITIVE_FIELDS.contains(lowerName)) {
                // 完全隐藏
                objectNode.put(fieldName, "***");
            } else if (PARTIAL_FIELDS.contains(lowerName) && value.isTextual()) {
                // 部分脱敏
                objectNode.put(fieldName, desensitizeValue(lowerName, value.asText()));
            } else if (value.isObject()) {
                desensitizeNode(value);
            } else if (value.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode element : value) {
                    desensitizeNode(element);
                }
            }
        }
    }

    /**
     * 对值进行部分脱敏
     */
    private static String desensitizeValue(String fieldName, String value) {
        return switch (fieldName) {
            case "phone", "mobile", "telephone" ->
                    PHONE_PATTERN.matcher(value).replaceAll("$1****$2");
            case "idcard" ->
                    IDCARD_PATTERN.matcher(value).replaceAll("$1***********$2");
            case "bankcard" -> {
                if (value.length() >= 8) {
                    yield value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
                }
                yield value;
            }
            case "email" ->
                    EMAIL_PATTERN.matcher(value).replaceAll("$1***@$2");
            default -> value;
        };
    }

    /**
     * 非 JSON 字符串的正则脱敏
     */
    private static String desensitizeByRegex(String text) {
        String result = desensitizeKeyValuePairs(text);
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        result = IDCARD_PATTERN.matcher(result).replaceAll("$1***********$2");
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***@$2");
        return result;
    }

    private static String desensitizeKeyValuePairs(String text) {
        Matcher matcher = KEY_VALUE_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String separator = matcher.group(1);
            String key = matcher.group(2);
            String value = matcher.group(3);
            String replacement = separator + key + "=" + desensitizeValueByKey(key, value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String desensitizeValueByKey(String key, String value) {
        String lowerName = key.toLowerCase();
        if (SENSITIVE_FIELDS.contains(lowerName)) {
            return "***";
        }
        if (PARTIAL_FIELDS.contains(lowerName)) {
            return desensitizeValue(lowerName, value);
        }
        return value;
    }
}
