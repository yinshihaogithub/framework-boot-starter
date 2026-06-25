package com.framework.core.exception;

import com.framework.core.result.ResultCode;

/**
 * 参数校验异常
 */
public class ParamException extends BusinessException {

    public ParamException(String message) {
        super(ResultCode.PARAM_ERROR, message);
    }
}
