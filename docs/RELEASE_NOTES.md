# 发布说明

版本：v1.0-final

日期：2026-07-01

## 交付定位

本版本交付一个 Java 工程脚手架第一版，核心目标是提供可直接启动、可扩展、可验收的工程级基础设施：

- 后端使用 Spring Boot 3、Java 17、MyBatis 注解 Mapper。
- 数据库统一使用 MySQL。
- 后台管理由 `admin-service` 聚合，先按 package 分包，不提前拆多个 service。
- 前端使用 Vue 3、Element Plus、Vite，页面风格靠近 NewAPI 的浅色控制台风格。
- 默认只保证本地事务，复杂写入通过手动事务控制。

## 后端模块

### 工程级 framework 模块

- `framework-core`：统一返回、异常、常量、Trace 上下文。
- `framework-web`：Web MVC、全局异常、SQL 注入防护。
- `framework-auth`：登录态、Token、在线会话、密码策略。
- `framework-security`：权限、数据范围、安全拦截。
- `framework-cache`：缓存抽象。
- `framework-lock`：分布式锁抽象。
- `framework-idempotent`：接口幂等。
- `framework-crypto`：密码与加密工具。
- `framework-log`：操作日志、TraceId 日志链路。
- `framework-rate-limiter`：限流能力。
- `framework-mq`：RocketMQ、Kafka、RabbitMQ 适配边界，以及失败消息、死信与补偿管理模型。
- `framework-retry`：重试基础能力。
- `framework-tools`：通用工具能力。
- `framework-notify`：通知模板与通知记录。
- `framework-local-message`：本地消息表与补偿模型。
- `framework-excel`：Excel 导入导出基础能力。
- `framework-datasource`：MyBatis Plus 分页与元数据填充。
- `framework-redis`：Redis 基础配置。
- `framework-feign`：服务调用基础配置。
- `framework-monitor`：健康检查与运行状态。
- `framework-job`：XXL-Job 集成边界。
- `framework-file`：文件记录与后台管理基础能力。
- `framework-starter`：starter 汇总。

### 聚合后台服务

`admin-service` 保留为第一版后台聚合服务，内部按业务域分包：

- 认证登录、退出、当前用户、密码修改。
- 系统管理：用户、角色、菜单、部门、字典、配置。
- 在线会话：会话分页、按会话下线、按用户下线。
- 审计日志：操作日志、登录日志。
- MQ 管理：统计、失败消息、死信、重试、忽略、人工补偿。
- 本地消息：消息分页、成功/失败标记、删除和补偿入口。
- 通知管理：模板、记录、统计。
- 文件管理：文件列表、上传、状态统计。
- Excel 中心：任务列表、导出任务、导入失败任务、错误明细。
- 监控看板：健康、内存、线程、运行信息。

## 前端页面

后台前端位于 `frontend/admin-web`，已覆盖：

- 登录页。
- 总览看板。
- 用户、角色、菜单、部门、字典、配置。
- 在线会话。
- 操作日志、登录日志。
- MQ 消息管理。
- 本地消息表。
- 通知中心。
- 文件中心。
- Excel 中心。
- 系统监控。

## 数据库

聚合初始化脚本：

```bash
mysql -uroot -proot framework_admin < sql/mysql/framework_boot_starter_init.sql
```

脚本包含：

- 系统后台表。
- 操作日志表。
- 登录日志表。
- MQ 失败消息表。
- 本地消息表。
- 通知模板与通知记录表。
- Excel 任务与错误明细表。
- 文件记录表。
- 默认租户、部门、角色、用户、菜单、字典、配置和通知模板数据。

默认数据库配置：

- 数据库：`framework_admin`
- 用户名：`root`
- 密码：`root`
- 后端端口：`8081`
- 前端端口：`5173`
- 默认账号：`admin / Admin@123`

## 验收结果

本版本已完成以下验收：

- 后端全量测试通过：`mvn test`
- 后端打包通过：`mvn package -DskipTests`
- 前端生产构建通过：`npm --prefix frontend/admin-web run build`
- 格式检查通过：`git diff --check`
- 后端健康检查通过：`/actuator/health`
- 前端首页访问通过：`http://localhost:5173/`
- 登录烟测通过：`admin / Admin@123`
- 核心后台接口烟测通过：认证、看板、会话、MQ、本地消息、日志、监控、通知、文件、Excel。

## 已移除或不纳入第一版

- 不保留 `codegen` 代码生成模块。
- 不把偏业务后台能力拆成多个 service，第一版先由 `admin-service` 聚合。
- 不做分布式事务，只保留本地事务和手动事务控制。
- `job` 不做自研调度，只作为 XXL-Job 集成边界。

## 后续可选优化

- 前端 Element Plus chunk 较大，后续可按路由和图表做拆包。
- 当后台某个业务域明显膨胀后，再从 `admin-service` 拆成独立 service。
- 生产环境可补充 Docker Compose、Nginx、外部化配置和 CI 发布流水线。
- MQ 的 RocketMQ、Kafka、RabbitMQ 可继续补真实中间件集成样例。
