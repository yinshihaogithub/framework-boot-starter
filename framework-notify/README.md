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

`framework.notify.webhook.url` 可以留空，由单条消息传入 `webhookUrl`；如果配置了固定 URL，启动期会校验它必须是合法 `http` / `https` 地址。`default-channel`、`webhook` 配置对象和 `webhook.timeout` 也会在启动期校验。

## 使用示例

```java
@Autowired
private NotifyService notifyService;

notifyService.send(NotifyChannelType.LOG, "库存预警", "SKU-1001 库存不足");
```

`NotifyService` 会隔离通道异常：扩展通道抛出异常或返回 `null` 时返回 `NotifyResult.failure(...)`，不会把通知故障直接抛给主业务流程。

## 工程约束

- `NotifyService` 对可预期失败返回 `NotifyResult.failure(...)`，不向主业务流程抛出空指针、空结果或通道异常。
- `NotifyMessage`、标题和内容不能为空；校验失败时不会触达具体通知通道。标题、内容、`webhookUrl` 和接收人会在入口去除首尾空格，空白接收人会被移除；`receivers` 和 `templateParams` 为空时会归一为空集合，扩展通道可以按稳定对象处理。
- 未指定通道时使用 `framework.notify.default-channel`；目标通道未注册时返回失败结果。
- `NotifyChannel.type()` 不能为空，同一通道类型不能重复注册；非法或重复通道会在启动期快速失败。
- 配置项 `framework.notify.webhook.url` 非空时只允许 `http` / `https`，`framework.notify.webhook.timeout` 必须大于 0；消息级非法 URL 会返回清晰失败结果。
- `WebhookNotifyChannel` 直接调用时也会把空 `receivers`、`templateParams` 作为空集合写入请求体，并保证异常失败信息非空。

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
