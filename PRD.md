# Framework Boot Starter 组件 PRD

> 版本：v2.0（融合版）
> 日期：2026-06-25
> 融合来源：需求文档 + 系统设计 + 场景方案 + JavaGuide/小林coding 知识体系
> 代码版本：14 个模块，全部编译通过

---

# 第一部分：总览

## 1. 目标

提供一套开箱即用的 Java 后端脚手架框架，封装通用技术能力，使业务开发聚焦业务逻辑。每个组件可独立引入，注解驱动，AOP 实现。

## 2. 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| JDK | OpenJDK | 17 LTS |
| 框架 | Spring Boot | 3.2.x |
| ORM | MyBatis-Plus | 3.5.x |
| 缓存 | Redis + Redisson | 7.x / 3.31.x |
| 消息队列 | RabbitMQ | 3.13.x |
| API 文档 | SpringDoc OpenAPI 3 | 2.3.x |
| 熔断 | Resilience4j | 2.2.x |
| 构建 | Maven | - |

## 3. 模块全景

```
framework-boot-starter/
├── framework-core              # 核心基础（统一响应/异常/常量/业务码）
├── framework-web               # Web层（全局异常/CORS/traceId/参数校验）
├── framework-auth              # 鉴权（JWT/登录/会话/Token管理/多端互踢）
├── framework-security          # 权限（@RequireLogin/@RequireRole/@RequirePermission/数据权限）
├── framework-cache             # 缓存（Redis封装/Caffeine/多级缓存/防穿透击穿雪崩）
├── framework-lock              # 分布式锁（@DistributedLock/SpEL/看门狗/fallback）
├── framework-idempotent        # 幂等性（@Idempotent/Token/RequestHash/BusinessKey）
├── framework-crypto            # 加解密（AES/RSA/BCrypt/脱敏）
├── framework-log               # 操作日志（@OperationLog/异步/链路traceId）
├── framework-rate-limiter      # 限流（@RateLimit/令牌桶/滑动窗口/多维度）
├── framework-mq                # 消息队列（生产/消费/死信/延迟/重试/管理控制台）
├── framework-retry             # 重试熔断（@Retry指数退避/@CircuitBreaker熔断降级）
├── framework-tools             # 工具库（雪花ID/树形工具/日期工具）
└── demo                        # 示例启动模块
```

## 4. 模块依赖关系

```
framework-core（核心基础）
├── framework-web
│   ├── framework-auth ── framework-security
│   ├── framework-log
│   ├── framework-idempotent
│   ├── framework-mq
│   └── framework-rate-limiter
├── framework-cache
├── framework-lock
├── framework-retry
├── framework-crypto
└── framework-tools
```

## 5. 横切关注点拦截链

```
请求 → TraceIdFilter → CorsFilter → TokenAuthFilter
  → PermissionAspect(@RequireLogin/@RequirePermission)
  → RateLimitAspect(@RateLimit)
  → IdempotentAspect(@Idempotent)
  → DistributedLockAspect(@DistributedLock)
  → RetryAspect(@Retry) → CircuitBreakerAspect(@CircuitBreaker)
  → Controller方法
  → OperationLogAspect(@OperationLog)
  → GlobalExceptionHandler(异常兜底)
  → 返回
```

---

# 第二部分：组件需求

---

## 组件 1：framework-core

### 定位
核心基础层，所有模块的根依赖。

### 功能需求

| 功能 | 类 | 说明 |
|---|---|---|
| 统一响应体 | `Result<T>` | code/message/data/timestamp |
| 分页响应体 | `PageResult<T>` | records/total/pageNum/pageSize/pages |
| 业务码规范 | `ResultCode` | 200成功/400-499客户端/500-599服务端/1000-1999通用业务/2000-2999鉴权/3000-3999自定义 |
| 业务异常 | `BusinessException` | 带业务码的运行时异常 |
| 参数异常 | `ParamException` | 参数校验失败 |
| 鉴权异常 | `AuthException` | Token无效/过期 |
| 权限异常 | `PermissionException` | 权限不足 |
| 全局常量 | `FrameworkConstants` | Header名/Redis前缀/默认分页 |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1719235200000
}
```

### 业务码表

| 码 | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或登录已过期 |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 500 | 系统繁忙 |
| 1001 | 参数校验失败 |
| 1002 | 业务处理失败 |
| 1003 | 请勿重复提交 |
| 2001 | Token无效 |
| 2002 | Token已过期 |
| 2003 | 账号或密码错误 |
| 2004 | 账号已被锁定 |
| 2005 | 权限不足 |
| 3001 | 请求过于频繁 |
| 3002 | 操作繁忙 |
| 3003 | 重复请求已拦截 |

---

## 组件 2：framework-web

### 定位
Web 层基础设施，全局异常处理、跨域、链路追踪、参数校验。

### 功能需求

| 功能 | 实现 | 说明 |
|---|---|---|
| 全局异常处理 | `GlobalExceptionHandler` | `@RestControllerAdvice` 捕获所有异常转 `Result` |
| 链路追踪 | `TraceIdFilter` | 生成/透传 traceId，写入 MDC，响应头返回 |
| 跨域配置 | `CorsConfig` | 可配置的 CORS 策略，支持凭证 |
| 参数校验 | JSR-303 | `@Valid` + `@Validated`，校验失败统一提示 |
| XSS防护 | 全局过滤 | HTML 特殊字符转义 |
| SQL注入防护 | MyBatis拦截器 | 参数化 + 危险关键字过滤 |

### 异常处理矩阵

| 异常类型 | HTTP状态 | 业务码 | 日志级别 |
|---|---|---|---|
| BusinessException | 200 | 自定义 | WARN |
| MethodArgumentNotValidException | 400 | 1001 | WARN |
| ConstraintViolationException | 400 | 1001 | WARN |
| AuthException | 401 | 2001/2002 | WARN |
| PermissionException | 403 | 2005 | WARN |
| NoHandlerFoundException | 404 | 404 | WARN |
| HttpRequestMethodNotSupportedException | 405 | 405 | WARN |
| Exception(兜底) | 500 | 500 | ERROR(含堆栈) |

### 链路追踪设计

```
请求进入 → TraceIdFilter
  ├─ 从 Header 获取 X-Trace-Id
  ├─ 不存在则生成 UUID（去横线）
  ├─ 写入 MDC（日志输出 [%X{traceId}]）
  └─ 响应头返回 X-Trace-Id
