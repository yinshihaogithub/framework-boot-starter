# Framework Boot Starter 系统设计文档

> 版本：v1.0
> 日期：2026-06-24
> 关联文档：`java_scaffold_prd.md`（需求文档）、`README.md`（使用说明）

---

## 一、系统架构

### 1.1 整体架构

```
                          ┌─────────────────────────────────────────────────┐
                          │                   客户端层                        │
                          │   Web浏览器  /  移动端  /  第三方系统  /  IoT      │
                          └──────────────────────┬──────────────────────────┘
                                                 │ HTTPS
                          ┌──────────────────────▼──────────────────────────┐
                          │                   网关层                          │
                          │        Nginx / Spring Cloud Gateway              │
                          │  路由转发 · 限流熔断 · 黑白名单 · 灰度 · 鉴权      │
                          └──────────────────────┬──────────────────────────┘
                                                 │
                          ┌──────────────────────▼──────────────────────────┐
                          │                 应用服务层                        │
                          │  ┌──────────┐ ┌──────────┐ ┌──────────┐         │
                          │  │ 业务服务A │ │ 业务服务B │ │ 业务服务C │         │
                          │  │framework │ │framework │ │framework │         │
                          │  └────┬─────┘ └────┬─────┘ └────┬─────┘         │
                          └───────┼────────────┼────────────┼───────────────┘
                                  │            │            │
                    ┌─────────────┼────────────┼────────────┼─────────────┐
                    │             │            │            │             │
              ┌─────▼─────┐ ┌────▼────┐ ┌────▼────┐ ┌────▼────┐ ┌──────▼─────┐
              │   Redis   │ │  MySQL  │ │ RabbitMQ│ │  ES     │ │  对象存储   │
              │ 缓存/锁   │ │ 主从    │ │ 消息队列│ │ 搜索    │ │  MinIO/OSS │
              └───────────┘ └─────────┘ └─────────┘ └─────────┘ └────────────┘
                    │             │            │            │             │
              ┌─────▼─────────────▼────────────▼────────────▼─────────────▼─┐
              │                      监控运维层                                │
              │   Prometheus · Grafana · ELK · SkyWalking · AlertManager     │
              └─────────────────────────────────────────────────────────────┘
```

### 1.2 分层职责

| 层 | 职责 | 技术选型 |
|---|---|---|
| 客户端层 | 用户交互、请求发起 | 浏览器 / App / SDK |
| 网关层 | 统一入口、路由、限流、鉴权 | Nginx / Spring Cloud Gateway |
| 应用服务层 | 业务逻辑处理 | Spring Boot 3 + 本脚手架 |
| 数据存储层 | 数据持久化与缓存 | MySQL / Redis / RabbitMQ / ES |
| 监控运维层 | 可观测性、告警 | Prometheus / Grafana / ELK |

---

## 二、模块架构

### 2.1 模块依赖关系

```
                    framework-core（核心基础）
                   /        |          \
          framework-web   framework-tools  framework-crypto
              |                               |
        framework-auth ──────────── framework-security
              |
     ┌────────┼────────────────────────┐
     │        │                        │
framework-lock  framework-idempotent  framework-mq
     │        │                        │
     └────────┼────────────────────────┘
              │
     framework-rate-limiter
              │
     framework-retry
              │
     framework-log（横切，依赖 core）
              │
           demo（聚合所有模块）
```

### 2.2 模块分类

| 类别 | 模块 | 说明 |
|---|---|---|
| **核心层** | framework-core | 统一响应、异常体系、常量、业务码 |
| **Web 层** | framework-web | 全局异常处理、CORS、traceId、参数校验 |
| **安全层** | framework-auth, framework-security, framework-crypto | 鉴权、权限、加解密 |
| **可靠性层** | framework-lock, framework-idempotent, framework-retry | 分布式锁、幂等、重试熔断 |
| **中间件层** | framework-cache, framework-mq, framework-rate-limiter | 缓存、消息队列、限流 |
| **可观测层** | framework-log | 操作日志、链路追踪 |
| **工具层** | framework-tools | 雪花ID、树形工具、日期工具 |

