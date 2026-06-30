# framework-local-message

> 本地消息表模块：基于数据库表保存待投递消息，支持状态流转、失败重试和按 topic 分发处理器。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-local-message</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 需要业务应用提供 MySQL `DataSource` 和 MyBatis 运行环境。模块默认使用注解 Mapper，无需 XML Mapper。
> Mapper repository 和自动建表器会校验动态表名，表名只允许字母、数字和下划线，避免动态 SQL 表名注入。

## 配置

配置前缀：`framework.local-message`。

```yaml
framework:
  local-message:
    enabled: true
    table-name: framework_local_message
    auto-create-table: true
    max-retry: 3
    batch-size: 100
    retry-interval: 1m
    scheduler:
      enabled: true
      fixed-delay: 30000
```

启动期会校验 `table-name` 只能包含字母、数字和下划线，`max-retry`、`batch-size`、`retry-interval` 和 `scheduler.fixed-delay` 必须大于 0。即使业务应用还没有提供 `DataSource`，这些配置错误也会快速失败。

## 表结构

自动建表开启时会创建 `framework_local_message`：

MySQL 初始化脚本：`framework-local-message/src/main/resources/db/mysql/framework_local_message.sql`。工程根目录也提供聚合脚本：`sql/mysql/framework_boot_starter_init.sql`。

Mapper repository 会完整映射 `message_id`、`trace_id`、上游消息 ID、租户、操作人、来源系统、状态、重试次数和下次重试时间；补偿扫描只拉取 `PENDING` 且已到期的消息。

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `message_id` | 本地消息唯一标识，便于和 MQ 消息、补偿记录关联 |
| `trace_id` | 全链路追踪 ID |
| `parent_message_id` | 上游消息 ID |
| `topic` | 消息主题 |
| `business_key` | 业务唯一键 |
| `tenant_id` | 租户 ID |
| `operator` | 操作人 |
| `source` | 消息来源系统 |
| `payload` | 消息体 JSON，使用 `LONGTEXT` 存储 |
| `status` | `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED` |
| `retry_count` | 已重试次数 |
| `max_retry` | 最大重试次数 |
| `next_retry_time` | 下次重试时间 |
| `error_message` | 最近失败原因 |

## 使用示例

```java
@Autowired
private LocalMessageService localMessageService;

localMessageService.publish("order.created", orderNo, jsonPayload);

localMessageService.publish(new LocalMessage()
        .setTopic("order.created")
        .setBusinessKey(orderNo)
        .setTenantId(tenantId)
        .setOperator(operator)
        .setSource("order-service")
        .setPayload(jsonPayload));
```

```java
@Bean
public LocalMessageHandler orderCreatedHandler() {
    return new LocalMessageHandler() {
        public String topic() {
            return "order.created";
        }

        public void handle(LocalMessage message) {
            mqProducer.send("order.exchange", "order.created", message.getPayload());
        }
    };
}
```

发布入口会先 trim `topic`、`messageId`、上游消息 ID、业务键、租户、操作人和来源系统再入库；`traceId` 会按框架链路规则净化，非法值会回退到当前 MDC 或生成安全 traceId。补偿扫描处理历史消息时也会在查询 handler 前归一化 `topic`，避免空白字符导致消息找不到处理器而长期停留在 `PENDING`。

`LocalMessageHandler.topic()` 会 trim 后注册，不能为空，不能重复；重复 topic 会在启动期快速失败，避免不同处理器互相覆盖。
