# admin-service

`admin-service` 是工程管理后台服务，不是底层 framework starter。它负责聚合 `framework-*`
能力，并向前端 `admin-web` 提供后台管理 API。

## Maven 坐标

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>admin-service</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 模块边界

| 分包 | 职责 |
| --- | --- |
| `dashboard` | 后台首页统计、模块状态 |
| `auth` | 后台登录、退出、当前用户、菜单权限 |
| `system` | 租户、部门、用户、角色、菜单、字典、参数 |
| `mq` | MQ 失败消息、死信、人工补偿、队列信息 |
| `localmessage` | 本地消息表查询、重试、终止处理 |
| `notify` | 通知模板、发送测试、发送记录 |
| `excel` | Excel 导出任务、导入失败登记、错误明细 |
| `log` | 操作/API/异常日志查询、traceId 查询 |
| `audit` | 后台关键管理动作强制审计落库 |
| `monitor` | 健康检查、运行时状态入口 |

`framework-mq`、`framework-log`、`framework-local-message` 等模块继续只提供基础能力；
后台查询、重试、补偿和页面入口统一放在 `admin-service`。

数据访问保持 MVC 三层边界：Controller 只做 HTTP 入参出参，Service 负责校验、编排和审计，
Repository 通过注解 Mapper 访问 MySQL。多表写操作只使用本地手动事务边界，不使用声明式
`@Transactional`。

后台权限不是只靠前端菜单隐藏：除登录/当前用户/退出外，管理接口必须在 Controller 上声明
`@RequirePermission`，后端根据登录会话里的权限点做硬校验。

## 启动

```bash
mvn -pl admin-service -am package -DskipTests
java -jar admin-service/target/admin-service-1.0.0.jar
```

默认使用 MySQL。`admin-service` 随包携带后台管理表初始化脚本：

```bash
mysql -uroot -proot framework_admin < admin-service/src/main/resources/db/mysql/admin_service.sql
```

如果要一次性初始化整个脚手架的框架表和后台表，也可以使用工程聚合脚本：

```bash
mysql -uroot -proot framework_admin < sql/mysql/framework_boot_starter_init.sql
```

本地启动时 `spring.sql.init.schema-locations` 会优先加载 classpath 下的
`db/mysql/admin_service.sql`，根目录聚合脚本用于源码工程内一键初始化。

默认后台账号：

| 用户名 | 密码 |
| --- | --- |
| `admin` | `Admin@123` |

前端入口在 `frontend/admin-web`，Compose 一键启动：

```bash
./scripts/start-compose.sh
```

`admin-service` 默认关闭 actuator 对 Redis/Rabbit/Kafka 的健康检查，方便本地只依赖 MySQL 启动后台。
生产环境如需把中间件纳入 `/actuator/health`，可通过 `management.health.*.enabled=true` 打开。

## API