### 2.3 横切关注点

```
                    请求流入
                       │
            ┌──────────▼──────────┐
            │   TraceIdFilter     │ ← 注入 traceId
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │   CorsFilter        │ ← 跨域处理
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │   TokenAuthFilter   │ ← JWT 校验 + 注入登录用户
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │  PermissionAspect   │ ← @RequireLogin / @RequirePermission
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │  RateLimitAspect    │ ← @RateLimit 限流
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │  IdempotentAspect   │ ← @Idempotent 幂等
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │ DistributedLockAspect│ ← @DistributedLock 分布式锁
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │  RetryAspect        │ ← @Retry 重试
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │   Controller        │ ← 业务方法
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │ OperationLogAspect  │ ← @OperationLog 操作日志
            └──────────┬──────────┘
                       │
            ┌──────────▼──────────┐
            │ GlobalExceptionHandler│ ← 异兜底
            └─────────────────────┘
```

---

## 三、技术选型

### 3.1 核心框架

| 组件 | 选型 | 版本 | 选型理由 |
|---|---|---|---|
| JDK | OpenJDK | 17 LTS | 长期支持，Spring Boot 3 最低要求 |
| 框架 | Spring Boot | 3.2.x | 生态成熟，Jakarta EE 9+ |
| ORM | MyBatis-Plus | 3.5.x | 灵活 SQL + 通用 CRUD，国内主流 |
| 缓存 | Redis + Redisson | 7.x / 3.31.x | 分布式锁/限流/缓存一体化 |
| 消息队列 | RabbitMQ | 3.13.x | 死信/延迟/重试队列完善，AMQP 标准 |
| API 文档 | SpringDoc | 2.3.x | OpenAPI 3 规范，替代 Swagger |
| JSON | Jackson | 2.16.x | Spring 默认，性能好 |
| 工具库 | Hutool | 5.8.x | 国产工具库，覆盖面广 |

### 3.2 安全选型

| 组件 | 选型 | 用途 |
|---|---|---|
| JWT | jjwt 0.12.x | Token 签发与校验 |
| 密码哈希 | BCrypt (jbcrypt) | 密码安全存储 |
| 加密 | AES-CBC / RSA / SM2-4 | 数据加解密 |
| 限流 | Redisson RRateLimiter | 分布式滑动窗口限流 |
| 熔断 | Resilience4j 2.2.x | 替代 Hystrix（已停更） |

### 3.3 为什么不用

| 方案 | 不选原因 |
|---|---|
| Spring Security | 过重，脚手架追求轻量，自定义 JWT + AOP 更灵活 |
| Hystrix | 已停止维护，Resilience4j 是官方推荐替代 |
| Sentinel | 功能强但依赖 Dashboard，脚手架用注解式更轻量 |
| XXL-JOB | 任务调度独立于脚手架核心，按需引入 |
| ShardingSphere | 分库分表按需引入，不内置 |

---

## 四、核心流程设计

### 4.1 请求处理全链路

```
HTTP 请求
  │
  ├─ 1. TraceIdFilter      → 生成/透传 traceId，写入 MDC
  ├─ 2. CorsFilter         → 跨域预检
  ├─ 3. TokenAuthFilter    → 解析 JWT，注入 UserContextHolder
  ├─ 4. DispatcherServlet  → 路由到 Controller
  │    ├─ 4a. PermissionAspect    → @RequireLogin / @RequireRole
  │    ├─ 4b. RateLimitAspect     → @RateLimit Redisson 限流
  │    ├─ 4c. IdempotentAspect    → @Idempotent Redis SETNX 防重
  │    ├─ 4d. DistributedLockAspect → @DistributedLock Redisson 加锁
  │    ├─ 4e. RetryAspect         → @Retry 重试 + 指数退避
  │    ├─ 4f. Controller 方法执行
  │    └─ 4g. OperationLogAspect  → @OperationLog 异步记录
  │
  ├─ 5. GlobalExceptionHandler → 异常统一转 Result
  └─ 6. 返回 HTTP 响应
```

