package com.framework.auth.service;

/**
 * Sends SMS verification codes.
 */
@FunctionalInterface
public interface SmsSender {

    void send(String phone, String code, long expireSeconds);
}
