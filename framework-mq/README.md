# framework-mq

> MQ 封装模块：支持 RabbitMQ、Kafka、RocketMQ 三种 provider，统一消息包装、traceId 传播、失败消息 MySQL 落库、重试调度和人工补偿管理。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-mq</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 推荐配置 MySQL + Redis，再按业务选择 RabbitMQ / Kafka / RocketMQ。MySQL 保存失败消息和人工补偿记录，Redis 仅用于消费幂等缓存。未提供 Redis 时消费者仍可消费消息，只是不做跨进程幂等缓存；未提供对应 provider Bean 或 `DataSource` 时，相关 Bean 会自动跳过，避免 starter 直接拖垮启动。

## 配置

配置前缀：`framework.mq`。

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/framework_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
  kafka:
    bootstrap-servers: localhost:9092

framework:
  mq:
    enabled: true
    provider: RABBIT # RABBIT / KAFKA / ROCKET
    auto-create-table: true
    failed-message-table-name: framework_mq_failed_message
    max-retry: 3
    dead-letter:
      enabled: true
      queue: framework.dead.letter.queue
    retry:
      fixed-delay: 30000
```

RabbitMQ 下 `RabbitTemplate` 已启用 publisher confirm / returns callback 的基础日志。Kafka 下会注册 `KafkaMqProducer`。RocketMQ 下业务引入 RocketMQ starter 后，会基于 `rocketMQTemplate` 注册 `RocketMqProducer`。

MySQL 初始化脚本：`framework-mq/src/main/resources/db/mysql/framework_mq.sql`。工程根目录也提供聚合脚本：`sql/mysql/framework_boot_starter_init.sql`。

失败消息 JDBC repository 和自动建表器会校验 `JdbcTemplate` 和动态表名，表名只允许字母、数字和下划线；字段映射会保留 `messageId`、`traceId`、上游消息 ID、业务 key、消息类型、租户、操作人、来源和人工补偿备注。已处理记录清理只删除 `SUCCESS`、`EXHAUSTED`、`MANUAL` 终态记录，不会误删待补偿消息。

配置启动期会快速校验：`max-retry` 必须大于 0，`retry.fixed-delay` 必须大于 0，`dead-letter.queue` 在启用死信监听时不能为空，`failed-message-table-name` 只能包含字母、数字和下划线。

## 核心类

| 类 | 说明 |
|---|---|
| `MessageWrapper<T>` | 消息包装器（messageId/traceId/parentMessageId/businessKey/type/payload/timestamp） |
| `MqMessageSender` | 通用发送接口，Rabbit/Kafka/Rocket 三个 provider 共用 |
| `MqMessageSenderRegistry` | 按 `framework.mq.provider` 选择当前发送器 |
| `MqProducer` | RabbitMQ 生产者：普通/延迟/TTL 消息发送 |
| `KafkaMqProducer` | Kafka 生产者：topic/key 发送 |
| `RocketMqProducer` | RocketMQ 生产者：topic/tag 发送 |
| `AbstractMessageWrapperConsumer<T>` | provider-neutral 消费基类：反序列化、traceId 恢复、幂等标记 |
| `AbstractMqConsumer<T>` | RabbitMQ 消费者基类：反序列化+幂等+ACK/NACK+重试 |
| `AbstractKafkaMqConsumer<T>` | Kafka `ConsumerRecord` 适配消费基类 |
| `AbstractRocketMqConsumer<T>` | RocketMQ String body 适配消费基类，不强依赖 Rocket starter |
| `MqQueueBuilder` | RabbitMQ 队列声明工具：Direct/Topic/DLX/Delay |
| `DeadLetterHandler` | 死信处理器：监听死信队列，持久化失败记录到 MySQL |
| `MqRetryScheduler` | 重试调度器：定时扫描失败消息，指数退避重试 |
| `MqAutoConfiguration` | 自动配置类 |

发送入口会校验 `payload`、消息类型、`messageId`、topic/routingKey 和延迟/TTL 参数；非法消息会在进入 MQ 客户端前抛出清晰异常。

## 装配行为

| 依赖条件 | 注册 Bean |
|---|---|
| 有 Rabbit `ConnectionFactory` | `RabbitTemplate` / `RabbitAdmin` / `MqProducer` / listener container factory |
| 有 `KafkaOperations` | `KafkaMqProducer` |
| 有 `rocketMQTemplate` | `RocketMqProducer` |
| 有任意 provider sender | `MqMessageSenderRegistry` |
| 有 `DataSource` | `MqFailedMessageRepository` / `MqTableInitializer` / `DeadLetterHandler` |
| 同时有 provider sender + MySQL | 重试调度器 |
| 同时有 Rabbit + MySQL | Rabbit 死信监听器 |

## 使用示例

### 1. 声明队列

```java
@Configuration
public class MqConfig {

