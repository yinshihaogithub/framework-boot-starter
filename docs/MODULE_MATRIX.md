# 模块矩阵

| 模块 | 工程级职责 | 关键外部依赖 | 工程化要求 |
|---|---|---|---|
| `framework-core` | 通用常量、响应、异常、trace 工具 | 无 | 保持零业务依赖，承载跨模块基础工具 |
| `framework-web` | Web 基础、异常、CORS、traceId、过滤器 | Servlet、MyBatis 可选 | 请求链路可观测、XSS 不改写协议 Header，SQL 注入防护不误伤正常参数化 DML，ObjectMapper 使用 Spring Jackson builder |
| `framework-auth` | JWT、会话、安全登录、短信/OAuth 扩展 | Redis 可选 | 生产 profile 强校验密钥，JWT/会话/登录锁定/短信/OAuth 配置启动校验，登录失败/锁定 Redis key 归一化 username，accessToken 绑定 Redis 会话，用户 ThreadLocal 请求后恢复，外部通道可替换 |
| `framework-security` | 权限注解和 AOP | Spring AOP/MyBatis 可选 | 支持类级/方法级注解和 IgnoreToken，失败错误码清晰，DataScope SQL 会按 WHERE/AND 和 ORDER BY/LIMIT 等尾部子句安全拼接 |
| `framework-cache` | 本地/远程缓存 | Caffeine、Redis 可选 | 缺 Redis 可降级，缓存 key/TTL 快速校验，本地容量/过期时间和远程默认 TTL 启动校验，带 TTL 写入/加载/expire 不会让 L1 超过调用方 TTL，通配删除按字面值安全匹配且 Redis 层使用 SCAN，多级缓存延迟双删使用受控 daemon scheduler |
| `framework-lock` | 分布式锁 | Redisson 可选 | 缺 Redisson 不启动锁切面，SpEL key 解析失败或解析为空快速暴露，写入 Redisson 前归一化首尾空格，等待时间/持有时间配置快速校验 |
| `framework-idempotent` | 幂等控制 | Redis 可选 | key 策略清晰，BUSINESS_KEY 支持 SpEL key，解析为空快速暴露，BUSINESS_KEY 和 TOKEN 写 Redis 前归一化首尾空格，缺 Redis 不启动，失败提示可配置 |
| `framework-crypto` | 加密、哈希、脱敏 | 无 | AES 随机 IV，密码使用 BCrypt，摘要/脱敏/加解密行为有回归测试 |
| `framework-log` | 操作日志、API 日志 | MySQL 可选 | MySQL 初始化脚本、异步 trace 传播，DB 存储失败隔离，采样率/保留天数快速校验，JSON 数组/query string 入日志前脱敏 |
| `framework-rate-limiter` | 限流 | Redisson 可选 | 全局/IP/用户维度，支持自定义 SpEL key 且解析失败/解析为空快速暴露，写入 Redisson 前归一化首尾空格，单位配置快速校验，缺 Redisson 不启动 |
| `framework-mq` | RabbitMQ/Kafka/RocketMQ、失败补偿 | MQ、MySQL、Redis 可选 | provider 可切换，发送/消费抽象统一，字节数据 UTF-8 解码，发送入口和补偿配置快速校验，消费 traceId 选择首个合法候选并跳过脏值，消费幂等 key 写 Redis 前归一化，失败记录 MySQL，JDBC 字段映射和自动建表器覆盖，死信处理恢复 MDC，自动重试将空 nextRetryTime 视为到期，补偿重发保留消息链路元数据并归一化审计字段 |
| `framework-retry` | 重试、熔断 | Resilience4j | 配置化熔断器启动校验，重试/熔断注解参数快速校验且 failureRate 按比例转换，fallback 只在重试耗尽后触发；熔断小窗口不会超过最小调用量，checked exception 降级文案保留原始错误 |
| `framework-tools` | ID、树、日期等工具 | 无 | 保持轻量，不引入业务状态，树构建校验空输入/重复 ID/环，日期/树/Snowflake 行为有回归测试 |
| `framework-notify` | 日志/Webhook/扩展通知 | HTTP 可选 | 通道接口可替换且 type 不可重复，消息校验和文本/可选集合归一，Webhook 配置 URL/timeout 启动校验，直调空集合兜底和通道异常收敛为失败结果 |
| `framework-local-message` | 本地消息表 | MySQL | 本地消息表脚本和自动建表器校验、配置启动校验、JDBC 字段映射覆盖、状态流转、重试调度，发布和补偿扫描 topic 归一化，handler topic 不可重复，保留并在入库前归一化 messageId/traceId/租户/操作人/来源等补偿排障字段 |
| `framework-excel` | Excel 导入导出 | EasyExcel | 行级错误收集，最大行数和默认 sheet 名启动校验，sheet 名规则前置校验，导入文件头和模板表头校验，表头比较前归一化首尾空格 |
| `framework-datasource` | MyBatis-Plus 分页、审计 | MyBatis-Plus | 不创建数据源，只增强已有数据访问，固定 MySQL 分页方言，dbType/maxLimit/审计字段启动校验，审计字段名启动期归一化并校验为合法 Java 字段名，更新时刷新审计更新时间 |
| `framework-redis` | Redis key 与常用操作 | Redis 可选 | 缺 Redis 仍可使用 key builder，keyPrefix/TTL 启动校验，keyPrefix/namespace/parts 拼接前归一化，key/token 访问前归一化并校验，TTL 访问前校验，轻量锁 Lua 原子释放 |
| `framework-feign` | Header/trace 透传 | Feign 可选 | traceId 按框架规则净化并从 MDC fallback，默认透传租户和网关注入的用户上下文，relay header 名启动校验，空白项归一后仍保留 trace 兜底 |
| `framework-monitor` | 健康检查 | Actuator 可选 | 暴露框架健康指标、运行时诊断信息和已引入 framework 模块列表，能识别按需引入的 framework-job，健康输出应用名启动校验并禁止控制字符 |
| `framework-job` | XXL-JOB 执行器接入 | XXL-JOB、MySQL(admin 可选) | 默认不启用 executor，启用时属性层校验 admin/appName/端口/日志路径/日志保留天数并归一化文本配置，JobHandler 名称按白名单校验后同时进入本地 facade 和 XXL-JOB registry，JobService 手动触发按归一化名称查询，随模块提供独立的 XXL-JOB admin MySQL 初始化脚本 |
| `framework-file` | 文件存储抽象 | 本地文件系统默认 | 存储服务可替换，默认实现带 basePath/大小/扩展名/key 启动与访问安全约束和元数据，上传原始文件名路径片段会被归一化且缺失文件名时回退安全默认名，返回元数据使用安全展示文件名 |
| `framework-starter` | 聚合依赖 | 默认运行时模块 | 聚合常用运行时框架模块，不引入 demo 和 job |
| `admin-service` | 管理后台聚合服务 | framework-starter、MySQL、MQ/Redis 可选 | 应用层模块，聚合 Dashboard、租户/部门/用户/角色/菜单系统管理、MQ 失败消息/人工补偿、本地消息表、通知模板/发送记录、Excel 任务/错误明细、代码生成预览、日志审计、traceId 查询和监控 API；不反向依赖到 framework 模块 |
| `frontend/admin-web` | 管理后台前端 | Vue3、Vite、Element Plus | 首屏即管理控制台，包含 Dashboard、系统管理、MQ 管理、本地消息、通知中心、Excel 中心、代码生成、日志中心和监控中心页面，通过 Vite proxy 访问 `admin-service` |
| `demo` | 示例应用 | MySQL、Redis、MQ 可选 | 默认 MySQL，接入根聚合初始化脚本，演示关键能力 |