### 4.2 登录认证流程

```
客户端                    网关/Filter              认证服务                Redis
  │                          │                       │                     │
  │── POST /auth/login ────→│                       │                     │
  │   {username,password,   │──白名单放行──────────→│                     │
  │    captcha,captchaKey}  │                       │──校验验证码────────→│
  │                          │                       │──查用户(DB)          │
  │                          │                       │──BCrypt校验密码      │
  │                          │                       │──检查账号状态        │
  │                          │                       │──多端互踢检查──────→│
  │                          │                       │──生成JWT             │
  │                          │                       │──存会话────────────→│
  │                          │                       │   session:{uid}:{dev}│
  │                          │←── {accessToken,     │                     │
  │                          │     refreshToken,    │                     │
  │                          │     userInfo}        │                     │
  │←────────────────────────│                       │                     │
```

### 4.3 幂等消费流程（MQ）

```
Producer                    RabbitMQ                 Consumer                Redis
  │                           │                        │                      │
  │── send(businessKey,msg) ─→│                        │                      │
  │                           │── 投递到队列 ─────────→│                      │
  │                           │                        │──幂等检查──────────→│
  │                           │                        │  key=businessKey     │
  │                           │                        │←── 未消费 ──────────│
  │                           │                        │──执行业务             │
  │                           │                        │──标记已消费────────→│
  │                           │                        │  TTL=7天             │
  │                           │←── ACK ───────────────│                      │
  │                           │                        │                      │
  │   （重复投递）              │── 重复投递 ──────────→│                      │
  │                           │                        │──幂等检查──────────→│
  │                           │                        │←── 已消费 ──────────│
  │                           │←── ACK(跳过) ─────────│                      │
```

### 4.4 分布式锁流程

```
线程A                     Redisson                  Redis                   线程B
  │                          │                        │                      │
  │── tryLock(key,3s,-1) ──→│                        │                      │
  │                          │── SETNX ─────────────→│                      │
  │                          │←── 成功 ──────────────│                      │
  │←── 获取锁成功 ──────────│                        │                      │
  │                          │── 启动看门狗 ─────────→│ (每10s续期30s)       │
  │                          │                        │                      │
  │── 执行业务 ──────────────│                        │                      │
  │                          │                        │  线程B tryLock ─────→│
  │                          │                        │←── 失败(已锁) ──────│
  │                          │                        │  线程B 等待3s超时    │
  │                          │                        │                      │
  │── unlock ──────────────→│                        │                      │
  │                          │── DEL ───────────────→│                      │
  │                          │── 停止看门狗            │                      │
```

---

## 五、数据模型设计

### 5.1 核心表结构

#### 用户体系

```sql
-- 用户表
CREATE TABLE sys_user (
    id              BIGINT PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    password        VARCHAR(128) NOT NULL COMMENT 'BCrypt哈希',
    real_name       VARCHAR(64),
    phone           VARCHAR(20),
    email           VARCHAR(128),
    avatar          VARCHAR(256),
    tenant_id       VARCHAR(32) NOT NULL,
    status          TINYINT DEFAULT 1 COMMENT '1正常 0禁用',
    login_fail_count INT DEFAULT 0,
    lock_until      DATETIME COMMENT '锁定到期时间',
    last_login_time DATETIME,
    last_login_ip   VARCHAR(64),
    create_time     DATETIME DEFAULT NOW(),
    update_time     DATETIME DEFAULT NOW(),
    deleted         TINYINT DEFAULT 0,
    INDEX idx_tenant_username (tenant_id, username)
);

-- 角色表
CREATE TABLE sys_role (
    id          BIGINT PRIMARY KEY,
    role_code   VARCHAR(64) NOT NULL UNIQUE,
    role_name   VARCHAR(64) NOT NULL,
    tenant_id   VARCHAR(32) NOT NULL,
    status      TINYINT DEFAULT 1,
    sort        INT DEFAULT 0,
    create_time DATETIME DEFAULT NOW(),
    INDEX idx_tenant (tenant_id)
);

-- 权限表
CREATE TABLE sys_permission (
    id              BIGINT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL UNIQUE COMMENT '如 user:add',
    permission_name VARCHAR(64) NOT NULL,
    type            TINYINT COMMENT '1菜单 2按钮 3接口',
    parent_id       BIGINT DEFAULT 0,
    sort            INT DEFAULT 0
);

-- 用户-角色关联
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 角色-权限关联
CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);
```

