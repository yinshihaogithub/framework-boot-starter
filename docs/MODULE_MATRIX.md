# 模块矩阵

| 模块 | 工程级职责 | 关键外部依赖 | 工程化要求 |
|---|---|---|---|
| `framework-core` | 通用常量、响应、异常、trace 工具 | 无 | 保持零业务依赖，承载跨模块基础工具 |
| `framework-web` | Web 基础、异常、CORS、traceId、过滤器 | Servlet | 请求链路可观测、过滤器可配置 |
| `framework-auth` | JWT、会话、安全登录、短信/OAuth 扩展 | Redis 可选 | 生产 profile 强校验密钥，accessToken 绑定 Redis 会话，外部通道可替换 |
| `framework-security` | 权限注解和 AOP | Spring AOP | 支持类级/方法级注解和 IgnoreToken，失败错误码清晰 |
| `framework-cache` | 本地/远程缓存 | Caffeine、Redis 可选 | 缺 Redis 可降级，本地缓存可配置 |
| `framework-lock` | 分布式锁 | Redisson 可选 | 缺 Redisson 不启动锁切面 |
| `framework-idempotent` | 幂等控制 | Redis 可选 | key 策略清晰，失败提示可配置 |
| `framework-crypto` | 加密、哈希、脱敏 | 无 | 工具方法稳定，避免隐式弱算法默认用于敏感场景 |
| `framework-log` | 操作日志、API 日志 | MySQL 可选 | MySQL 初始化脚本、异步 trace 传播 |
| `framework-rate-limiter` | 限流 | Redisson 可选 | 全局/IP/用户维度，缺依赖不启动 |
| `framework-mq` | RabbitMQ/Kafka/RocketMQ、失败补偿 | MQ、MySQL、Redis 可选 | provider 可切换，发送/消费抽象统一，失败记录 MySQL，补偿重发保留消息链路元数据 |
| `framework-retry` | 重试、熔断 | Resilience4j | 配置化熔断器，fallback 行为可验证 |
| `framework-tools` | ID、树、日期等工具 | 无 | 保持轻量，不引入业务状态 |
| `framework-notify` | 日志/Webhook/扩展通知 | HTTP 可选 | 通道接口可替换，失败不影响主流程 |
| `framework-local-message` | 本地消息表 | MySQL | 本地消息表脚本、状态流转、重试调度 |
| `framework-excel` | Excel 导入导出 | EasyExcel | 行级错误收集，最大行数限制，导入文件头校验 |
| `framework-datasource` | MyBatis-Plus 分页、审计 | MyBatis-Plus | 不创建数据源，只增强已有数据访问 |
| `framework-redis` | Redis key 与常用操作 | Redis 可选 | 缺 Redis 仍可使用 key builder |
| `framework-feign` | Header/trace 透传 | Feign 可选 | traceId 从 MDC fallback |
| `framework-monitor` | 健康检查 | Actuator 可选 | 暴露框架健康指标 |
| `framework-job` | 定时任务注册与手动触发 | Spring Scheduler | 线程池可配置，任务接口统一 |
| `framework-file` | 文件存储抽象 | 本地文件系统默认 | 存储服务可替换，默认实现带大小/扩展名约束和元数据 |
| `framework-starter` | 聚合依赖 | 所有模块 | 聚合所有运行时框架模块，不引入 demo |
| `demo` | 示例应用 | MySQL、Redis、MQ 可选 | 默认 MySQL，演示关键能力 |
