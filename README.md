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
| `framework-job` | 任务：XXL-JOB 执行器自动配置、JobHandler 注册和本地触发 |
| `framework-file` | 文件：统一文件模型、本地存储默认实现、对象存储扩展点 |
| `framework-starter` | 聚合 Starter：一次引入常用脚手架能力 |
| `admin-service` | 管理后台服务：登录认证、系统管理、Dashboard、MQ 补偿、本地消息、通知/Excel/文件中心、日志审计、监控 API |
| `frontend/admin-web` | 管理后台前端：登录页、系统管理、运维控制台，Vue3 + Vite + Element Plus |
| `demo` | 示例启动模块：演示全部能力 |

## 管理后台功能与日志设计（非技术版）

这一节是给不熟悉代码的人看的：它解释“像截图那种 AI SaaS 管理后台，后端到底要提供什么能力，哪些日志必须记录”。前端页面只是把数据展示出来，真正重要的是后端能不能把用户行为、AI 调用、消息链路、异常和人工补偿完整留痕。

### 1. 这个页面是什么

截图里的页面属于 `Admin Dashboard 管理仪表盘`，常见于 AI 企业控制台、知识库后台、客服机器人后台、运营管理平台。

它不是单纯的静态页面，背后至少要有这些后端能力：

| 页面区域 | 后端要提供什么 |
|---|---|
| 顶部搜索框 | 搜索知识库、会话、用户、意图、流程、失败消息、日志 |
| 左侧菜单 | 按用户角色返回可见菜单和权限点 |
| 核心指标卡片 | 统计活跃用户、会话数、消息数、会话深度、同比/环比 |
| 流量折线图 | 按小时/天聚合请求量、会话量、消息量 |
| 趋势分析 | 统计会话趋势、活跃用户趋势、知识命中趋势 |
| 右侧 AI 性能 | 统计成功率、平均响应、P95/P99、错误率、慢响应率 |
| 质量快照 | 统计错误率、无知识命中率、慢响应率，并给出阈值告警 |
| 运营效率 | 统计人均会话、单会话消息数、人均消息数 |

一句话：前端负责“好看和好用”，后端负责“数据真实、链路可查、异常可追、操作可审计”。

### 2. 后台应该有哪些功能模块

建议把管理后台按下面这些模块设计。即使一开始不全部做，也应该按这个边界预留。

| 模块 | 给使用者看的能力 | 后端真实职责 |
|---|---|---|
| Dashboard 看板 | 看到系统今天/近 7 天/近 30 天运行情况 | 汇总用户、会话、消息、AI 响应、错误率、成功率等指标 |
| 知识库管理 | 新建知识库、上传文档、查看解析状态 | 文档上传、解析、切片、向量化、索引构建、失败重试 |
| 意图管理 | 配置用户问题属于哪类意图 | 意图 CRUD、规则配置、测试命中、启停、版本记录 |
| 意图树配置 | 配置多轮对话流程和跳转关系 | 节点、边、条件、发布、回滚、版本管理 |
| 流水线管理 | 配置 AI 处理链路 | 编排模型调用、知识召回、工具调用、消息发送、异常兜底 |
| 数据通道 | 配置外部数据来源或同步任务 | 接口配置、鉴权配置、同步状态、失败记录、补偿重跑 |
| 链路追踪 | 根据 traceId 查一次请求完整路径 | 串起 HTTP、Feign、MQ、本地消息、AI 调用日志，业务按需补充 XXL-JOB 执行日志 |
| 用户管理 | 管理后台账号、角色、权限 | 登录、会话、权限、菜单、操作审计 |
| 示例问题 | 维护测试问题和验收集 | 批量测试知识库/意图/模型效果，记录命中和失败原因 |
| 系统设置 | 配置模型、密钥、Webhook、阈值 | 参数校验、敏感配置脱敏、变更日志、权限保护 |
| MQ 补偿管理 | 查看失败消息、手动重发、清理终态记录 | 死信记录、自动重试、人工补偿、操作人和备注留痕 |
| XXL-JOB 管理（可选） | 查看/触发业务后台任务 | 由业务服务按需接入 `framework-job`，记录任务执行状态、耗时和错误 |

