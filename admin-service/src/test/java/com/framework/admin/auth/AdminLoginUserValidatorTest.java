package com.framework.admin.auth;

import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.auth.context.LoginUser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLoginUserValidatorTest {

    private final FakeRepository repository = new FakeRepository();
    private final AdminLoginUserValidator validator = new AdminLoginUserValidator(repository);

    @Test
    void acceptsEnabledUser() {
        repository.user = user("ENABLED");

        assertThat(validator.isValid(new LoginUser().setUserId(1L))).isTrue();
    }

    @Test
    void rejectsDisabledOrMissingUser() {
        repository.user = user("DISABLED");

        assertThat(validator.isValid(new LoginUser().setUserId(1L))).isFalse();

        repository.user = null;
        assertThat(validator.isValid(new LoginUser().setUserId(1L))).isFalse();
        assertThat(validator.isValid(new LoginUser())).isFalse();
        assertThat(validator.isValid(null)).isFalse();
    }

    private static AdminUser user(String status) {
        return new AdminUser()
                .setId(1L)
                .setStatus(status);
    }

    private static class FakeRepository extends AdminSystemRepository {
        private AdminUser user;

        private FakeRepository() {
            super(null);
        }

        @Override
        public Optional<AdminUser> findUserById(Long id) {
            return user != null && user.getId().equals(id) ? Optional.of(user) : Optional.empty();
        }
    }
}
