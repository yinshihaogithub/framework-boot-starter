# Framework Boot Starter

Java 框架脚手架 —— 开箱即用的后端基础能力。

## 模块一览

| 模块 | 说明 |
|---|---|
| `framework-core` | 核心基础：统一响应 `Result<T>` / `PageResult<T>`、业务码 `ResultCode`、异常体系、常量 |
| `framework-web` | Web 层：全局异常处理、参数校验、CORS 跨域、链路追踪 traceId |
| `framework-auth` | 鉴权：JWT 工具、登录会话管理、Token 刷新/黑名单、多端互踢、TokenAuthFilter |
| `framework-security` | 权限：`@RequireLogin` / `@RequireRole` / `@RequirePermission` / `@IgnoreToken` 注解 + AOP |
| `framework-cache` | 缓存：Redis 封装、Caffeine 本地缓存、多级缓存 |
| `framework-lock` | 分布式锁：`@DistributedLock` 注解 + SpEL key + 看门狗续期 + fallback |
| `framework-idempotent` | 幂等性：`@Idempotent` 注解，Token/RequestHash/BusinessKey 三种策略 |
| `framework-crypto` | 加解密：AES/MD5/SHA、BCrypt 密码哈希、数据脱敏（手机/身份证/银行卡/邮箱/姓名） |
| `framework-log` | 操作日志：`@OperationLog` 注解 + 异步记录 + 全链路 traceId |
| `framework-rate-limiter` | 限流：`@RateLimit` 注解，全局/IP/用户维度，Redisson 滑动窗口 |
| `framework-mq` | 消息队列：RabbitMQ / RocketMQ / Kafka 发送适配、消费辅助、失败补偿、死信/重试管理 |
| `framework-retry` | 重试：`@Retry` 注解（固定/指数退避）、`@CircuitBreaker` 熔断降级（Resilience4j） |
| `framework-tools` | 工具库：雪花 ID 生成器、树形结构工具、日期工具 |
| `framework-notify` | 通知告警：统一消息模型、日志/Webhook 通道、短信/邮件扩展点 |
| `framework-local-message` | 本地消息表：消息落表、状态流转、失败重试、按 topic 分发 |
| `framework-excel` | Excel：EasyExcel 导入导出、模板生成、行级错误收集 |
| `framework-datasource` | 数据源：MyBatis-Plus 分页、审计字段自动填充、数据源配置 |
| `framework-redis` | Redis：Key 规范、通用 StringRedisTemplate 封装、轻量锁工具 |
| `framework-feign` | Feign：traceId/token/租户 Header 透传 |
| `framework-monitor` | 监控：Actuator 健康检查、框架健康指示器 |
| `framework-job` | 任务：Spring Scheduler 配置、JobHandler 注册和手动触发 |
| `framework-file` | 文件：统一文件模型、本地存储默认实现、对象存储扩展点 |
| `framework-starter` | 聚合 Starter：一次引入常用脚手架能力 |
| `demo` | 示例启动模块：演示全部能力 |

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 2. 启动基础组件

```bash
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=framework_demo mysql:8.0
docker run -d --name redis -p 6379:6379 redis:7
mysql -uroot -proot framework_demo < sql/mysql/framework_boot_starter_init.sql
```

### 3. 编译

```bash
cd framework-boot-starter
mvn clean install -DskipTests
```

### 4. 启动 Demo

```bash
cd demo
mvn spring-boot:run
```

### 5. 访问

- Swagger UI: http://localhost:8080/swagger-ui.html
- 公开接口: http://localhost:8080/api/demo/public
- 限流接口: http://localhost:8080/api/demo/rate-limit

## 注解速查

| 注解 | 模块 | 作用 |
|---|---|---|
| `@RequireLogin` | security | 必须登录 |
| `@RequireRole("ADMIN")` | security | 必须指定角色 |
| `@RequirePermission("user:add")` | security | 必须指定权限 |
| `@IgnoreToken` | security | 跳过鉴权 |
| `@DistributedLock(key = "order:#{#id}")` | lock | 分布式锁 |
| `@Idempotent(key = "order:#{#orderNo}")` | idempotent | 幂等防重 |
| `@RateLimit(limit = 10, window = 60)` | rate-limiter | 限流 |
| `@OperationLog(module = "用户管理", action = "新增")` | log | 操作日志 |
| `@Retry(maxAttempts = 3, strategy = EXPONENTIAL)` | retry | 自动重试 |
| `@CircuitBreaker(name = "service", fallback = "recover")` | retry | 熔断降级 |

## 统一响应

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1719235200000
}
```

## 配置说明

```yaml
framework:
  auth:
    enabled: true
    jwt:
      secret: your-secret-key-at-least-32-chars
      access-token-expire: 7200
      refresh-token-expire: 604800
    session-timeout: 7200
    white-list:
      - /api/demo/public
      - /auth/**
  cache:
    enabled: true
    multi-level: true
    local:
      max-size: 10000
      expire-after-write: 300
  log:
    enabled: true
    api-sample-rate: 0
    retention-days: 30
    db-storage:
      enabled: false
  mq:
    enabled: true
    provider: RABBIT
    auto-create-table: true
    failed-message-table-name: framework_mq_failed_message
    max-retry: 3
    dead-letter:
      enabled: true
      queue: framework.dead.letter.queue
    retry:
      fixed-delay: 30000
  lock:
    enabled: true
  idempotent:
    enabled: true
  rate-limiter:
    enabled: true
  notify:
    enabled: true
    default-channel: LOG
  local-message:
    enabled: true
    table-name: framework_local_message
    auto-create-table: true
  excel:
    enabled: true
    default-sheet-name: Sheet1
    max-rows: 100000
  datasource:
    enabled: true
    db-type: MYSQL
  redis:
    enabled: true
    key-prefix: framework
  feign:
    enabled: true
  monitor:
    enabled: true
  job:
    enabled: true
  file:
    enabled: true
    base-path: /tmp/framework-files
```

`framework.*` 主要模块已接入 Spring Boot 配置属性处理器，IDE 可提示配置项。`prod` / `production` profile 下必须显式配置 JWT secret。

## MySQL 初始化

所有框架表默认按 MySQL 设计。聚合脚本在 `sql/mysql/framework_boot_starter_init.sql`，模块内也各自携带：

| 模块 | 脚本 |
|---|---|
| `framework-log` | `framework-log/src/main/resources/db/mysql/sys_operation_log.sql` |
| `framework-local-message` | `framework-local-message/src/main/resources/db/mysql/framework_local_message.sql` |
| `framework-mq` | `framework-mq/src/main/resources/db/mysql/framework_mq.sql` |

## 全链路 TraceId

`framework-web` 会接收或生成 `X-Trace-Id`，写入 `MDC["traceId"]` 并回写响应头。
`framework-feign` 会把当前 traceId 透传到下游；`framework-log` 的异步日志线程池和操作日志异步记录会继承 MDC。
业务自定义线程池可复用 `TraceContextTaskDecorator` 传播 traceId。
