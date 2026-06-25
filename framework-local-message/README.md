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

> 需要业务应用提供 MySQL `DataSource`。模块只依赖 `spring-jdbc`，不会主动触发 Spring Boot 数据源自动配置。

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

## 表结构

自动建表开启时会创建 `framework_local_message`：

MySQL 初始化脚本：`framework-local-message/src/main/resources/db/mysql/framework_local_message.sql`。工程根目录也提供聚合脚本：`sql/mysql/framework_boot_starter_init.sql`。

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `topic` | 消息主题 |
| `business_key` | 业务唯一键 |
| `payload` | 消息体 JSON |
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