#### 操作日志

```sql
CREATE TABLE sys_operation_log (
    id          BIGINT PRIMARY KEY,
    trace_id    VARCHAR(32) COMMENT '链路ID',
    user_id     BIGINT,
    username    VARCHAR(64),
    tenant_id   VARCHAR(32),
    module      VARCHAR(64) COMMENT '模块名',
    action      VARCHAR(128) COMMENT '操作描述',
    type        VARCHAR(20) COMMENT 'INSERT/UPDATE/DELETE/...',
    method      VARCHAR(256) COMMENT '类.方法',
    uri         VARCHAR(256),
    http_method VARCHAR(10),
    ip          VARCHAR(64),
    params      TEXT COMMENT '请求参数JSON',
    result      TEXT COMMENT '返回结果JSON',
    success     TINYINT COMMENT '1成功 0失败',
    error_msg   TEXT,
    elapsed_ms  INT COMMENT '耗时毫秒',
    create_time DATETIME DEFAULT NOW(),
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_module_time (module, create_time)
);
```

### 5.2 Redis 数据模型

| Key 模式 | 类型 | TTL | 用途 |
|---|---|---|---|
| `framework:session:{userId}:{deviceId}` | Hash | 2h | 用户会话 |
| `framework:token:blacklist:{token}` | String | Token剩余有效期 | Token黑名单 |
| `framework:lock:{key}` | String(Redisson) | -1(看门狗) | 分布式锁 |
| `framework:idempotent:{key}` | String | 10s | 幂等防重 |
| `framework:rate:{type}:{key}` | RateLimiter | - | 限流计数 |
| `framework:mq:consumed:{businessKey}` | String | 7d | MQ消费幂等 |
| `framework:cache:{key}` | String/Hash | 可配 | 业务缓存 |
| `framework:login_fail:{username}` | String | 30min | 登录失败计数 |

---

## 六、部署架构

### 6.1 单体部署（小规模）

```
                    Nginx (80/443)
                        │
                ┌───────▼───────┐
                │  Spring Boot  │
                │  (fat jar)    │
                │  port: 8080   │
                └───────┬───────┘
                        │
            ┌───────────┼───────────┐
            │           │           │
        MySQL      Redis       RabbitMQ
        (3306)     (6379)       (5672)
```

### 6.2 集群部署（中规模）

```
                        Nginx (LB)
                     /              \
              ┌─────▼─────┐   ┌─────▼─────┐
              │ App-1     │   │ App-2     │
              │ 8080      │   │ 8080      │
              └─────┬─────┘   └─────┬─────┘
                    │                │
          ┌─────────┼────────────────┘
          │         │         │
      MySQL    Redis      RabbitMQ
      主从     Cluster    集群
```

### 6.3 微服务部署（大规模）

```
                 Spring Cloud Gateway
                    /        |        \
            ┌──────▼──┐ ┌────▼────┐ ┌──▼──────┐
            │Service-A│ │Service-B│ │Service-C│
            │×3实例   │ │×3实例   │ │×3实例   │
            └──────┬──┘ └────┬────┘ └──┬──────┘
                   │         │         │
              Nacos注册中心 & 配置中心
                   │         │         │
            ┌──────▼─────────▼─────────▼──────┐
            │       基础设施层                  │
            │  MySQL分库分表 / Redis集群 /     │
            │  RabbitMQ集群 / ES集群 / MinIO   │
            └─────────────────────────────────┘
```