### 3. Dashboard 指标建议

截图上的指标可以按下面方式落地。

| 指标 | 含义 | 建议统计口径 |
|---|---|---|
| 活跃用户 | 某时间范围内产生会话或消息的用户数 | 按 `userId` 去重 |
| 会话数 | 用户发起的 AI 对话次数 | 按 `conversationId` 计数 |
| 消息数 | 用户消息 + AI 回复总数，或只统计用户消息 | 需要在接口里明确口径 |
| 会话深度 | 平均每个会话有多少条消息 | `消息数 / 会话数` |
| 成功率 | AI 请求成功数量占比 | `成功调用 / 总调用` |
| 平均响应 | AI 请求平均耗时 | 建议单位毫秒或秒 |
| P95 响应 | 95% 请求能在这个时间内完成 | 用于发现慢请求 |
| 错误率 | 失败请求占比 | 超过阈值要告警 |
| 无知识命中率 | 知识库没有召回内容的比例 | 用于判断知识库质量 |
| 慢响应率 | 超过阈值的请求比例 | 例如超过 20 秒算慢 |
| 人均会话 | 活跃用户平均会话数 | `会话数 / 活跃用户` |
| 单会话消息 | 每个会话平均消息数 | `消息数 / 会话数` |

注意：指标必须能按 `24h / 7d / 30d` 查询，最好还能按租户、知识库、模型、渠道过滤。

### 4. 必须记录的日志

后台管理系统最怕“出了问题不知道谁干的、哪一步坏了、消息补偿到哪里了”。所以日志不是可选项，必须做。

| 日志类型 | 必须记录什么 | 作用 |
|---|---|---|
| 登录日志 | 用户、IP、浏览器、登录结果、失败原因、时间、traceId | 排查盗号、爆破、异常登录 |
| 操作日志 | 谁、在什么时间、对什么资源、做了什么、成功或失败 | 审计追责，尤其是删除、发布、回滚、修改配置 |
| API 访问日志 | 接口地址、请求方法、状态码、耗时、IP、traceId、脱敏参数 | 排查接口慢、接口报错、调用来源 |
| AI 调用日志 | 模型、Provider、请求耗时、token 数、成功失败、错误码、traceId | 统计成本、性能和模型稳定性 |
| 会话消息日志 | conversationId、messageId、用户问题、AI 回复、知识命中、反馈 | 还原用户完整对话 |
| 知识库处理日志 | 上传、解析、切片、向量化、索引构建、失败原因 | 排查“为什么知识库没生效” |
| 意图/流程发布日志 | 配置版本、发布人、发布时间、发布结果、回滚记录 | 避免线上流程被误改后无法追溯 |
| MQ 失败日志 | messageId、traceId、队列、错误原因、重试次数、状态 | 支撑死信队列和人工补偿 |
| 本地消息表日志 | topic、payload 摘要、状态、重试次数、下次重试时间 | 保证业务事件最终能处理 |
| XXL-JOB 执行日志 | 任务名、参数、开始时间、结束时间、耗时、成功失败、错误堆栈 | 排查定时任务和后台批处理 |
| 安全审计日志 | 权限拒绝、Token 失效、敏感配置查看/修改 | 保护后台和敏感数据 |
| 通知告警日志 | Webhook/短信/邮件发送结果、失败原因 | 确认告警有没有真的发出去 |

日志字段建议统一包含：

| 字段 | 为什么重要 |
|---|---|
| `traceId` | 串起一次请求的完整链路 |
| `tenantId` | 多租户系统必须知道是哪一个租户 |
| `operatorId` / `operatorName` | 知道是谁操作的 |
| `clientIp` | 知道从哪里操作的 |
| `resourceType` / `resourceId` | 知道操作的是哪个资源 |
| `action` | 知道做了新增、修改、删除、发布、回滚还是重试 |
| `success` | 知道操作是否成功 |
| `errorMessage` | 失败时能看到明确原因 |
| `beforeValue` / `afterValue` | 关键配置变更要知道改前改后 |
| `createTime` | 所有日志必须有时间 |

