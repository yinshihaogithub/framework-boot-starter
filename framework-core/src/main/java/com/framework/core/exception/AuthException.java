package com.framework.core.exception;

import com.framework.core.result.ResultCode;

/**
 * 鉴权异常
 */
public class AuthException extends BusinessException {

    public AuthException(String message) {
        super(ResultCode.TOKEN_INVALID, message);
    }

    public AuthException(ResultCode resultCode) {
        super(resultCode);
    }

    public AuthException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }
}
