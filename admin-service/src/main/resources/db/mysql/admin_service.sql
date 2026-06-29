-- Admin service MySQL initialization script.
-- Framework module tables stay in their own module scripts; this file owns admin-service management tables and seeds.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT NOT NULL DEFAULT 0,
    dept_name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_parent (tenant_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    dept_id BIGINT NULL,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64),
    mobile VARCHAR(32),
    email VARCHAR(128),
    password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    last_login_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (tenant_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台角色表';

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_type VARCHAR(32) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    route_path VARCHAR(256),
    component VARCHAR(256),
    permission VARCHAR(128),
    icon VARCHAR(64),
    sort_order INT NOT NULL DEFAULT 0,
    visible TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_permission (permission),
    INDEX idx_parent_sort (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台菜单与权限表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dict_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dict_item (dict_code, item_value),
    INDEX idx_dict_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_name VARCHAR(128) NOT NULL,
    config_value TEXT,
    `sensitive` TINYINT(1) NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数表';

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64),
    user_id BIGINT,
    client_ip VARCHAR(64),
    success TINYINT(1) NOT NULL DEFAULT 0,
    message VARCHAR(512),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username_time (username, create_time),
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

CREATE TABLE IF NOT EXISTS framework_notify_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    receivers VARCHAR(1024),
    webhook_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_code (template_code),
    INDEX idx_channel_status (channel, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知模板表';

CREATE TABLE IF NOT EXISTS framework_notify_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(64),
    channel VARCHAR(32),
    title VARCHAR(256),
    content TEXT,
    receivers VARCHAR(1024),
    webhook_url VARCHAR(512),
    success TINYINT(1) NOT NULL DEFAULT 0,
    result_message VARCHAR(512),
    trace_id VARCHAR(64),
    operator_name VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_template_time (template_code, create_time),
    INDEX idx_channel_success (channel, success),
    INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送记录表';

CREATE TABLE IF NOT EXISTS framework_excel_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(128) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    biz_type VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    filename VARCHAR(256),
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    failure_rows INT NOT NULL DEFAULT 0,
    operator_name VARCHAR(64),
    error_message TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type_status (task_type, status),
    INDEX idx_biz_type (biz_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Excel导入导出任务表';

CREATE TABLE IF NOT EXISTS framework_excel_error (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    row_index INT NOT NULL DEFAULT 0,
    error_message TEXT,
    raw_data TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_row (task_id, row_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Excel导入错误明细表';

CREATE TABLE IF NOT EXISTS framework_file_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_key VARCHAR(256) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    file_size BIGINT NOT NULL DEFAULT 0,
    url VARCHAR(512),
    storage_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    business_type VARCHAR(64),
    business_key VARCHAR(256),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_file_key (file_key),
    INDEX idx_business (business_type, business_key),
    INDEX idx_deleted_time (deleted, create_time),
    INDEX idx_operator_time (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

INSERT INTO sys_tenant (id, tenant_code, tenant_name, status)
VALUES (1, 'default', '默认租户', 'ENABLED')
ON DUPLICATE KEY UPDATE tenant_name = VALUES(tenant_name), status = VALUES(status);

INSERT INTO sys_dept (id, tenant_id, parent_id, dept_name, sort_order, status)
VALUES (1, 1, 0, '总部', 1, 'ENABLED')
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name), status = VALUES(status);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, sort_order, status)
VALUES (1, 1, 'SUPER_ADMIN', '超级管理员', 1, 'ENABLED')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), status = VALUES(status);

INSERT INTO sys_user (id, tenant_id, dept_id, username, nickname, mobile, email, password_hash, status)
VALUES (1, 1, 1, 'admin', '系统管理员', '13800000000', 'admin@example.com',
        '$2a$10$iTzdWgLkeKBCb.m7F1DTd.mANVTILQBpxCa.U2YJ/iHUT6ZhU2NX2', 'ENABLED')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), password_hash = VALUES(password_hash), status = VALUES(status);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

INSERT INTO sys_menu (id, parent_id, menu_type, menu_name, route_path, component, permission, icon, sort_order, visible)
VALUES
    (1, 0, 'MENU', '数据看板', 'dashboard', 'Dashboard', 'dashboard:view', 'DataBoard', 10, 1),
    (2, 0, 'MENU', '系统管理', 'system', 'Layout', 'system:view', 'Setting', 20, 1),
    (3, 2, 'MENU', '用户管理', 'system/users', 'SystemUsers', 'system:user:view', 'User', 23, 1),
    (4, 2, 'MENU', '角色管理', 'system/roles', 'SystemRoles', 'system:role:view', 'Avatar', 24, 1),
    (5, 2, 'MENU', '菜单管理', 'system/menus', 'SystemMenus', 'system:menu:view', 'Menu', 25, 1),
    (6, 2, 'MENU', '字典管理', 'system/dicts', 'SystemDicts', 'system:dict:view', 'Collection', 26, 1),
    (7, 2, 'MENU', '参数配置', 'system/configs', 'SystemConfigs', 'system:config:view', 'Tools', 27, 1),
    (8, 0, 'MENU', '日志中心', 'logs', 'Logs', 'log:view', 'Document', 30, 1),
    (9, 0, 'MENU', '链路追踪', 'trace', 'Trace', 'trace:view', 'Search', 40, 1),
    (10, 0, 'MENU', 'MQ 管理', 'mq', 'Mq', 'mq:view', 'Connection', 50, 1),
    (11, 0, 'MENU', '本地消息', 'local-message', 'LocalMessage', 'local-message:view', 'Tickets', 60, 1),
    (12, 0, 'MENU', '监控中心', 'monitor', 'Monitor', 'monitor:view', 'Monitor', 70, 1),
    (13, 0, 'MENU', '通知中心', 'notify', 'Notify', 'notify:view', 'Bell', 80, 1),
    (14, 0, 'MENU', 'Excel 中心', 'excel', 'Excel', 'excel:view', 'Files', 90, 1),
    (20, 0, 'MENU', '文件中心', 'files', 'Files', 'file:view', 'FolderOpened', 95, 1),
    (15, 2, 'MENU', '租户管理', 'system/tenants', 'SystemTenants', 'system:tenant:view', 'OfficeBuilding', 21, 1),
    (16, 2, 'MENU', '部门管理', 'system/depts', 'SystemDepts', 'system:dept:view', 'MostlyCloudy', 22, 1),
    (18, 0, 'MENU', '登录日志', 'login-logs', 'LoginLogs', 'log:login:view', 'Document', 31, 1),
    (19, 0, 'MENU', '在线会话', 'sessions', 'Sessions', 'session:view', 'Monitor', 32, 1),
    (101, 3, 'BUTTON', '新增用户', NULL, NULL, 'system:user:create', NULL, 1, 0),
    (102, 3, 'BUTTON', '重置密码', NULL, NULL, 'system:user:reset-password', NULL, 2, 0),
    (103, 10, 'BUTTON', '消息重试', NULL, NULL, 'mq:retry', NULL, 1, 0),
    (104, 11, 'BUTTON', '本地消息重试', NULL, NULL, 'local-message:retry', NULL, 1, 0),
    (105, 13, 'BUTTON', '发送测试通知', NULL, NULL, 'notify:send-test', NULL, 1, 0),
    (133, 13, 'BUTTON', '新增通知模板', NULL, NULL, 'notify:template:create', NULL, 2, 0),
    (134, 13, 'BUTTON', '更新通知模板', NULL, NULL, 'notify:template:update', NULL, 3, 0),
    (135, 13, 'BUTTON', '删除通知模板', NULL, NULL, 'notify:template:delete', NULL, 4, 0),
    (106, 14, 'BUTTON', '创建Excel任务', NULL, NULL, 'excel:task:create', NULL, 1, 0),
    (131, 20, 'BUTTON', '上传文件', NULL, NULL, 'file:upload', NULL, 1, 0),
    (132, 20, 'BUTTON', '删除文件', NULL, NULL, 'file:delete', NULL, 2, 0),
    (107, 15, 'BUTTON', '新增租户', NULL, NULL, 'system:tenant:create', NULL, 1, 0),
    (108, 16, 'BUTTON', '新增部门', NULL, NULL, 'system:dept:create', NULL, 1, 0),
    (109, 15, 'BUTTON', '更新租户', NULL, NULL, 'system:tenant:update', NULL, 2, 0),
    (110, 15, 'BUTTON', '删除租户', NULL, NULL, 'system:tenant:delete', NULL, 3, 0),
    (111, 16, 'BUTTON', '更新部门', NULL, NULL, 'system:dept:update', NULL, 2, 0),
    (112, 16, 'BUTTON', '删除部门', NULL, NULL, 'system:dept:delete', NULL, 3, 0),
    (113, 3, 'BUTTON', '更新用户', NULL, NULL, 'system:user:update', NULL, 3, 0),
    (114, 3, 'BUTTON', '更新用户状态', NULL, NULL, 'system:user:update-status', NULL, 4, 0),
    (115, 3, 'BUTTON', '删除用户', NULL, NULL, 'system:user:delete', NULL, 5, 0),
    (130, 3, 'BUTTON', '解锁用户', NULL, NULL, 'system:user:unlock', NULL, 6, 0),
    (116, 4, 'BUTTON', '新增角色', NULL, NULL, 'system:role:create', NULL, 1, 0),
    (117, 4, 'BUTTON', '更新角色', NULL, NULL, 'system:role:update', NULL, 2, 0),
    (118, 4, 'BUTTON', '删除角色', NULL, NULL, 'system:role:delete', NULL, 3, 0),
    (119, 4, 'BUTTON', '角色授权', NULL, NULL, 'system:role:authorize', NULL, 4, 0),
    (120, 5, 'BUTTON', '新增菜单', NULL, NULL, 'system:menu:create', NULL, 1, 0),
    (121, 5, 'BUTTON', '更新菜单', NULL, NULL, 'system:menu:update', NULL, 2, 0),
    (122, 5, 'BUTTON', '删除菜单', NULL, NULL, 'system:menu:delete', NULL, 3, 0),
    (123, 6, 'BUTTON', '新增字典', NULL, NULL, 'system:dict:create', NULL, 1, 0),
    (124, 6, 'BUTTON', '更新字典', NULL, NULL, 'system:dict:update', NULL, 2, 0),
    (125, 6, 'BUTTON', '删除字典', NULL, NULL, 'system:dict:delete', NULL, 3, 0),
    (126, 7, 'BUTTON', '新增参数', NULL, NULL, 'system:config:create', NULL, 1, 0),
    (127, 7, 'BUTTON', '更新参数', NULL, NULL, 'system:config:update', NULL, 2, 0),
    (128, 7, 'BUTTON', '删除参数', NULL, NULL, 'system:config:delete', NULL, 3, 0),
    (129, 19, 'BUTTON', '强制下线', NULL, NULL, 'session:kick', NULL, 1, 0)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), route_path = VALUES(route_path),
    component = VALUES(component), permission = VALUES(permission), icon = VALUES(icon),
    sort_order = VALUES(sort_order), visible = VALUES(visible);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

INSERT INTO sys_dict_type (id, dict_code, dict_name, status)
VALUES
    (1, 'common_status', '通用状态', 'ENABLED'),
    (2, 'message_status', '消息状态', 'ENABLED')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), status = VALUES(status);

