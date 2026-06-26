# 工程化标准

本文定义 `framework-boot-starter` 每个模块必须满足的工程级基线。

## 模块基线

- 每个模块必须有 `pom.xml` 和 `README.md`。
- README 必须写明模块依赖、配置前缀、装配行为和最小使用示例。
- 需要自动装配的模块必须使用 Spring Boot 3 `AutoConfiguration.imports` 注册。
- 可选外部依赖必须使用条件装配，缺少 Redis、MySQL、MQ、对象存储等组件时不能拖垮应用启动。
- 配置项必须放在 `framework.*` 命名空间，并生成 Spring configuration metadata。
- 业务可替换点必须暴露接口，默认实现只能覆盖常用场景。
- 插件式扩展点注册必须校验扩展名，空名和重复名要启动期快速失败，不能静默跳过或覆盖业务实现。
- 默认聚合 starter 必须有依赖契约测试，只聚合通用运行时模块；XXL-JOB 等需要外部调度中心或端口暴露的能力必须保持按需单独引入。

## 数据基线

- 框架持久化表统一按 MySQL 设计。
- 带表结构的模块必须在 `src/main/resources/db/mysql/` 提供初始化脚本。
- 工程根目录必须维护聚合初始化脚本 `sql/mysql/framework_boot_starter_init.sql`，导入前设置 `utf8mb4` 字符集和 `+08:00` 时区，并用来源注释标明每段表结构来自哪个模块脚本；来源片段必须和模块脚本逐字同步。
- 外部平台库脚本必须和业务库聚合脚本分开维护；例如 XXL-JOB admin 的 MySQL 表由 `framework-job/src/main/resources/db/mysql/xxl_job_admin.sql` 单独提供，不能默认混入业务库初始化脚本。
- 生产代码进行字节和字符串转换时必须显式指定字符集，MQ payload、文件内容和外部协议数据默认按 UTF-8 处理，不能依赖 JVM 平台默认编码。
- Demo 默认使用 MySQL，不能把 H2 作为默认数据库，并且必须通过 `spring.sql.init` 指向根目录聚合初始化脚本。

## 可观测基线

