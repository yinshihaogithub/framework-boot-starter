package com.framework.auth.service;

import com.framework.auth.context.LoginUser;

/**
 * Extension point for validating restored login users.
 */
public interface LoginUserValidator {

    boolean isValid(LoginUser user);
}
