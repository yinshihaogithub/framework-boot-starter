package com.framework.admin.auth;

import com.framework.admin.system.AdminSystemMapperSupport;
import com.framework.auth.context.LoginUser;
import com.framework.auth.service.LoginUserValidator;
import org.springframework.stereotype.Component;

/**
 * Ensures restored admin sessions still belong to an enabled account.
 */
@Component
public class AdminLoginUserValidator implements LoginUserValidator {

    private final AdminSystemMapperSupport repository;

    public AdminLoginUserValidator(AdminSystemMapperSupport repository) {
        this.repository = repository;
    }

    @Override
    public boolean isValid(LoginUser user) {
        if (user == null || user.getUserId() == null) {
            return false;
        }
        return repository.findUserById(user.getUserId())
                .map(adminUser -> "ENABLED".equals(adminUser.getStatus()))
                .orElse(false);
    }
}