- Web 请求必须有全链路 `traceId`。
- 外部传入的 `traceId` 必须做协议级净化，不能把空白、换行、控制字符或超长值写入 MDC、响应头、Feign Header、MQ Header 或持久化链路字段。
- Web trace 过滤器必须在请求结束后恢复进入过滤器前的 MDC 上下文，不能无条件清空调用方已有链路信息。
- 统一响应体必须在存在链路上下文时回填 `traceId`，方便调用方按响应定位日志；无上下文时不得主动生成 traceId 污染后台流程。
- Web XSS 过滤只能处理请求参数，不得改写 `Authorization`、traceId、租户、用户等协议 Header。
- Web SQL 注入防护只能拦截注释、`UNION SELECT`、堆叠语句、危险函数等注入特征，不能把正常参数化 `INSERT`、`UPDATE`、`DELETE` 当作风险 SQL。
- 异步线程池、Feign、MQ 消息要传播 traceId。
- Feign 透传 Header 配置必须校验 HTTP Header 名合法性，空白项要归一移除，归一后为空时仍必须保留 traceId 兜底；冒号、换行等非法协议字符要快速失败；透传 `X-Trace-Id` 时必须按框架 traceId 规则净化，不合法时回退到 MDC/新 traceId，不能把脏请求头继续发给下游。
- 监控健康输出中的应用名必须在启动期校验，不能包含换行等控制字符；空白应用名应回退到框架默认值；模块列表必须能识别按需引入的可选模块，例如 `framework-job`。
- 管理/补偿类能力要记录操作人、失败原因和状态流转；人工输入的任务名、topic、handler 名称等路由标识必须在查询和执行前归一化，不能因为首尾空白误判不存在。
- 操作日志/API 日志必须在入库或输出前脱敏，覆盖 JSON 对象、JSON 数组、嵌套对象数组和 query string；采样率、日志保留天数等配置必须启动期快速校验；DB mapper 缺失或建表/写入/清理失败不能影响业务主流程。
- MQ 自动重试、死信处理和人工补偿必须保留原始消息身份与链路元数据，不能生成新的 `messageId`/`traceId` 断链；自动重试扫描必须将 `PENDING` 且 `nextRetryTime` 为空的记录视为立即到期；死信处理结束后必须恢复进入前的 MDC 上下文；重试次数、重试间隔、死信队列和失败表名等配置必须启动期快速校验；失败消息 JDBC 仓储和自动建表器必须校验数据访问组件和动态表名，完整映射补偿排障字段，清理逻辑只能删除终态记录。
- MQ provider 扩展必须至少覆盖统一发送、统一消费解码、发送入口校验、traceId 恢复和幂等键策略；provider 特有 ACK/重试交给对应容器适配。
- MQ provider header 解析必须容忍空值和缺失值，不能因脏 trace header 在进入统一消费逻辑前中断消息消费；统一消费逻辑选择 traceId 时必须跳过非法 wrapper/header 候选，使用第一个符合框架链路规则的 traceId。
- MQ 消费幂等缓存依赖 Redis 时必须降级为可选增强；缺少 Redis 只能跳过去重缓存，不能中断正常消费和 ACK；幂等 key 在写 Redis 前必须归一化首尾空格，避免业务 key 或 fallback messageId 的隐形空格制造不同去重桶。
- 本地消息表必须持久化 `messageId`、`traceId`、上游消息 ID、租户、操作人和来源系统；这些补偿排障字段入库前必须归一化，`traceId` 必须按框架链路规则净化，非法值不能写入表；表名、最大重试次数、批大小、重试间隔和调度 fixedDelay 必须启动期快速校验；发布入口必须校验 topic/payload，并在入库前归一化 topic，不能把不可补偿或无法路由的脏消息写入表；JDBC 仓储和自动建表器必须校验数据访问组件和动态表名，补偿扫描只能拉取 `PENDING` 且已到期的消息；`LocalMessageHandler` topic 不能为空或重复。
- 通知、补偿、导入导出等外围能力必须把可预期失败收敛为领域结果或清晰异常；通知消息校验、标题/内容/Webhook URL/接收人归一、可选集合归一、Webhook 通道直调空集合兜底、通道异常和通道空结果不能以空指针形式泄漏到主业务流程；`NotifyChannel` type 不能为空或重复。
- Webhook 类外部回调必须限制 URL scheme 为 `http`/`https`；配置项 URL 非空时和超时配置都必须启动期快速校验，避免把非法配置推迟到底层客户端异常。
- Excel 导入必须校验文件头和模板表头，表头比较前要归一化首尾空格，不匹配时进入行级错误结果，不能静默导入空字段。
- Excel 导入导出配置必须快速校验，`max-rows` 必须大于 0，导出默认 sheet 名不能为空且必须符合 Excel 工作表名限制；方法入参 sheet 名也必须在写文件前校验。
- 文件存储默认实现必须校验大小、扩展名、路径安全和输入流，并返回可用于审计/展示的基础元数据；上传原始文件名必须去除路径片段并归一到安全字符，`StoredFile.originalFilename` 必须使用归一后的安全展示名，缺少有效文件名时不能漏出空指针，应回退到安全默认名。
- 文件存储本地实现必须在启动期校验 `base-path`、`max-size` 和扩展名白名单，避免空路径退化到当前工作目录、非正数大小绕过限制或空白扩展名放开异常文件。
- 数据访问增强模块不能主动创建数据源；`db-type` 必须固定为 `MYSQL`，`max-limit` 和审计字段配置必须启动期快速校验，审计字段名必须归一化首尾空格并校验为合法 Java 字段名；审计字段插入时只填空值，更新时必须刷新更新时间字段。
- 鉴权必须区分 accessToken / refreshToken；接口鉴权必须绑定服务端会话状态，请求期间隔离本次登录用户，请求结束必须恢复进入过滤器前的用户 ThreadLocal，入口前为空时清理。
- 鉴权配置必须启动期校验 JWT 密钥和过期时间、会话超时、登录失败锁定、短信验证码时间窗、密码过期天数、白名单路径和 OAuth2 启用时的必填参数。
- 登录失败计数、账号锁定、解锁和失败计数清理必须在写入 Redis 前归一化 `username` 首尾空格，并拒绝空白用户名，避免同一账号进入不同锁定桶。
- 鉴权密码过期策略必须支持关闭开关；启用时必须校验用户 ID，Redis 时间戳脏值要记录并自愈，不能让缓存污染中断登录链路。
- 权限切面必须支持方法级和类级注解，公开接口要能通过 `@IgnoreToken` 明确跳过权限校验。
- 数据权限拦截器拼接 SQL 时必须根据原 SQL 是否已有 `WHERE` 选择 `WHERE`/`AND`，并把权限条件插入到 `GROUP BY`、`ORDER BY`、`HAVING`、`LIMIT`、`OFFSET` 等尾部子句之前，不能简单在 SQL 末尾追加导致语法错误。
- 幂等、限流、分布式锁等横切切面必须在缺少 Redis/Redisson 运行依赖时跳过装配；SpEL key 解析失败或解析为空要快速暴露，不能把表达式原文、空 key 或 `null` 写入 Redis，也不能把显式 key 静默退回默认维度；幂等 BUSINESS_KEY 和 TOKEN 写入 Redis 前、限流和分布式锁显式 key 写入 Redisson 前必须归一化首尾空格，避免隐形空格制造不同控制桶；限流时间单位只能使用底层明确支持的秒/分/小时，不能静默降级；分布式锁等待时间和持有时间配置必须在访问 Redisson 前快速校验。
- 重试切面必须校验重试参数，`maxInterval` 必须大于 0，指数退避 `multiplier` 必须为有限正数；非重试异常必须直接抛出，不能进入重试耗尽 fallback；熔断注解必须校验 name、timeout、failureRate、滑动窗口和打开状态等待时间，并将比例配置转换为底层组件需要的百分比语义；熔断小窗口的最小调用量不能超过窗口大小，checked exception 降级文案必须保留原始错误；熔断器配置文件中的名称、阈值、窗口、最小调用量、半开请求数和打开状态等待时间必须启动期快速校验。
- 缓存服务必须校验 key、TTL、loader 等输入；通配删除只能把 `*` 当通配符，其他正则/Redis glob 特殊字符按字面值处理，Redis 层必须使用 `SCAN MATCH`，不能使用阻塞式 `KEYS`；本地容量、本地过期时间、远程默认 TTL 配置必须真实作用于缓存装配并在启动期快速校验；带 TTL 写入、loader 加载和 `expire` 必须同步约束 L1，不能让本地缓存超过调用方 TTL；多级缓存延迟双删必须使用受控 daemon scheduler，不能按删除请求无界创建线程。
- Redis 基础服务必须启动期校验 `key-prefix`、默认 TTL 和锁 TTL，并在访问前归一化和校验 key、token 等输入，TTL 必须访问前校验；Redis key builder 必须在拼接前归一化 `key-prefix`、namespace 和 key parts，避免隐形空格污染 key 空间；分布式轻量锁释放必须使用 token compare-and-delete 的原子脚本。
- 加密工具不得使用固定 IV 做对称加密；密码存储必须使用带盐慢哈希，摘要算法只能用于指纹和完整性校验。
- 通用工具类必须有确定性回归测试；树构建要支持 `null` 根 parentId，对空输入返回空列表，节点 ID 必须非空且唯一，自引用或环形父子关系必须快速失败；时间转换必须固定框架默认时区。
- XXL-JOB executor 启用时必须在配置属性层校验 admin 地址、appName、端口、日志路径和日志保留天数；注入 executor 前必须归一化文本配置首尾空格；`JobHandler` 扩展必须校验任务名，任务名只允许字母、数字、点、下划线和短横线，空名、非法名和重复名要快速失败；本地触发 facade 和 XXL-JOB registry 适配必须使用同一套注册规则；模块必须提供可选的 XXL-JOB admin MySQL 初始化脚本。

## 验收基线

- 每个模块至少覆盖自动配置测试。
- 关键行为必须有单元测试或上下文 runner 测试。
- 新增自动配置类必须有对应 `ApplicationContextRunner` 或 `WebApplicationContextRunner` 测试。
- 新增 `@ConfigurationProperties` 必须使用 `framework.*` 前缀，并在模块 README 明确写出完整前缀。
- 根 POM 必须保留 `spring-boot-configuration-processor`，避免配置元数据退化。
- 全仓必须通过默认 JDK 和 JDK 17 的 `mvn test`。
