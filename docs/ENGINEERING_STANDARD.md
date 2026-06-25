# 工程化标准

本文定义 `framework-boot-starter` 每个模块必须满足的工程级基线。

## 模块基线

- 每个模块必须有 `pom.xml` 和 `README.md`。
- README 必须写明模块依赖、配置前缀、装配行为和最小使用示例。
- 需要自动装配的模块必须使用 Spring Boot 3 `AutoConfiguration.imports` 注册。
- 可选外部依赖必须使用条件装配，缺少 Redis、MySQL、MQ、对象存储等组件时不能拖垮应用启动。
- 配置项必须放在 `framework.*` 命名空间，并生成 Spring configuration metadata。
- 业务可替换点必须暴露接口，默认实现只能覆盖常用场景。

## 数据基线

- 框架持久化表统一按 MySQL 设计。
- 带表结构的模块必须在 `src/main/resources/db/mysql/` 提供初始化脚本。
- 工程根目录必须维护聚合初始化脚本 `sql/mysql/framework_boot_starter_init.sql`。
- Demo 默认使用 MySQL，不能把 H2 作为默认数据库。

## 可观测基线

- Web 请求必须有全链路 `traceId`。
- 异步线程池、Feign、MQ 消息要传播 traceId。
- 管理/补偿类能力要记录操作人、失败原因和状态流转。
- MQ 自动重试和人工补偿必须保留原始消息身份与链路元数据，不能生成新的 `messageId`/`traceId` 断链。
- MQ provider 扩展必须至少覆盖统一发送、统一消费解码、traceId 恢复和幂等键策略；provider 特有 ACK/重试交给对应容器适配。
- 通知、补偿、导入导出等外围能力必须把可预期失败收敛为领域结果或清晰异常，避免空指针和不明失败模式。
- 文件存储默认实现必须校验大小、扩展名、路径安全和输入流，并返回可用于审计/展示的基础元数据。
- 鉴权必须区分 accessToken / refreshToken；接口鉴权必须绑定服务端会话状态，请求结束必须清理用户 ThreadLocal。
- 权限切面必须支持方法级和类级注解，公开接口要能通过 `@IgnoreToken` 明确跳过权限校验。

## 验收基线

- 每个模块至少覆盖自动配置测试。
- 关键行为必须有单元测试或上下文 runner 测试。
- 新增自动配置类必须有对应 `ApplicationContextRunner` 或 `WebApplicationContextRunner` 测试。
- 新增 `@ConfigurationProperties` 必须使用 `framework.*` 前缀，并在模块 README 明确写出完整前缀。
- 根 POM 必须保留 `spring-boot-configuration-processor`，避免配置元数据退化。
- 全仓必须通过默认 JDK 和 JDK 17 的 `mvn test`。
