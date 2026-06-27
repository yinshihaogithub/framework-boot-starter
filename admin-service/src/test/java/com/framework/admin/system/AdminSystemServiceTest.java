package com.framework.admin.system;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.MenuRequest;
import com.framework.admin.system.AdminSystemModels.RoleRequest;
import com.framework.admin.system.AdminSystemModels.TenantRequest;
import com.framework.admin.system.AdminSystemModels.UserCreateRequest;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSystemServiceTest {

    private final FakeRepository repository = new FakeRepository();
    private final FakeAuditService auditService = new FakeAuditService();
    private final AdminSystemService service = new AdminSystemService(repository, auditService);

    @Test
    void usersSanitizesPagingAndPasswordHash() {
        repository.users = List.of(new AdminUser()
                .setId(2L)
                .setUsername("alice")
                .setPasswordHash("secret"));
        repository.userCount = 1;

        PageResult<AdminUser> page = service.users(null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getPasswordHash()).isNull();
        assertThat(repository.listUserPageNum).isEqualTo(1);
        assertThat(repository.listUserPageSize).isEqualTo(200);
    }

    @Test
    void createUserHashesPasswordAndWritesAudit() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("Pass@123");
        request.setRoleIds(List.of(1L));
        repository.nextUserId = 9L;

        Result<Long> result = service.createUser(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9L);
        assertThat(repository.createdUser).isSameAs(request);
        assertThat(repository.createdPasswordHash).isNotEqualTo("Pass@123");
        assertThat(PasswordUtils.verify("Pass@123", repository.createdPasswordHash)).isTrue();
        assertThat(auditService.actions).containsExactly("新增用户");
    }

    @Test
    void deleteTenantRejectsDefaultAndTenantWithUsers() {
        Result<String> defaultTenant = service.deleteTenant(1L, null);
        repository.tenantUserCount = 2;
        Result<String> occupiedTenant = service.deleteTenant(8L, null);

        assertThat(defaultTenant.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(defaultTenant.getMessage()).isEqualTo("默认租户不能删除");
        assertThat(occupiedTenant.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(occupiedTenant.getMessage()).isEqualTo("租户下存在用户，不能删除");
        assertThat(repository.deletedTenantId).isNull();
    }

    @Test
    void roleValidationRejectsInvalidStatus() {
        RoleRequest request = new RoleRequest();
        request.setRoleCode("OPS");
        request.setRoleName("运维");
        request.setStatus("LOCKED");

        Result<Long> result = service.createRole(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("状态只能是 ENABLED 或 DISABLED");
        assertThat(repository.createdRole).isNull();
    }

    @Test
    void menuValidationRejectsInvalidTypeAndSelfParent() {
        MenuRequest invalidType = new MenuRequest();
        invalidType.setMenuType("PAGE");
        invalidType.setMenuName("首页");

        MenuRequest selfParent = new MenuRequest();
        selfParent.setMenuType("MENU");
        selfParent.setMenuName("首页");
        selfParent.setParentId(7L);

        Result<Long> invalidTypeResult = service.createMenu(invalidType, null);
        Result<String> selfParentResult = service.updateMenu(7L, selfParent, null);

        assertThat(invalidTypeResult.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(invalidTypeResult.getMessage()).isEqualTo("菜单类型只能是 MENU 或 BUTTON");
        assertThat(selfParentResult.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(selfParentResult.getMessage()).isEqualTo("上级菜单不能选择自己");
    }

    @Test
    void createTenantValidatesRequiredFields() {
        TenantRequest request = new TenantRequest();
        request.setTenantCode("tenant-a");

        Result<Long> result = service.createTenant(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("租户编码和名称不能为空");
    }

    private static class FakeRepository extends AdminSystemRepository {
        private List<AdminUser> users = List.of();
        private long userCount;
        private int listUserPageNum;
        private int listUserPageSize;
        private Long nextUserId = 1L;
        private UserCreateRequest createdUser;
        private String createdPasswordHash;
        private long tenantUserCount;
        private Long deletedTenantId;
        private RoleRequest createdRole;

        private FakeRepository() {
            super(null);
        }

        @Override
        public List<AdminUser> listUsers(String keyword, String status, int pageNum, int pageSize) {
            this.listUserPageNum = pageNum;
            this.listUserPageSize = pageSize;
            return new ArrayList<>(users);
        }

        @Override
        public long countUsers(String keyword, String status) {
            return userCount;
        }

        @Override
        public Long createUser(UserCreateRequest request, String passwordHash) {
            this.createdUser = request;
            this.createdPasswordHash = passwordHash;
            return nextUserId;
        }

        @Override
        public long countUsersByTenant(Long tenantId) {
            return tenantUserCount;
        }

        @Override
        public void deleteTenant(Long id) {
            this.deletedTenantId = id;
        }

        @Override
        public Long createRole(RoleRequest request) {
            this.createdRole = request;
            return 3L;
        }
    }

    private static class FakeAuditService extends AdminAuditService {
        private final List<String> actions = new ArrayList<>();

        private FakeAuditService() {
            super(null, null);
        }

        @Override
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            actions.add(action);
        }
    }
}
