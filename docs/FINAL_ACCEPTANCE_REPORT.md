# 最终验收记录

日期：2026-07-01

## 验收范围

- 后端工程：`framework-*` 基础能力模块、`admin-service` 聚合后台服务、`demo` 示例服务。
- 前端工程：`frontend/admin-web` 后台管理端。
- 数据库：统一使用 MySQL，聚合初始化脚本为 `sql/mysql/framework_boot_starter_init.sql`。
- 默认账号：`admin / Admin@123`。

## 当前模块边界

- `framework-*`：只承载工程级通用能力，包括核心返回模型、Web、认证、权限、安全、缓存、锁、幂等、加密、日志、限流、MQ、重试、工具、通知、本地消息、Excel、多数据源、Redis、Feign、监控、XXL-Job、文件和 starter 汇总。
- `admin-service`：承载后台管理聚合能力，按业务域分包，包括认证、系统管理、在线会话、审计日志、MQ 消息链路与死信管理、本地消息表、通知、文件、Excel、监控和看板。
- `frontend/admin-web`：后台管理页面，保持原有业务逻辑，视觉风格靠近 NewAPI 的浅色文档/控制台风格。

## 工程约束

- Mapper 使用 MyBatis 注解方式，不使用 XML mapper。
- 主代码不使用 `JdbcTemplate`、`NamedParameterJdbcTemplate`、`GeneratedKeyHolder`。
- SQL 查询避免 `SELECT *`，使用明确投影列。
- 事务只保证本地事务，复杂写入使用手动事务控制。
- MQ 保留 RocketMQ、Kafka、RabbitMQ 适配思路，并提供失败消息、死信队列、人工补偿和链路查询的后台入口。
- `job` 模块定位为 XXL-Job 集成，不承载业务调度逻辑。
- 不保留代码生成模块。

## 本轮验收结果

- `mvn test`：通过。
- `mvn package -DskipTests`：通过。
- `npm --prefix frontend/admin-web run build`：通过。
- `git diff --check`：通过。
- 后端健康检查：`http://localhost:8081/actuator/health` 返回 `UP`。
- 前端访问检查：`http://localhost:5173/` 返回 `200`。
- 登录烟测：`admin / Admin@123` 登录成功。

## 核心接口烟测

以下接口均使用登录后的 Bearer Token 验证通过：

- `/admin/auth/me`
- `/admin/dashboard`
- `/admin/sessions?pageNum=1&pageSize=5`
- `/admin/mq/stats`
- `/admin/local-messages?pageNum=1&pageSize=5`
- `/admin/logs?pageNum=1&pageSize=5`
- `/admin/monitor/health`
- `/admin/notify/stats`
- `/admin/files?pageNum=1&pageSize=5`
- `/admin/excel/tasks?pageNum=1&pageSize=5`

## 后续可选优化

- 前端生产包里 Element Plus chunk 较大，第一版可接受；后续如果管理端继续扩展，可以再做路由级拆包和图表按需加载。
- 当前后台聚合在 `admin-service` 内按包拆分，第一版利于交付；后续当某个后台域独立增长明显时，再拆成独立 service。
