package com.framework.core.exception;

import com.framework.core.result.ResultCode;

/**
 * 权限异常
 */
public class PermissionException extends BusinessException {

    public PermissionException(String message) {
        super(ResultCode.PERMISSION_DENIED, message);
    }

    public PermissionException() {
        super(ResultCode.PERMISSION_DENIED);
    }
}