| 功能 | 方法 | 路径 |
| --- | --- | --- |
| 登录 | POST | `/admin/auth/login` |
| 当前用户 | GET | `/admin/auth/me` |
| 退出 | POST | `/admin/auth/logout` |
| 租户列表 | GET | `/admin/system/tenants` |
| 新增租户 | POST | `/admin/system/tenants` |
| 更新租户 | PUT | `/admin/system/tenants/{id}` |
| 删除租户 | DELETE | `/admin/system/tenants/{id}` |
| 部门树 | GET | `/admin/system/depts` |
| 新增部门 | POST | `/admin/system/depts` |
| 更新部门 | PUT | `/admin/system/depts/{id}` |
| 删除部门 | DELETE | `/admin/system/depts/{id}` |
| 用户列表 | GET | `/admin/system/users` |
| 新增用户 | POST | `/admin/system/users` |
| 更新用户 | PUT | `/admin/system/users/{id}` |
| 更新用户状态 | PUT | `/admin/system/users/{id}/status` |
| 重置密码 | PUT | `/admin/system/users/{id}/password` |
| 删除用户 | DELETE | `/admin/system/users/{id}` |
| 角色列表 | GET | `/admin/system/roles` |
| 新增角色 | POST | `/admin/system/roles` |
| 更新角色 | PUT | `/admin/system/roles/{id}` |
| 删除角色 | DELETE | `/admin/system/roles/{id}` |
| 角色菜单ID | GET | `/admin/system/roles/{id}/menu-ids` |
| 角色授权 | PUT | `/admin/system/roles/{id}/menus` |
| 菜单树 | GET | `/admin/system/menus` |
| 新增菜单 | POST | `/admin/system/menus` |
| 更新菜单 | PUT | `/admin/system/menus/{id}` |
| 删除菜单 | DELETE | `/admin/system/menus/{id}` |
| 字典类型 | GET | `/admin/system/dict-types` |
| 新增字典类型 | POST | `/admin/system/dict-types` |
| 更新字典类型 | PUT | `/admin/system/dict-types/{id}` |
| 删除字典类型 | DELETE | `/admin/system/dict-types/{id}` |
| 字典项 | GET | `/admin/system/dict-items` |
| 新增字典项 | POST | `/admin/system/dict-items` |
| 更新字典项 | PUT | `/admin/system/dict-items/{id}` |
| 删除字典项 | DELETE | `/admin/system/dict-items/{id}` |
| 系统参数 | GET | `/admin/system/configs` |
| 新增系统参数 | POST | `/admin/system/configs` |
| 更新系统参数 | PUT | `/admin/system/configs/{id}` |
| 删除系统参数 | DELETE | `/admin/system/configs/{id}` |
| Dashboard | GET | `/admin/dashboard` |
| trace 聚合详情 | GET | `/admin/traces/{traceId}` |
| MQ 统计 | GET | `/admin/mq/stats` |
| MQ 失败消息 | GET | `/admin/mq/failed-messages` |
| MQ 手动重试 | POST | `/admin/mq/failed-messages/{id}/retry` |
| MQ 人工补偿完成 | POST | `/admin/mq/failed-messages/{id}/manual-success` |
| MQ 人工终止 | POST | `/admin/mq/failed-messages/{id}/manual-failure` |
| MQ 删除失败记录 | DELETE | `/admin/mq/failed-messages/{id}` |
| MQ 清空已处理记录 | DELETE | `/admin/mq/failed-messages/clean` |
| 本地消息列表 | GET | `/admin/local-messages` |
| 本地消息重试 | POST | `/admin/local-messages/retry-due` |
| 本地消息人工成功 | POST | `/admin/local-messages/{id}/success` |
| 本地消息人工失败 | POST | `/admin/local-messages/{id}/failure` |
| 本地消息删除 | DELETE | `/admin/local-messages/{id}` |
| 通知统计 | GET | `/admin/notify/stats` |
| 通知模板 | GET | `/admin/notify/templates` |
| 新增通知模板 | POST | `/admin/notify/templates` |
| 更新通知模板 | PUT | `/admin/notify/templates/{id}` |
| 删除通知模板 | DELETE | `/admin/notify/templates/{id}` |
| 发送测试通知 | POST | `/admin/notify/templates/{id}/send-test` |
| 通知发送记录 | GET | `/admin/notify/records` |
| Excel 统计 | GET | `/admin/excel/stats` |
| Excel 任务 | GET | `/admin/excel/tasks` |
| 创建导出任务 | POST | `/admin/excel/tasks/export` |
| 登记导入失败任务 | POST | `/admin/excel/tasks/import-failure` |
| Excel 错误明细 | GET | `/admin/excel/tasks/{taskId}/errors` |
| 日志列表 | GET | `/admin/logs` |
| 登录日志 | GET | `/admin/logs/login` |
| traceId 查询 | GET | `/admin/logs/traces/{traceId}` |

Swagger UI: `/swagger-ui.html`

系统管理里的用户、角色、菜单、字典、参数变更会强制写入 `sys_operation_log`，
包含操作人、traceId、请求 URI、操作类型和关键参数。登录成功/失败会写入 `sys_login_log`，
前端“登录日志”页面可按用户名和登录结果筛选。

MQ 和本地消息的重试、人工成功、人工失败、删除、清理都会写入 `sys_operation_log`。
`framework-mq` 的失败消息使用 MySQL 存储时，即使当前服务没有配置具体 MQ sender，
后台仍可查询死信记录、执行人工补偿和删除清理；真正重发仍需要对应 RocketMQ、Kafka 或 RabbitMQ sender 可用。

通知中心和 Excel 中心属于 `admin-service` 的管理数据：通知模板、发送记录、Excel 任务、
导入错误明细统一落 MySQL。通知发送测试会调用 `framework-notify` 的通道门面，Excel 导出任务
会调用 `framework-excel` 真实生成 xlsx 字节，并把任务结果写入 `framework_excel_task`。