### 6.4 容器化部署

```dockerfile
FROM eclipse-temurin:17-jre-alpine
COPY target/demo.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
```

```yaml
# docker-compose.yml
services:
  app:
    build: .
    ports: ["8080:8080"]
    depends_on: [mysql, redis, rabbitmq]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/framework
      SPRING_DATA_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: framework
    ports: ["3306:3306"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  rabbitmq:
    image: rabbitmq:3.13-management
    ports: ["5672:5672", "15672:15672"]
```

---

## 七、高可用设计

### 7.1 应用层高可用

| 策略 | 实现 |
|---|---|
| 无状态服务 | Session 存 Redis，应用不持有状态，可水平扩缩 |
| 健康检查 | `/actuator/health/liveness` + `/actuator/health/readiness` |
| 优雅停机 | `server.shutdown=graceful`，等待请求处理完成 |
| 负载均衡 | Nginx upstream / Spring Cloud LoadBalancer |

### 7.2 数据层高可用

| 组件 | 方案 | RPO | RTO |
|---|---|---|---|
| MySQL | 主从复制 + MHA/MGR | 0 | <30s |
| Redis | Cluster 集群 + 哨兵 | 秒级 | <30s |
| RabbitMQ | 镜像队列 / Quorum Queue | 0 | <30s |

### 7.3 熔断降级策略

```
                    请求量增大
                        │
              ┌─────────▼─────────┐
              │  限流（第一道防线）  │ ← @RateLimit
              │  超限直接拒绝       │
              └─────────┬─────────┘
                        │ 放行的请求
              ┌─────────▼─────────┐
              │  熔断（第二道防线）  │ ← @CircuitBreaker
              │  失败率>50%熔断     │
              └─────────┬─────────┘
                        │ 熔断时降级
              ┌─────────▼─────────┐
              │  降级（兜底）       │ ← fallback 方法
              │  返回缓存/默认值    │
              └─────────┬─────────┘
                        │
              ┌─────────▼─────────┐
              │  重试（自愈）       │ ← @Retry
              │  指数退避重试       │
              └───────────────────┘
```

---

## 八、可观测性设计

### 8.1 日志体系

| 层 | 格式 | 存储 |
|---|---|---|
| 应用日志 | `%d [%thread] [%X{traceId}] %-5level %logger - %msg` | 文件 + ELK |
| 操作日志 | JSON（module/action/params/result/elapsed） | MySQL + ES |
| 访问日志 | Nginx access log | 文件 |
| 慢SQL | MyBatis 拦截器记录 >100ms 的 SQL | 文件 + 告警 |

### 8.2 指标监控

| 维度 | 指标 | 采集方式 |
|---|---|---|
| JVM | 堆内存/GC/线程数 | Micrometer → Prometheus |
| HTTP | QPS/RT/错误率 | Spring Boot Actuator |
| DB | 连接池/慢SQL | Druid / Actuator |
| Redis | 命中率/内存/连接 | Lettuce metrics |
| MQ | 消费速率/堆积量 | RabbitMQ Management API |
| 业务 | 自定义指标 | Micrometer Counter/Gauge |

### 8.3 链路追踪

```
TraceId 贯穿全链路:

HTTP请求 → TraceIdFilter生成traceId → MDC → 日志输出
                                         ↓
                                    HTTP Header 透传
                                         ↓
                                    下游服务继续用同一 traceId
                                         ↓
                                    MQ消息也携带 traceId
```

---

## 九、安全设计

### 9.1 认证安全

| 措施 | 实现 |
|---|---|
| 密码存储 | BCrypt（salt=10），不可逆 |
| 密码传输 | HTTPS 加密通道 |
| 登录失败锁定 | 连续失败5次锁定30分钟 |
| 验证码强制 | 失败3次后强制图形验证码 |
| Token 安全 | access(2h) + refresh(7d)，黑名单吊销 |
| 多端互踢 | 同设备登录踢旧会话 |

### 9.2 接口安全