    @Bean
    public QueueTriple demoQueue() {
        return MqQueueBuilder.buildDirect("demo.exchange", "demo.queue", "demo.routing.key");
    }
}
```

### 2. RabbitMQ 发送消息

```java
@Autowired
private MqProducer mqProducer;

// 普通消息
mqProducer.send("demo.exchange", "demo.routing.key", payload);

// 带业务 Key（用于消费端幂等）
mqProducer.send("demo.exchange", "demo.routing.key", orderNo, orderDTO);

// 延迟消息（需安装 rabbitmq_delayed_message_exchange 插件）
mqProducer.sendWithDelay("demo.exchange", "demo.routing.key", payload, 5000); // 5秒后投递

// TTL 消息（超时进入死信队列）
mqProducer.sendWithTtl("demo.exchange", "demo.routing.key", payload, 30000); // 30秒后死信
```

### 3. Kafka / RocketMQ 发送消息

```java
@Autowired
private MqMessageSenderRegistry senderRegistry;

// framework.mq.provider=KAFKA 时：destination=topic，routingKey=key
senderRegistry.activeSender().send("order-topic", orderNo, orderDTO);

// framework.mq.provider=ROCKET 时：destination=topic，routingKey=tag
senderRegistry.activeSender().send("order-topic", "created", orderDTO);
```

### 4. RabbitMQ 消费消息

```java
@Component
public class OrderConsumer extends AbstractMqConsumer<OrderDTO> {

    private final OrderService orderService;

    public OrderConsumer(StringRedisTemplate redis, OrderService orderService) {
        super(redis, OrderDTO.class);
        this.orderService = orderService;
    }

    @RabbitListener(queues = "demo.queue")
    public void handle(Message message, Channel channel) throws Exception {
        handleMessage(message, channel);  // 调用基类方法
    }

    @Override
    protected void doConsume(MessageWrapper<OrderDTO> wrapper) throws Exception {
        OrderDTO order = wrapper.getPayload();
        orderService.process(order);
    }
}
```

### 5. Kafka / RocketMQ 消费消息

```java
@Component
public class OrderKafkaConsumer extends AbstractKafkaMqConsumer<OrderDTO> {

    public OrderKafkaConsumer(StringRedisTemplate redis) {
        super(redis, OrderDTO.class);
    }

    @KafkaListener(topics = "order-topic")
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        handleRecord(record);
    }

    @Override
    protected void doConsume(MessageWrapper<OrderDTO> wrapper) {
        orderService.process(wrapper.getPayload());
    }
}
```

```java
@Component
public class OrderRocketConsumer extends AbstractRocketMqConsumer<OrderDTO> {

    public OrderRocketConsumer(StringRedisTemplate redis) {
        super(redis, OrderDTO.class);
    }

    // RocketMQ listener method receives the JSON body from RocketMQ starter.
    public void onMessage(String body) throws Exception {
        handleMessage(body);
    }

    @Override
    protected void doConsume(MessageWrapper<OrderDTO> wrapper) {
        orderService.process(wrapper.getPayload());
    }
}
```

### 6. 订单超时取消场景

```java
// 下单时发送 15 分钟延迟消息
mqProducer.sendWithDelay("order.timeout.exchange", "order.timeout",
    MessageWrapper.of(orderNo, orderDTO), 15 * 60 * 1000);

// 消费者：15 分钟后收到消息，检查是否已支付
@RabbitListener(queues = "order.timeout.queue")
public void handle(Message message, Channel channel) throws Exception {
    handleMessage(message, channel);
}

