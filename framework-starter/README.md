# framework-starter

> 聚合 Starter：一次引入常用脚手架能力。

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果业务只需要部分能力，建议按模块单独引入，避免无关依赖进入应用。

已聚合：core、web、auth、security、cache、lock、idempotent、crypto、log、rate-limiter、
mq、retry、tools、notify、local-message、excel、datasource、redis、feign、monitor、file。

未默认聚合：job。XXL-JOB 执行器涉及调度中心地址和端口暴露，建议业务按需单独引入 `framework-job`。

如启用日志、MQ 失败补偿、本地消息表等持久化能力，统一使用 MySQL；可执行根目录聚合脚本
`sql/mysql/framework_boot_starter_init.sql` 初始化框架表结构。
