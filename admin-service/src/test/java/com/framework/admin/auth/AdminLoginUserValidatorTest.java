package com.framework.admin.auth;

import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemMapperSupport;
import com.framework.auth.context.LoginUser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLoginUserValidatorTest {

    private final FakeMapperSupport mapperSupport = new FakeMapperSupport();
    private final AdminLoginUserValidator validator = new AdminLoginUserValidator(mapperSupport);

    @Test
    void acceptsEnabledUser() {
        mapperSupport.user = user("ENABLED");

        assertThat(validator.isValid(new LoginUser().setUserId(1L))).isTrue();
    }

    @Test
    void rejectsDisabledOrMissingUser() {
        mapperSupport.user = user("DISABLED");

        assertThat(validator.isValid(new LoginUser().setUserId(1L))).isFalse();

        mapperSupport.user = null;
        assertThat(validator.isValid(new LoginUser().setUserId(1L))).isFalse();
        assertThat(validator.isValid(new LoginUser())).isFalse();
        assertThat(validator.isValid(null)).isFalse();
    }

    private static AdminUser user(String status) {
        return new AdminUser()
                .setId(1L)
                .setStatus(status);
    }

    private static class FakeMapperSupport extends AdminSystemMapperSupport {
        private AdminUser user;

        private FakeMapperSupport() {
            super(null);
        }

        @Override
        public Optional<AdminUser> findUserById(Long id) {
            return user != null && user.getId().equals(id) ? Optional.of(user) : Optional.empty();
        }
    }
}