敏感内容必须脱敏，不能把密码、Token、API Key、身份证、手机号、银行卡、私钥原文写入日志。

### 5. 全链路 traceId 是什么

`traceId` 可以理解为“一次请求的身份证号”。用户点了一下按钮，后端可能会经历：

```text
前端请求
  -> 后端接口
  -> 权限校验
  -> 查询知识库
  -> 调用 AI 模型
  -> 发送 MQ 消息
  -> 本地消息表补偿
  -> 记录操作日志
```

如果每一步都带同一个 `traceId`，出问题时就能从日志里把整条链路查出来。这个脚手架里已经有这些基础：

| 模块 | 和 traceId 的关系 |
|---|---|
| `framework-web` | 接收或生成 `X-Trace-Id`，写入日志上下文 |
| `framework-feign` | 调用下游服务时透传 traceId |
| `framework-log` | 操作日志和 API 日志记录 traceId |
| `framework-mq` | 消息发送、消费、死信和补偿保留 traceId |
| `framework-local-message` | 本地消息表保存 traceId |
| `framework-monitor` | 暴露框架运行诊断信息 |
| `framework-job` | 业务服务按需接入 XXL-JOB 后，可把任务执行日志纳入 traceId 查询 |

所以后台的“链路追踪”页面应该允许输入 `traceId`，然后查出这次请求相关的 API 日志、AI 调用日志、MQ 消息、本地消息、错误记录，以及业务服务按需接入后的 XXL-JOB 执行记录。

当前 `admin-service` 已提供 `/admin/dashboard` 首页聚合接口，展示 MQ、本地消息、日志、通知、Excel 和文件中心的核心数量；也提供 `/admin/traces/{traceId}` 聚合接口，可串起操作/API 日志、MQ 失败消息和本地消息；前端“链路追踪”页面会展示汇总计数、时间线和三类明细。系统管理变更会强制写入 `sys_operation_log`，登录成功/失败会写入 `sys_login_log` 并可在“登录日志”页面查询。

系统管理已经覆盖企业后台第一版的组织基础：租户、部门、用户、角色、菜单、字典和参数。租户/部门管理在 `admin-service`
提供 CRUD API，前端系统管理菜单下提供对应页面；默认租户和总部部门作为内置种子数据保留，删除时有保护。

MQ 治理第一版已经按工程级后台闭环实现：`framework-mq` 保留 RocketMQ、Kafka、RabbitMQ 三类适配，失败消息统一落 MySQL；`admin-service` 提供失败消息查询、手动重发、人工补偿完成、人工终止、删除和清理已处理记录。`framework-local-message` 负责本地消息表、到期重试和状态流转，后台提供查询、扫描重试、人工成功、人工失败和删除。所有人工动作都会写入 `sys_operation_log`，并携带操作人、traceId、请求 URI、关键参数和结果。

通知、Excel 和文件已按“framework 提供能力、admin-service 提供管理面”的边界接入：`framework-notify`
继续提供 LOG/Webhook/短信/邮件通道抽象，后台提供通知模板、发送测试和发送记录；`framework-excel`
继续提供 EasyExcel 导入导出服务，后台提供导出任务、导入失败登记和错误明细；`framework-file`
继续提供本地/对象存储抽象，后台提供文件上传、下载、删除和元数据列表。相关管理数据统一落 MySQL，
初始化脚本包含 `framework_notify_template`、`framework_notify_record`、`framework_excel_task`、
`framework_excel_error` 和 `framework_file_record`。

### 6. 建议落地顺序

如果要一步一步做，不建议一上来就做全量大后台。推荐顺序：

1. 先做登录、用户、角色、菜单权限。
2. 再做 Dashboard 统计接口，先返回核心指标。
3. 接着做操作日志、API 日志、登录日志查询页面。
4. 然后做知识库管理、意图管理、流程配置。
5. 再补 AI 调用日志、会话日志、链路追踪。
6. 最后做 MQ 补偿管理、通知告警、系统参数和按需接入的 XXL-JOB 管理。

这样做的好处是：先保证后台能安全访问，再保证关键操作有日志，然后再逐步增强 AI 业务能力。

