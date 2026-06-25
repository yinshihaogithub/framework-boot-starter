# framework-notify

> 通知告警模块：统一消息模型，内置日志/Webhook 通道，短信和邮件通道通过接口扩展。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-notify</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.notify`。

```yaml
framework:
  notify:
    enabled: true
    default-channel: LOG
    webhook:
      url: https://example.com/webhook
      timeout: 3s
```

## 使用示例

```java
@Autowired
private NotifyService notifyService;

notifyService.send(NotifyChannelType.LOG, "库存预警", "SKU-1001 库存不足");
```

`NotifyService` 会隔离通道异常：扩展通道抛出异常时返回 `NotifyResult.failure(...)`，不会把通知故障直接抛给主业务流程。

## 扩展通道

```java
@Bean
public NotifyChannel smsNotifyChannel() {
    return new NotifyChannel() {
        public NotifyChannelType type() {
            return NotifyChannelType.SMS;
        }

        public NotifyResult send(NotifyMessage message) {
            smsClient.send(message.getReceivers(), message.getContent());
            return NotifyResult.success(type());
        }
    };
}
```