```

---

## 组件 3：framework-auth

### 定位
认证授权模块，JWT 签发/校验、登录会话管理、Token 刷新/黑名单、多端互踢。

### 登录流程

```
客户端                    网关/Filter              认证服务                Redis
  │── POST /auth/login ──→│                       │                     │
  │   {username,password, │──白名单放行──────────→│──校验验证码────────→│
  │    captcha,captchaKey}│                       │──查用户(DB)          │
  │                        │                       │──BCrypt校验密码      │
  │                        │                       │──检查账号状态        │
  │                        │                       │──多端互踢检查──────→│
  │                        │                       │──生成JWT             │
  │                        │                       │──存会话────────────→│
  │←── {accessToken,      │                       │   session:{uid}:{dev}│
  │     refreshToken,     │                       │                     │
  │     userInfo}         │                       │                     │
```

### Token 规范

| 属性 | accessToken | refreshToken |
|---|---|---|
| 用途 | 接口鉴权 | 刷新accessToken |
| 有效期 | 2小时 | 7天 |
| 载荷 | userId/tenantId/roles/deviceId | userId/deviceId |
| 吊销 | Redis黑名单 | Redis删除 |

### 功能需求

| 功能 | 类/方法 | 说明 |
|---|---|---|
| JWT签发 | `JwtUtils.generateAccessToken` | HS256签名，载荷含userId/username/tenantId/deviceId |
| JWT校验 | `JwtUtils.validateToken` | 签名+过期+载荷完整性 |
| Token刷新 | `SessionManager.refreshToken` | refreshToken换新accessToken |
| Token注销 | `SessionManager.logout` | 删会话+accessToken加黑名单 |
| 会话管理 | `SessionManager` | Redis持久化，TTL可配 |
| 多端互踢 | `kickOutOldSession` | 同设备登录踢旧会话 |
| 在线用户 | `getOnlineUserCount` | 扫描session keys |
| 强制下线 | `forceLogout` | 删session+token加黑名单 |
| Token认证过滤器 | `TokenAuthFilter` | 白名单放行+Token校验+注入UserContextHolder |

### 登录安全策略

| 策略 | 实现 |
|---|---|
| 密码错误锁定 | 连续失败5次锁定30分钟，Redis计数 |
| 验证码强制 | 失败3次后强制图形验证码 |
| IP限制 | 同IP登录失败频率限制 |
| 密码强度 | 长度/大小写/数字/特殊字符校验 |
| 密码过期 | N天过期强制修改 |

### 登录方式

| 方式 | 流程 |
|---|---|
| 账号密码 | 验证码→查用户→BCrypt校验→状态校验→多端互踢→生成Token→会话持久化→登录日志 |
| 短信验证码 | 发验证码(限频60s)→校验→生成Token |
| OAuth2第三方 | 重定向授权→回调换code→获取用户信息→首次自动注册 |

### 配置

```yaml
framework:
  auth:
    enabled: true
    jwt:
      secret: your-secret-at-least-32-chars
      access-token-expire: 7200
      refresh-token-expire: 604800
    session-timeout: 7200
    white-list:
      - /auth/**
      - /public/**
      - /actuator/**
```

---

## 组件 4：framework-security

### 定位
RBAC 权限控制，注解式鉴权，数据权限。

### RBAC 模型

```
用户(User) ── N:N ── 角色(Role) ── N:N ── 权限(Permission)
                                         │
                                    菜单(Menu) ── N:N ── 权限
```

### 注解能力

| 注解 | 作用 | 示例 |
|---|---|---|
| `@IgnoreToken` | 跳过鉴权 | 登录/验证码接口 |
| `@RequireLogin` | 必须登录 | 所有需登录的接口 |
| `@RequireRole("ADMIN")` | 必须指定角色 | 管理后台接口 |
| `@RequirePermission("user:add")` | 必须指定权限 | 增删改操作 |

### 权限校验逻辑

| 模式 | 逻辑 |
|---|---|
| OR（默认） | 满足任一角色/权限即可 |
| AND（logicalAnd=true） | 必须全部满足 |

### 数据权限

| 策略 | 说明 | SQL拼接 |
|---|---|---|
| 全部数据 | 可看所有 | 无限制 |
| 本部门 | 只看本部门 | `AND dept_id = #{deptId}` |
| 本部门及子部门 | 本部门+下级 | `AND dept_id IN (子部门列表)` |
| 仅本人 | 只看自己 | `AND create_by = #{userId}` |

### 实现方式

```java
@DataScope(deptAlias = "d", userAlias = "u")
List<User> selectUsers(@Param("user") UserQuery query);
// MyBatis拦截器自动拼接: AND (d.id = #{deptId} OR u.create_by = #{userId})
```

### 功能需求

| 功能 | 说明 |
|---|---|
| 角色管理 | 增删改查、角色分配权限 |
| 菜单管理 | 树形菜单、权限标识 |
| 用户授权 | 用户分配角色 |
| 权限校验 | 注解+AOP拦截 |
| 数据权限 | MyBatis拦截器自动拼接SQL |
| 权限缓存 | Redis缓存用户权限，变更时刷新 |

---

## 组件 5：framework-cache

### 定位
多级缓存，防穿透/击穿/雪崩，热点探测。

### 缓存分层

| 层 | 技术 | 命中耗时 | 适用 |
|---|---|---|---|
| L1本地缓存 | Caffeine | <1ms | 配置/字典/热点数据 |
| L2分布式缓存 | Redis Cluster | 1-3ms | 用户/商品/库存 |
| L3持久化 | SSD Redis/Tair | 3-10ms | 历史订单/大对象 |
| DB | MySQL | 10-50ms | 兜底 |

### 缓存防护

| 问题 | 原因 | 方案 |
|---|---|---|
| 缓存穿透 | 查不存在的key | 布隆过滤器+空值缓存 |
| 缓存击穿 | 热点key过期 | 互斥锁singleflight |
| 缓存雪崩 | 大量key同时过期 | 过期时间随机化+多级兜底 |
| 热点探测 | 单key高访问 | 自动识别+本地预热 |
| 大key拆分 | List/Hash过大 | 按hash(key)%N分片 |
| 热key拆分 | 单点热 | key_1~key_N多副本读 |

### 缓存一致性

| 策略 | 说明 | 适用 |
|---|---|---|
| Cache Aside（推荐） | 先更DB再删缓存，延迟双删 | 通用默认 |
| Write Through | 同步写缓存+DB | 强一致 |
| Write Behind | 异步写DB | 日志/计数 |

### 功能需求

| 功能 | 说明 |
|---|---|
| Redis封装 | String/Hash/List/Set/ZSet操作 |
| 本地缓存 | Caffeine LRU/LFU |
| 多级缓存 | L1+L2，L1自动失效 |
| 注解缓存 | @Cacheable/@CacheEvict/@CachePut |
| 防缓存击穿 | 互斥锁singleflight |
| 防缓存穿透 | 空值缓存+布隆过滤器 |
| 防缓存雪崩 | 过期时间随机化 |

### 理论基础（小林coding 图解Redis）

| 知识点 | 要点 |
|---|---|
| 数据结构 | String(SDS)/List(quicklist)/Hash(listpack)/Set(intset)/ZSet(skiplist) |
| 持久化 | RDB全量快照/AOF追加写命令/混合（RDB+AOF） |
| 高可用 | 主从复制/哨兵Sentinel/Cluster分片(16384槽) |
| 淘汰策略 | LRU/LFU/随机/noeviction 等8种 |

---

## 组件 6：framework-lock

### 定位
分布式锁，Redisson 注解式+编程式，看门狗自动续期。

### 注解式

```java
@DistributedLock(
    key = "order:#{#orderId}",      // SpEL表达式
    waitTime = 3,                    // 等待秒数
    leaseTime = -1,                  // -1启用看门狗
    unit = TimeUnit.SECONDS,
    message = "操作繁忙，请稍候",
    fallback = "handleLockFail"      // 获取失败回调
)
public void processOrder(Long orderId) { ... }
```

### 锁类型

| 类型 | 说明 |
|---|---|
| 可重入锁 | 默认，同线程可多次获取 |
| 公平锁 | 按请求顺序获取 |
| 读写锁 | 读读共享，读写/写写互斥 |
| 联锁(MultiLock) | 多个RLock同时加锁 |
| 红锁(RedLock) | 多节点RedLock算法 |

### 看门狗机制（Redisson）

```
获取锁(leaseTime=-1)
  → 默认30s过期
  → 启动看门狗线程，每10s检查
    → 还持有锁 → 续期到30s
    → 业务完成释放 → 停止看门狗
```

### 功能需求

| 功能 | 说明 |
|---|---|
| SpEL key解析 | 支持 `#{#param.field}` 格式 |
| 自动续期 | 看门狗机制(leaseTime=-1) |
| 锁失败处理 | 快速失败/自旋等待/回调方法 |
| 锁监控 | 持锁时长/等待时长指标 |
| 防死锁 | 超时自动释放 |
| 可重入 | 同一线程多次获取 |

### 理论基础（JavaGuide 分布式锁）

| 方案 | 优点 | 缺点 |
|---|---|---|
| DB唯一索引 | 简单 | 性能差，无续期 |
| Redis SETNX | 高性能 | 主从切换可能丢锁 |
| **Redisson** | 功能完整，看门狗 | 依赖Redis |
| ZooKeeper | 强一致 | 性能差 |

---

## 组件 7：framework-idempotent

### 定位
接口幂等性，防止重复提交。

### 注解式

```java
@Idempotent(
    key = "order:#{#request.orderNo}",     // SpEL
    expire = 10,                            // 幂等窗口(秒)
    strategy = IdempotentStrategy.TOKEN,   // TOKEN/REQUEST_HASH/BUSINESS_KEY
    message = "请勿重复提交"
)
public Result pay(@RequestBody PayRequest request) { ... }
```

### 幂等策略

| 策略 | 原理 | 场景 |
|---|---|---|
| TOKEN | 请求前先获取token，提交时校验并删除 | 表单提交 |
| REQUEST_HASH | 对请求体hash，窗口内相同hash拒绝 | API防重 |
| BUSINESS_KEY | 业务唯一键(订单号)，DB唯一索引兜底 | 支付/下单 |

### 功能需求

| 功能 | 说明 |
|---|---|
| Token生成/校验 | `/idempotent/token` 接口下发 |
| 自动防重 | 注解拦截，窗口内拒绝 |
| 首次结果缓存 | 幂等返回首次结果（可选） |
| 异常释放 | 业务异常时释放幂等锁，允许重试 |

### 实现原理

```
请求进入 → IdempotentAspect
  → 构建幂等key（SpEL/Token/Hash）
  → Redis SETNX 抢占
    → 成功 → 执行业务
      → 成功 → 返回结果
      → 异常 → 删除Redis key（允许重试）
    → 失败 → 抛出"请勿重复提交"
```

---

## 组件 8：framework-crypto

### 定位
加解密工具库，字段级加解密，数据脱敏，密码哈希。

### 能力清单

| 类别 | 算法 | 工具类 | 用途 |
|---|---|---|---|
| 对称加密 | AES-CBC | `AesUtils` | 数据加密存储/传输 |
| 摘要算法 | MD5/SHA-256/SHA-512 | `DigestUtils` | 数据完整性/哈希 |
| 密码哈希 | BCrypt(salt=10) | `PasswordUtils` | 密码安全存储 |
| 数据脱敏 | 手机/身份证/银行卡/邮箱/姓名 | `DesensitizeUtils` | 展示脱敏 |

### 脱敏规则

| 类型 | 原始 | 脱敏后 |
|---|---|---|
| 手机号 | 13812345678 | 138****5678 |
| 身份证 | 110101199001011234 | 110***********1234 |
| 银行卡 | 6222123456781234 | 6222 **** **** 1234 |
| 邮箱 | zhangsan@qq.com | z***@qq.com |
| 姓名 | 张三丰 | 张**丰 |

### 功能需求

| 功能 | 说明 |
|---|---|
| AES加解密 | CBC模式，PKCS5Padding，Base64编码 |
| 密钥生成 | `AesUtils.generateKey()` 生成256位密钥 |
| BCrypt密码 | `hash()`加密，`verify()`校验 |
| 脱敏工具 | 手机/身份证/银行卡/邮箱/姓名 5种 |
| 国密支持 | SM2/SM3/SM4（规划中） |

---

## 组件 9：framework-log

### 定位
操作日志，注解式，异步记录，链路追踪。

### 注解式

```java
@OperationLog(
    module = "用户管理",
    action = "新增用户",
    type = LogType.INSERT,
    saveParam = true,
    saveResult = false
)
@PostMapping("/users")
public Result create(@RequestBody UserDTO dto) { ... }
```

### 日志类型

| 类型 | 说明 |
|---|---|
| 操作日志 | 增删改操作记录，含操作人/IP/参数/结果 |
| API日志 | 请求/响应全量记录（可配采样率） |
| 登录日志 | 登录成功/失败/IP/设备 |
| 异常日志 | 未捕获异常，含完整堆栈 |
| 链路日志 | traceId串联全链路 |

### 功能需求

| 功能 | 说明 |
|---|---|
| 异步记录 | 日志写入走异步线程池，不阻塞业务 |
| 链路追踪 | MDC注入traceId，全链路串联 |
| 敏感脱敏 | 参数中密码/手机号自动脱敏 |
| 日志存储 | DB(可查) + ES(可搜索)可选 |
| 日志清理 | 定时清理过期日志 |
| 采样率 | API日志可配采样比例(0-100%) |

### 异步线程池配置

| 参数 | 默认值 |
|---|---|
| 核心线程 | 2 |
| 最大线程 | 8 |
| 队列容量 | 1000 |
| 拒绝策略 | CallerRunsPolicy(调用者执行) |

---

## 组件 10：framework-rate-limiter

### 定位
分布式限流，Redisson 滑动窗口，多维度。

### 注解式

```java
@RateLimit(
    key = "api:user:#{#userId}",       // SpEL
    limit = 100,                        // 窗口内允许请求数
    window = 60,                        // 时间窗口(秒)
    limitType = LimitType.IP,           // GLOBAL/IP/USER/DEFAULT
    message = "请求过于频繁"
)
@GetMapping("/users/{userId}")
public Result getUser(@PathVariable Long userId) { ... }
```

### 限流算法

| 算法 | 原理 | 优点 | 缺点 |
|---|---|---|---|
| 令牌桶 | 匀速发令牌，允许突发 | 允许突发 | 实现稍复杂 |
| 滑动窗口 | 细分窗口滑动 | 精确 | 稍复杂 |
| 漏桶 | 匀速输出 | 匀速 | 不允许突发 |
| 固定窗口 | 时间窗口内计数 | 简单 | 临界点突发 |

### 限流维度

| 维度 | key构造 |
|---|---|
| 全局 | `framework:rate:global:{method}` |
| IP | `framework:rate:ip:{ip}:{method}` |
| 用户 | `framework:rate:user:{userId}:{method}` |
| 默认 | `framework:rate:default:{method}` |

### 理论基础（JavaGuide 高可用-限流）

限流是高可用第一道防线，配合降级熔断使用。

---

## 组件 11：framework-mq

### 定位
RabbitMQ 封装，生产/消费/死信/延迟/重试/管理控制台。

### 架构

```
Producer → Exchange → Queue → Consumer
                         │
                    消费失败 → NACK → 死信交换机 → 死信队列
                                                      │
                                              DeadLetterHandler 记录
                                                      │
                                              MqRetryScheduler 定时重试
                                                      │
                                              重试成功 / 耗尽(EXHAUSTED)
                                                      │
                                              管理控制台 人工处理
```

### 消息包装器

```java
MessageWrapper<T> {
    String messageId;     // UUID，幂等消费
    String businessKey;   // 业务键（订单号）
    String type;          // 消息类型
    T payload;            // 业务数据
    long timestamp;       // 创建时间
}
```

### 生产者能力

| 方法 | 说明 |
|---|---|
| `send(exchange, routingKey, payload)` | 普通发送 |
| `send(exchange, routingKey, businessKey, payload)` | 带业务Key |
| `sendWithDelay(exchange, routingKey, payload, delayMs)` | 延迟消息(x-delay插件) |
| `sendWithTtl(exchange, routingKey, payload, ttlMs)` | TTL消息(死信超时转发) |

### 消费者基类

```java
public abstract class AbstractMqConsumer<T> {
    // 幂等检查（Redis SETNX，7天TTL）
    // 手动ACK/NACK
    // 重试次数控制（最大3次）
    // 死信兜底

    protected abstract void doConsume(MessageWrapper<T> wrapper) throws Exception;

    public void handleMessage(Message message, Channel channel) throws Exception {
        // 子类在 @RabbitListener 方法中调用此方法
    }
}
```

### 死信处理

| 组件 | 职责 |
|---|---|
| `DeadLetterHandler` | 监听死信队列，解析x-death header，持久化失败记录 |
| `MqFailedMessage` | 失败消息模型，5种状态(PENDING/RETRYING/SUCCESS/EXHAUSTED/MANUAL) |
| `MqRetryScheduler` | 每30s扫描PENDING消息，指数退避重试(1min→5min→30min→2h→12h) |

### 重试策略

```
消费失败 → 记录到失败表(PENDING)
  → 30s后扫描 → 重发到原交换机
    → 成功 → SUCCESS
    → 失败 → retryCount+1
      → <3次 → 更新下次重试时间(指数退避)
      → ≥3次 → EXHAUSTED(等待人工处理)

人工处理 → 管理控制台手动重发 → MANUAL
```

### 管理控制台

**API接口**：

| 接口 | 方法 | 路径 | 功能 |
|---|---|---|---|
| 统计概览 | GET | `/admin/mq/stats` | 各状态消息数+队列列表 |
| 失败消息列表 | GET | `/admin/mq/failed-messages` | 分页+筛选 |
| 消息详情 | GET | `/admin/mq/failed-messages/{id}` | 完整信息+消息体 |
| 手动重发 | POST | `/admin/mq/failed-messages/{id}/retry` | 单条重发 |
| 批量重发 | POST | `/admin/mq/failed-messages/batch-retry` | 批量重发 |
| 删除记录 | DELETE | `/admin/mq/failed-messages/{id}` | 删除单条 |
| 清空已处理 | DELETE | `/admin/mq/failed-messages/clean` | 清理已处理记录 |

**可视化页面**：`/mq-admin.html`

| 区域 | 功能 |
|---|---|
| 统计卡片 | 待重试/重试中/已成功/已耗尽/总计，30s自动刷新 |
| 筛选栏 | 队列/状态/业务Key/类型 + 批量重发 + 清空 |
| 消息列表 | ID/消息ID/队列/类型/状态/重试次数/失败原因/操作 |
| 详情弹窗 | 完整信息 + 格式化JSON消息体 |

### 队列声明工具

| 方法 | 说明 |
|---|---|
| `buildDirect(exchange, queue, routingKey)` | 直连队列 |
| `buildTopic(exchange, queue, pattern)` | 主题队列(通配符) |
| `buildQueueWithDLX(queue, dlxExchange, dlxKey)` | 带死信的队列 |
| `buildRetryQueue(queue, ttl, targetExchange, targetKey)` | 重试队列(TTL+死信转发) |
| `buildDelayed(exchange, queue, routingKey)` | 延迟队列(x-delayed-message) |

### 消费者容器配置

| 参数 | 默认值 |
|---|---|
| ACK模式 | MANUAL(手动) |
| 并发消费者 | 3 |
| 最大并发 | 10 |
| 预取 | 10 |
| 失败重试间隔 | 5000ms |

### 理论基础（JavaGuide 高性能-MQ）

| MQ对比 | RabbitMQ | RocketMQ | Kafka |
|---|---|---|---|
| 吞吐量 | 万级 | 十万级 | 百万级 |
| 延迟 | 微秒 | 毫秒 | 毫秒 |
| 事务消息 | 不支持 | 支持 | 不支持 |
| 延迟队列 | 死信+插件 | 原生 | 不支持 |
| 适用 | 企业级 | 电商/金融 | 大数据/日志 |

### 理论基础（小林coding 场景-订单超时）

| 方案 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| 定时轮询 | 简单 | DB压力大 | 小规模 |
| DelayQueue | 高效 | 不持久 | 单机 |
| Redis ZSet | 分布式 | 实时性一般 | 中规模 |
| **MQ延迟队列** | 异步高效、可扩展 | 配置复杂 | **大规模** |

---

## 组件 12：framework-retry

### 定位
重试+熔断降级，注解式，指数退避，Resilience4j。

### 重试注解

```java
@Retry(
    maxAttempts = 3,                           // 最大重试次数
    strategy = RetryStrategy.EXPONENTIAL,       // FIXED/EXPONENTIAL
    initialInterval = 1000,                     // 初始间隔(ms)
    multiplier = 2.0,                           // 退避乘数
    maxInterval = 30000,                        // 最大间隔(ms)
    retryFor = {IOException.class},             // 重试的异常
    noRetryFor = {BusinessException.class},     // 不重试的异常
    fallback = "recover"                        // 回调方法
)
public String callApi() { ... }
```

### 重试策略

| 策略 | 计算公式 | 示例 |
|---|---|---|
| FIXED | 每次等待initialInterval | 1s→1s→1s |
| EXPONENTIAL | initial * multiplier^attempt | 1s→2s→4s→8s |

### 熔断注解

```java
@CircuitBreaker(
    name = "payment-service",       // 熔断器名称
    fallback = "paymentFallback",   // 降级方法
    timeout = 2000,                 // 超时(ms)
    failureRate = 0.5,              // 失败率阈值
    slidingWindowSize = 100,        // 滑窗大小
    waitDurationInOpenState = 30    // 熔断持续(秒)
)
public Result pay(PayRequest req) { ... }
```

### 熔断状态机

```
CLOSED → 失败率>50% → OPEN（拒绝请求）
  OPEN → 等待30s → HALF_OPEN（放行10个探测）
  HALF_OPEN → 探测成功 → CLOSED（恢复）
  HALF_OPEN → 探测失败 → OPEN（继续熔断）
```

### 熔断参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| failureRateThreshold | 50% | 触发熔断 |
| slowCallDurationThreshold | 2000ms | 慢调用阈值 |
| slowCallRateThreshold | 80% | 慢调用比例触发 |
| permittedNumberOfCallsInHalfOpenState | 10 | 半开放行数 |
| slidingWindowSize | 100 | 统计窗口 |
| minimumNumberOfCalls | 10 | 最小请求量 |
| waitDurationInOpenState | 30s | 熔断持续 |

### 理论基础（JavaGuide 高可用）

| 概念 | 说明 |
|---|---|
| 限流 | 第一道防线，超限直接拒绝 |
| 熔断 | 第二道防线，失败率超阈值熔断 |
| 降级 | 兜底，返回缓存/默认值 |
| 重试 | 自愈，指数退避重试 |

### 超时层次设计

| 层 | 超时 | 原则 |
|---|---|---|
| 网关 | 30s | 最外层兜底 |
| RPC/HTTP | 3-5s | 上游>下游 |
| DB | 1-3s | 避免长事务 |
| Redis | 200ms | 快速失败 |
| MQ | 5s | 发送超时 |

---

## 组件 13：framework-tools

### 定位
通用工具库。

### 功能清单

| 工具 | 类 | 说明 |
|---|---|---|
| 雪花ID | `SnowflakeIdGenerator` | 1bit符号+41bit时间+10bit机器+12bit序列，时钟回拨检测 |
| 树形工具 | `TreeUtils` | 列表转树/树转列表，泛型支持 |
| 日期工具 | `DateUtils` | 格式化/解析/转换/时区/差值计算 |

### 雪花算法

```
| 1bit | 41bit时间戳 | 10bit机器ID | 12bit序列号 |
  符号   ~69年         1024台机器    每毫秒4096个

时钟回拨: 记录lastTimestamp，回拨时自旋等待或抛异常
```

### 理论基础（JavaGuide 分布式ID）

| 方案 | 唯一性 | 递增 | 性能 | 依赖 |
|---|---|---|---|---|
| UUID | 全局唯一 | 无序 | 高 | 无 |
| DB自增 | 全局唯一 | 递增 | 低 | DB |
| **雪花算法** | 全局唯一 | 趋势递增 | 极高 | 时钟 |
| 号段模式 | 全局唯一 | 趋势递增 | 高 | DB(弱) |

---

## 组件 14：demo

### 定位
示例启动模块，演示全部能力。

### 示例接口

| 接口 | 注解组合 | 演示能力 |
|---|---|---|
| `GET /api/demo/public` | - | 白名单公开接口 |
| `GET /api/demo/authed` | `@RequireLogin` | 登录校验 |
| `POST /api/demo/create` | `@RequirePermission("user:add")` | 权限校验 |
| `PUT /api/demo/lock/{id}` | `@DistributedLock` | 分布式锁 |
| `POST /api/demo/order` | `@Idempotent` | 幂等防重 |
| `GET /api/demo/rate-limit` | `@RateLimit` | 限流 |
| `POST /api/demo/log` | `@OperationLog` | 操作日志 |
| `POST /api/demo/mq/send` | `MqProducer.send` | MQ发送 |
| `POST /api/demo/mq/delay` | `MqProducer.sendWithDelay` | 延迟消息 |
| `GET /api/demo/retry` | `@Retry` | 指数退避重试 |
| `GET /api/demo/circuit-breaker` | `@CircuitBreaker` | 熔断降级 |
| `POST /api/demo/combo` | 全部注解组合 | 全能力演示 |

---

# 第三部分：架构设计

## 1. 整体架构

```
客户端 → 网关层(Nginx/Gateway) → 应用服务层(Spring Boot+脚手架) → 数据层(MySQL/Redis/MQ) → 监控层
```

## 2. 部署架构

### 单体部署

```
Nginx → Spring Boot(8080) → MySQL/Redis/RabbitMQ
```

### 集群部署

```
Nginx(LB) → App×N → MySQL主从/Redis Cluster/RabbitMQ集群
```

### 微服务部署

```
Gateway → Service-A×3 / Service-B×3 / Service-C×3
          ↓
Nacos注册中心 + 配置中心
          ↓
MySQL分库分表 / Redis集群 / MQ集群 / ES / MinIO
```

## 3. 高可用设计

### 四层防线

```
请求 → 限流(@RateLimit) → 熔断(@CircuitBreaker) → 降级(fallback) → 重试(@Retry)
       第一道防线          第二道防线              兜底              自愈
```

### 冗余设计

| 层 | 方案 |
|---|---|
| 应用 | 无状态，多实例+负载均衡 |
| DB | 主从复制+MHA/MGR |
| Redis | Cluster集群+哨兵 |
| MQ | 镜像队列/Quorum Queue |
| 机房 | 同城双活/两地三中心/异地多活 |

## 4. 数据模型

### Redis Key 模型

| Key模式 | 类型 | TTL | 用途 |
|---|---|---|---|
| `session:{userId}:{deviceId}` | Hash | 2h | 用户会话 |
| `token:blacklist:{token}` | String | Token剩余 | Token黑名单 |
| `lock:{key}` | Redisson | -1(看门狗) | 分布式锁 |
| `idempotent:{key}` | String | 10s | 幂等防重 |
| `rate:{type}:{key}` | RateLimiter | - | 限流计数 |
| `mq:consumed:{businessKey}` | String | 7d | MQ消费幂等 |
| `mq:failed:{id}` | String | - | MQ失败消息 |
| `cache:{key}` | String/Hash | 可配 | 业务缓存 |
| `login_fail:{username}` | String | 30min | 登录失败计数 |

## 5. 安全设计

### 三层防护

| 层 | 措施 |
|---|---|
| 认证安全 | BCrypt密码存储/HTTPS传输/失败锁定/验证码/Token黑名单/多端互踢 |
| 接口安全 | XSS防护/SQL注入/CSRF/接口签名/限流防刷/越权防护 |
| 数据安全 | AES字段加密/脱敏展示/传输加密/审计日志 |

---

# 第四部分：场景方案

## 场景 1：秒杀系统

### 四层防线

| 层 | 方案 | 拦截 |
|---|---|---|
| 前端 | CDN+验证码+按钮防抖 | ~80% |
| 网关 | IP限流+黑名单+Token | ~15% |
| 缓存 | Redis Lua原子扣减+一人一单 | 剩余 |
| MQ | 异步下单削峰 | 保护DB |
| DB | 乐观锁兜底 `WHERE stock>0` | 最终一致 |

### 脚手架应用

```java
@RequireLogin
@RateLimit(limit = 100, window = 1, limitType = LimitType.USER)
@Idempotent(key = "seckill:#{#userId}_#{#skuId}", expire = 30)
@DistributedLock(key = "stock:#{#skuId}", waitTime = 1)
@OperationLog(module = "秒杀", action = "抢购")
@PostMapping("/seckill")
public Result seckill(Long userId, Long skuId) {
    // Redis Lua 原子扣减
    boolean success = stockService.deductByLua(skuId, 1);
    if (!success) return Result.fail("库存不足");
    // MQ 异步下单
    mqProducer.send("order.exchange", "seckill", userId + "_" + skuId, orderDTO);
    return Result.success("抢购成功，订单创建中");
}
```

## 场景 2：订单超时取消

```java
// 下单时发送延迟消息（15分钟）
mqProducer.sendWithDelay("order.timeout.exchange", "order.timeout",
    MessageWrapper.of(orderNo, orderDTO), 15 * 60 * 1000);

// 消费者
@RabbitListener(queues = "order.timeout.queue")
public void handle(Message message, Channel channel) throws Exception {
    handleMessage(message, channel);  // 基类幂等+ACK
}

@Override
protected void doConsume(MessageWrapper<OrderDTO> wrapper) {
    OrderDTO order = wrapper.getPayload();
    if (orderService.isUnpaid(order.getOrderNo())) {
        orderService.cancel(order.getOrderNo());
        stockService.restore(order.getSkuId(), order.getQuantity());
    }
}
```

## 场景 3：QPS突增10倍

```
紧急: @RateLimit限流 + @CircuitBreaker熔断 + K8s扩容
架构: 多级缓存(framework-cache) + MQ削峰(framework-mq) + 读写分离+分库分表
```

## 场景 4：分布式锁防超卖

```java
@DistributedLock(key = "stock:#{#skuId}", waitTime = 3, leaseTime = -1)
public boolean deductStock(Long skuId, int qty) {
    // 看门狗自动续期，业务完成自动释放
    return stockMapper.deduct(skuId, qty);  // UPDATE SET stock=stock-? WHERE id=? AND stock>=?
}
```

## 场景 5：接口幂等防重

```java
@Idempotent(
    key = "pay:#{#request.orderNo}",
    expire = 10,
    strategy = IdempotentStrategy.BUSINESS_KEY
)
@PostMapping("/pay")
public Result pay(@RequestBody PayRequest request) {
    // 10秒内相同orderNo的请求只执行一次
    return paymentService.process(request);
}
```

## 场景 6：熔断降级

```java
@CircuitBreaker(
    name = "payment-service",
    failureRate = 0.5,
    timeout = 2000,
    fallback = "payFallback"
)
@PostMapping("/pay")
public Result pay(PayRequest req) {
    return paymentClient.call(req);  // 超时/失败率>50%自动熔断
}

public Result payFallback(PayRequest req) {
    return Result.fail("支付服务暂不可用，请稍后重试");
}
```

---

# 第五部分：理论基础

## 分布式理论

| 理论 | 核心 |
|---|---|
| CAP | CP(强一致) vs AP(可用性) 取舍，P不可避免 |
| BASE | 基本可用+软状态+最终一致 |
| Raft | leader选举，易理解 |
| 一致性哈希 | 哈希环+虚拟节点，减少数据迁移 |

## MySQL核心

| 知识点 | 要点 |
|---|---|
| B+树索引 | 非叶子不存数据，叶子链表，范围查询高效 |
| MVCC | ReadView+undo log版本链，解决读写冲突 |
| 隔离级别 | 读未提交→读已提交→可重复读(RR)→串行化 |
| 锁 | 记录锁/间隙锁/Next-Key Lock |
| 日志 | redo log(崩溃恢复)/undo log(回退)/binlog(复制) |
| 两阶段提交 | redo log prepare → binlog write → redo log commit |

## Redis核心

| 知识点 | 要点 |
|---|---|
| 数据结构 | String(SDS)/List(quicklist)/Hash(listpack)/Set(intset)/ZSet(skiplist) |
| 持久化 | RDB全量/AOF增量/混合 |
| 高可用 | 主从/哨兵/Cluster(16384槽) |
| 缓存问题 | 穿透(布隆过滤器)/击穿(互斥锁)/雪崩(随机过期) |

## 网络与OS

| 知识点 | 要点 |
|---|---|
| HTTPS | TCP握手→TLS握手→加密通信 |
| TCP | 三次握手/四次挥手/拥塞控制 |
| I/O模型 | select/poll/epoll(事件驱动O(1)) |
| 零拷贝 | mmap/sendfile/splice |

---

# 第六部分：优先级与验收

## 优先级

### P0（已实现）

| 模块 | 状态 |
|---|---|
| framework-core | ✅ |
| framework-web | ✅ |
| framework-auth | ✅ |
| framework-security | ✅ |
| framework-lock | ✅ |
| framework-idempotent | ✅ |
| framework-crypto | ✅ |
| framework-log | ✅ |
| framework-rate-limiter | ✅ |
| framework-mq | ✅ |
| framework-retry | ✅ |
| framework-tools | ✅ |
| demo | ✅ |

### P1（规划中）

| 模块 | 说明 |
|---|---|
| framework-cache | 多级缓存+防穿透击穿雪崩（pom已有，代码待补） |
| framework-gateway | Spring Cloud Gateway 封装 |

## 验收标准

| 维度 | 标准 |
|---|---|
| 功能完整性 | 14个模块全部编译通过，demo可运行 |
| 注解可用 | 所有注解在demo中有示例验证 |
| 文档 | 每个模块有使用说明+代码示例 |
| 性能 | 框架自身开销 ≤5ms/请求 |
| 安全 | 通过OWASP Top 10检查 |
| 兼容 | JDK 17+ / Spring Boot 3.x |
| 可扩展 | 模块可独立引入，互不依赖 |

## 交付物

| 交付物 | 说明 |
|---|---|
| 源代码 | 14个Maven模块，41+个Java文件 |
| PRD文档 | 本文档（融合版） |
| 系统设计 | `SYSTEM_DESIGN.md` |
| 场景方案 | `SYSTEM_DESIGN_SCENARIOS.md` |
| 知识体系 | `DISTRIBUTED_PERFORMANCE_AVAILABILITY.md` |
| README | `README.md` 快速开始 |
| Demo | 可运行的示例项目 |
| MQ管理页面 | `/mq-admin.html` 可视化控制台 |