@Override
protected void doConsume(MessageWrapper<OrderDTO> wrapper) {
    OrderDTO order = wrapper.getPayload();
    if (orderService.isUnpaid(order.getOrderNo())) {
        orderService.cancel(order.getOrderNo());
        stockService.restore(order.getSkuId(), order.getQuantity());
    }
}
```

## 消费者基类能力

| 能力 | 说明 |
|---|---|
| 反序列化 | 自动将 JSON 反序列化为 `MessageWrapper<T>` |
| 幂等消费 | 基于 Redis SETNX，key = businessKey 或 messageId，写入 Redis 前归一化首尾空格，TTL 7天；缺少 Redis 时跳过幂等缓存 |
| 手动 ACK | 消费成功 `basicAck`，失败 `basicNack`（不重新入队） |
| 重试控制 | 重试次数 < 3 时 NACK 进入死信重试；≥ 3 时 ACK 进入死信兜底 |
| traceId 传播 | 生产者写入 `X-Trace-Id` Header，消费者恢复 MDC，消费结束后恢复调用方上下文 |
| 字符集 | RabbitMQ body、Kafka header 等字节数据统一按 UTF-8 解码，避免平台默认编码导致消息体乱码 |
| 死信兜底 | 超过重试次数的消息由 `DeadLetterHandler` 持久化到 MySQL，并在处理结束后恢复进入前的 MDC 上下文 |

Kafka/RocketMQ 使用 `AbstractMessageWrapperConsumer` 同一套解码、traceId 恢复和幂等标记；异常继续抛给业务使用的 listener 容器，由 Kafka/RocketMQ 自身重试/DLQ 策略接管。Kafka trace header 存在但 value 为空时会按未提供 header 处理，统一消费逻辑会跳过非法 wrapper/header traceId 候选并使用第一个合法 traceId，避免 provider 脏 header 或脏消息体中断消费链路。

## 重试机制

```
消费失败 → NACK → 死信队列
  → DeadLetterHandler 持久化到 MySQL（状态 PENDING）
  → MqRetryScheduler 每 30s 扫描
    → 按原 MessageWrapper 重发到原交换机/Topic（状态 RETRYING）
      → 成功 → SUCCESS
      → 失败 → retryCount + 1
        → < 3 → 指数退避等待下次重试
        → ≥ 3 → EXHAUSTED（等待人工处理）
```

自动重试和人工重发都会优先从失败记录的 `payload` 还原原始 `MessageWrapper`，保留 `messageId`、`traceId`、`parentMessageId`、`businessKey` 和 `type`。历史 raw payload 记录会使用失败表里的元数据补齐包装层，避免补偿链路生成新的消息身份。自动重试扫描会把 `nextRetryTime` 为空的 `PENDING` 记录视为立即到期，避免历史或手工导入记录永久挂起。人工重发的 `operator` 和 `remark` 入库前会 trim，空白值按未提供处理，避免审计字段被表单空格污染。

## 管理控制台

管理控制台 REST API 已迁移到应用层 `admin-service`，`framework-mq` 只保留 MQ 发送、消费、死信、重试和仓储能力。

| 接口 | 方法 | 路径 | 功能 |
|---|---|---|---|
| 统计概览 | GET | `/admin/mq/stats` | 各状态消息数 + 队列列表 |
| 失败消息列表 | GET | `/admin/mq/failed-messages` | 分页 + 筛选（队列/状态/traceId/业务Key/类型） |
| 消息详情 | GET | `/admin/mq/failed-messages/{id}` | 完整信息 |
| 手动重发 | POST | `/admin/mq/failed-messages/{id}/retry` | 单条重发，支持 operator/remark |
| 批量重发 | POST | `/admin/mq/failed-messages/batch-retry` | 批量重发，支持 operator/remark |
| 删除记录 | DELETE | `/admin/mq/failed-messages/{id}` | 删除单条 |
| 清空已处理 | DELETE | `/admin/mq/failed-messages/clean` | 清理 SUCCESS/EXHAUSTED |
| 队列列表 | GET | `/admin/mq/queues` | 队列信息 |

## 消费者容器配置

| 参数 | 默认值 |
|---|---|
| ACK 模式 | MANUAL（手动） |
| 并发消费者 | 3 |
| 最大并发 | 10 |
| 预取 | 10 |
| 失败重试间隔 | 5000ms |

## Redis Key

| Key | 类型 | TTL | 用途 |
|---|---|---|---|
| `framework:mq:consumed:{key}` | String | 7天 | 消费幂等标记 |