### 7. 和当前脚手架模块的对应关系

| 需求 | 当前可复用模块 |
|---|---|
| 登录、Token、会话 | `framework-auth` |
| 权限、角色、接口保护 | `framework-security` |
| API traceId、异常处理 | `framework-web` |
| 操作日志、API 日志 | `framework-log` |
| 健康检查、模块诊断 | `framework-monitor` |
| MQ 死信、重试、人工补偿 | `framework-mq` |
| 本地消息表、最终一致性 | `framework-local-message` |
| 后台任务 | `framework-job`，基于 XXL-JOB，业务服务按需引入 |
| 通知告警 | `framework-notify` |
| Excel 导入导出 | `framework-excel` |
| 文件上传 | `framework-file` |
| Redis key、轻量锁 | `framework-redis` |

管理后台已按应用层模块落在 `admin-service`，前端落在 `frontend/admin-web`。基础能力继续复用 `framework-*`，后台菜单、Dashboard、日志查询、MQ 人工补偿等入口不再放进 framework 模块。

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+（推荐；本地只运行 `admin-service` 时默认不纳入健康检查）

### 2. 启动基础组件

推荐用 Compose 一键启动管理后台：

```bash
cp .env.example .env
./scripts/start-compose.sh
```

访问：

- 管理后台: http://localhost:5173
- 后台 API: http://localhost:8081
- 默认账号: `admin`
- 默认密码: `Admin@123`

也可以只启动基础组件后本地跑服务：

```bash
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=framework_demo mysql:8.0
docker run -d --name redis -p 6379:6379 redis:7
mysql -uroot -proot framework_demo < sql/mysql/framework_boot_starter_init.sql
```

管理后台默认库名是 `framework_admin`：

```bash
./scripts/init-mysql.sh
```

本地启动 `admin-service`：

```bash
./scripts/start-admin.sh
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
    remote:
      default-ttl: 3600
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
    enabled: false
    admin-addresses: http://127.0.0.1:8080/xxl-job-admin
    app-name: framework-demo-job
    port: 9999
    log-path: /tmp/xxl-job/jobhandler
  file:
    enabled: true
    base-path: /tmp/framework-files
```

`framework.*` 主要模块已接入 Spring Boot 配置属性处理器，IDE 可提示配置项。`prod` / `production` profile 下必须显式配置 JWT secret。

## MySQL 初始化

所有框架表默认按 MySQL 设计。聚合脚本在 `sql/mysql/framework_boot_starter_init.sql`，执行时会先设置 `utf8mb4` 字符集和 `+08:00` 时区；模块内也各自携带：

| 模块 | 脚本 |
|---|---|
| `framework-log` | `framework-log/src/main/resources/db/mysql/sys_operation_log.sql` |
| `framework-local-message` | `framework-local-message/src/main/resources/db/mysql/framework_local_message.sql` |
| `framework-mq` | `framework-mq/src/main/resources/db/mysql/framework_mq.sql` |

聚合脚本还包含 `admin-service` 的系统管理表和种子数据：

| 表 | 说明 |
|---|---|
| `sys_user` / `sys_role` / `sys_menu` | 后台用户、角色、菜单和按钮权限 |
| `sys_user_role` / `sys_role_menu` | 用户角色、角色菜单授权 |
| `sys_tenant` / `sys_dept` | 默认租户和部门 |
| `sys_dict_type` / `sys_dict_item` | 字典类型和字典项 |
| `sys_config` | 系统参数，敏感配置返回时脱敏 |
| `sys_login_log` | 后台登录日志 |
| `framework_notify_template` / `framework_notify_record` | 通知模板和发送记录 |
| `framework_excel_task` / `framework_excel_error` | Excel 导入导出任务和错误明细 |

## 全链路 TraceId

`framework-web` 会接收或生成 `X-Trace-Id`，写入 `MDC["traceId"]` 并回写响应头。
`framework-feign` 会把当前 traceId 透传到下游；`framework-log` 的异步日志线程池和操作日志异步记录会继承 MDC。
业务自定义线程池可复用 `TraceContextTaskDecorator` 传播 traceId。