INSERT INTO sys_dict_item (dict_code, item_label, item_value, sort_order, status)
VALUES
    ('common_status', '启用', 'ENABLED', 1, 'ENABLED'),
    ('common_status', '禁用', 'DISABLED', 2, 'ENABLED'),
    ('message_status', '待处理', 'PENDING', 1, 'ENABLED'),
    ('message_status', '处理中', 'PROCESSING', 2, 'ENABLED'),
    ('message_status', '成功', 'SUCCESS', 3, 'ENABLED'),
    ('message_status', '失败', 'FAILED', 4, 'ENABLED')
ON DUPLICATE KEY UPDATE item_label = VALUES(item_label), sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO sys_config (config_key, config_name, config_value, `sensitive`, remark)
VALUES
    ('admin.default.password.changed', '默认密码是否已修改', 'false', 0, '生产环境应强制修改默认管理员密码'),
    ('security.password.min-length', '密码最小长度', '8', 0, '后台用户密码策略'),
    ('notify.webhook.url', '告警 Webhook', '', 1, '通知告警目标地址')
ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), `sensitive` = VALUES(`sensitive`), remark = VALUES(remark);

INSERT INTO framework_notify_template
    (template_code, template_name, channel, title, content, receivers, webhook_url, status)
VALUES
    ('system-alert', '系统告警模板', 'LOG', '系统告警', '模块 ${module} 状态异常：${reason}', '', '', 'ENABLED')
ON DUPLICATE KEY UPDATE template_name = VALUES(template_name), channel = VALUES(channel),
    title = VALUES(title), content = VALUES(content), status = VALUES(status);