| 措施 | 实现 |
|---|---|
| XSS 防护 | 全局 HTML 转义过滤器 |
| SQL 注入 | MyBatis 参数化 + 关键字过滤 |
| CSRF | Token 校验（前后端分离可关） |
| 接口签名 | HMAC-SHA256 签名校验 |
| 限流防刷 | @RateLimit 多维度限流 |
| 越权防护 | @RequirePermission + 数据权限 |

### 9.3 数据安全

| 措施 | 实现 |
|---|---|
| 敏感字段加密 | AES 加密存储（手机号/身份证） |
| 数据脱敏 | DesensitizeUtils（展示时脱敏） |
| 传输加密 | HTTPS / 字段级加密 |
| 审计日志 | @OperationLog 记录关键操作 |

---

## 十、性能设计

### 10.1 性能目标

| 指标 | 目标 |
|---|---|
| 框架开销 | 单请求 < 5ms（不含业务） |
| 并发能力 | 单实例 1000+ QPS |
| 锁开销 | 分布式锁获取 < 2ms |
| 缓存命中 | L1 < 1ms, L2 < 3ms |
| 日志开销 | 异步记录，< 1ms |

### 10.2 性能优化策略

| 层 | 策略 |
|---|---|
| 网络层 | HTTP/2 连接复用、Keep-Alive |
| 应用层 | 线程池隔离、异步化、连接池 |
| 缓存层 | 多级缓存、热点预热、防穿透 |
| DB 层 | 读写分离、索引优化、慢SQL监控 |
| MQ 层 | 批量发送、预取、消费者并发 |

### 10.3 容量评估

```
单机QPS = 1000 / 平均RT(ms)
集群QPS = 单机QPS × 实例数 × 0.7(利用率)

示例:
  目标 1万 QPS，平均 RT 20ms
  单机QPS = 1000/20 = 50
  所需实例 = 10000 / (50 × 0.7) ≈ 286台
  → 优化RT到5ms: 单机QPS=200, 实例=10000/140 ≈ 72台
```

---

## 十一、扩展性设计

### 11.1 水平扩展

| 组件 | 扩展方式 |
|---|---|
| 应用实例 | 无状态，直接加机器 + 负载均衡 |
| MySQL | 读写分离 → 分库分表（ShardingSphere） |
| Redis | 主从 → Sentinel → Cluster |
| RabbitMQ | 单节点 → 集群 → 镜像队列 |

### 11.2 模块扩展

脚手架设计为可插拔，新增业务模块只需：

```
1. 创建新 Maven 模块，依赖 framework-core
2. 编写业务代码，使用脚手架注解
3. 在 demo 模块中引入新模块依赖
```

### 11.3 插件化能力

| 扩展点 | 方式 |
|---|---|
| 自定义异常处理 | 实现 `@RestControllerAdvice` |
| 自定义过滤器 | 实现 `Filter` 接口，注册 Bean |
| 自定义注解 | 定义注解 + `@Aspect` 切面 |
| 自定义工具 | 新增工具类到 framework-tools |
| 自定义消息消费者 | 继承 `AbstractMqConsumer<T>` |

---

## 十二、演进路线

### Phase 1: 单体脚手架（当前）

- ✅ 14 个基础模块
- ✅ 注解驱动，开箱即用
- ✅ Demo 验证全流程

### Phase 2: 微服务化

- [ ] 拆分用户服务、权限服务、日志服务
- [ ] 引入 Nacos 注册中心 + 配置中心
- [ ] Spring Cloud Gateway 替代 Nginx
- [ ] OpenFeign 服务间调用
- [ ] Seata 分布式事务

### Phase 3: 云原生

- [ ] Docker 容器化
- [ ] Kubernetes 编排
- [ ] Helm Chart 部署
- [ ] Service Mesh（Istio）
- [ ] GitOps（ArgoCD）

### Phase 4: 智能化

- [ ] AI 代码生成（基于脚手架模板）
- [ ] 智能监控告警（异常预测）
- [ ] 自动容量规划
- [ ] AIOps 故障自愈
