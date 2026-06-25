package com.framework.auth.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Default development SMS sender. Applications can replace it with a real provider bean.
 */
@Slf4j
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phone, String code, long expireSeconds) {
        log.info("[短信验证码] phone={}, code={}（有效期{}秒）", phone, code, expireSeconds);
    }
}
