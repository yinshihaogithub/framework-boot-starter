# 系统设计场景方案

> 来源：小林coding 系统设计面试题 + 淘宝/京东实战经验
> 关联：framework-boot-starter 脚手架各模块
> 日期：2026-06-24

---

## 场景一：秒杀系统

### 核心挑战

高并发、低库存、短时间爆发式访问，10万人抢10个商品。

### 四层防线（层层漏斗）

```
10万请求 → 前端层(拦截80%) → 网关层(限流拦截15%) → 缓存层(Redis原子扣减) → MQ → DB(串行)
                ↓                    ↓                      ↓                ↓
            CDN+验证码            IP限流+黑名单         Lua脚本扣库存      乐观锁兜底
```

### 各层方案

| 层 | 方案 | 拦截比例 |
|---|---|---|
| 前端层 | 静态资源CDN + 验证码 + 按钮防抖 + 答题削峰 | ~80% |
| 网关层 | IP限流 + 黑名单 + Token鉴权 + 设备指纹 | ~15% |
| 缓存层 | Redis Lua 原子扣减 + 一人一单校验(SETNX) | 剩余请求 |
| MQ层 | Kafka/RabbitMQ 异步下单削峰 | 保护DB |
| DB层 | 乐观锁兜底 `WHERE stock > 0` | 最终一致 |

### 防超卖核心

```lua
-- Redis Lua 原子扣减
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then return -1 end           -- 库存不存在
if stock < tonumber(ARGV[1]) then return 0 end  -- 库存不足
redis.call('DECRBY', KEYS[1], ARGV[1])
-- 一人一单校验
if redis.call('SADD', KEYS[2], ARGV[2]) == 0 then return -2 end  -- 已抢过
return 1
```

### 脚手架模块对应

| 能力 | 脚手架模块 | 注解/类 |
|---|---|---|
| 网关限流 | framework-rate-limiter | `@RateLimit(limit=100, window=1)` |
| 分布式锁 | framework-lock | `@DistributedLock(key="seckill:#{#skuId}")` |
| 幂等防重 | framework-idempotent | `@Idempotent(key="seckill:#{#userId}_#{#skuId}")` |
| 异步下单 | framework-mq | `mqProducer.send("order.exchange", "seckill", orderNo, orderDTO)` |
| 操作日志 | framework-log | `@OperationLog(module="秒杀", action="抢购")` |

### 订单超时取消

```
下单成功 → 发延迟消息(15分钟) → 消费者检查支付状态
  → 已支付 → 忽略
  → 未支付 → 取消订单 + 归还库存
```

```java
// 发送延迟消息
mqProducer.sendWithDelay("order.timeout.exchange", "order.timeout",
    MessageWrapper.of(orderNo, orderDTO), 15 * 60 * 1000);
```

---

## 场景二：短链系统

### 核心需求

长链转短链 + 短链跳回长链 + 点击统计。

### 设计方案

```
用户输入长URL
    │
    ▼
分布式ID发号器(Snowflake) → 生成唯一数字ID
    │
    ▼
Base62编码 → 6位短链 (可存568亿链接)
    │
    ▼
存储: MySQL(长→短映射) + Redis(缓存)
    │
    ▼
返回: https://t.cn/abc123
```

### 跳转流程

```
访问短链 → Redis查缓存 → 命中 → 302重定向到长链 + 记录点击
                      ↓ 未命中
                    MySQL查 → 命中 → 回填Redis + 302重定向
                             ↓ 未命中
                           返回404
```

**为什么用302不用301？** 301是永久重定向，浏览器会缓存，后续不再请求服务器，无法统计点击量。302每次都请求服务器。

### 防护

| 问题 | 方案 |
|---|---|
| 缓存穿透 | 布隆过滤器拦截不存在的短链 |
| 恶意刷短链 | 网关IP限流 `@RateLimit` |
| 短链冲突 | Snowflake ID 全局唯一，不会冲突 |
| 短链过期 | 定时清理 + 软删除 |

### 脚手架对应

| 能力 | 模块 |
|---|---|
| 分布式ID | framework-tools `SnowflakeIdGenerator` |
| 缓存 | framework-cache 多级缓存 |
| 限流 | framework-rate-limiter `@RateLimit` |

---

## 场景三：点赞系统

### 核心挑战

高频读写，大V一条微博可能有百万点赞。

### 三步设计

```
用户点赞
    │
    ├─ Step1: Redis Set 存储（内存操作，极速）
    │   sadd like:{articleId} {userId}     → 点赞
    │   srem like:{articleId} {userId}     → 取消
    │   sismember like:{articleId} {userId} → 查状态
    │   scard like:{articleId}             → 赞数
    │
    ├─ Step2: MQ 异步持久化（削峰落库）
    │   点赞消息 → Kafka → 消费者批量写入 MySQL
    │
    └─ Step3: 热点防护（大V文章）
        本地缓存(Caffeine)缓存赞数
        1-2秒汇总合并 → 更新 Redis → 异步落库
```

### 数据模型

```sql
-- 点赞记录表
CREATE TABLE user_like (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    target_id   BIGINT NOT NULL COMMENT '文章/评论ID',
    target_type TINYINT NOT NULL COMMENT '1文章 2评论',
    create_time DATETIME DEFAULT NOW(),
    UNIQUE KEY uk_user_target (user_id, target_id, target_type),
    INDEX idx_target (target_id, target_type)
);

-- 点赞计数表（汇总）
CREATE TABLE like_count (
    target_id   BIGINT PRIMARY KEY,
    target_type TINYINT,
    count       BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT NOW()
);
```

### 脚手架对应

| 能力 | 模块 |
|---|---|
| Redis操作 | framework-cache |
| 异步落库 | framework-mq `mqProducer.send()` |
| 幂等消费 | framework-mq `AbstractMqConsumer` |
| 限流防刷 | framework-rate-limiter |

---

## 场景四：订单超时自动取消

### 五种方案对比

| 方案 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| 定时轮询 | 简单 | DB压力大，延迟高 | 小规模 |
| DelayQueue | 高效 | 内存不持久，崩溃丢失 | 单机 |
| 时间轮 | 精确 | 内存不持久 | 单机 |
| Redis ZSet | 分布式 | 实时性一般 | 中规模 |
| **MQ延迟队列** | 异步高效、可扩展 | 配置复杂 | **大规模推荐** |

### MQ延迟队列方案（推荐）

```
下单成功 → 发延迟消息(30分钟) → 延迟交换机(TTL)
                                    ↓ 30分钟后
                              消费者收到消息
                                    ↓
                              检查订单支付状态
                              ├─ 已支付 → 忽略
                              └─ 未支付 → 取消订单 + 归还库存
```

```java
// 发送延迟消息（30分钟）
mqProducer.sendWithDelay("order.timeout.exchange", "order.timeout",
    MessageWrapper.of(orderNo, orderDTO), 30 * 60 * 1000);

// 消费者
@Component
public class OrderTimeoutConsumer extends AbstractMqConsumer<OrderDTO> {
    @RabbitListener(queues = "order.timeout.queue")
    public void handle(Message message, Channel channel) throws Exception {
        handleMessage(message, channel);
    }

    @Override
    protected void doConsume(MessageWrapper<OrderDTO> wrapper) {
        OrderDTO order = wrapper.getPayload();
        Order dbOrder = orderService.getById(order.getOrderNo());
        if (dbOrder.getStatus() == OrderStatus.UNPAID) {
            orderService.cancel(dbOrder);  // 取消订单
            stockService.restore(order.getSkuId(), order.getQuantity());  // 归还库存
        }
    }
}
```

### 脚手架对应

| 能力 | 模块 |
|---|---|
| 延迟消息 | framework-mq `sendWithDelay()` |
| 幂等消费 | framework-mq `AbstractMqConsumer` |
| 死信兜底 | framework-mq `DeadLetterHandler` |

---

## 场景五：分布式ID发号器

### 方案对比

| 方案 | 唯一性 | 趋势递增 | 性能 | 依赖 |
|---|---|---|---|---|
| UUID | 全局唯一 | 无序 | 高 | 无 |
| DB自增 | 全局唯一 | 递增 | 低 | DB |
| **雪花算法** | 全局唯一 | 趋势递增 | 极高(本地内存) | 时钟 |
| **号段模式** | 全局唯一 | 趋势递增 | 高(本地内存) | DB(弱依赖) |
| Redis INCR | 全局唯一 | 递增 | 中 | Redis |

### 雪花算法（脚手架已实现）

```java
// framework-tools 已提供
SnowflakeIdGenerator generator = new SnowflakeIdGenerator(workerId);
long id = generator.nextId();  // 本地内存计算，无网络IO
```

结构：`1位符号 + 41位时间戳 + 10位机器ID + 12位序列号`

### 号段模式（美团Leaf思想）

```
应用启动 → 向DB申请号段 [1, 1000] → 内存 AtomicLong 分发
              ↓ 用到20%时
          异步申请下一号段 [1001, 2000]
              ↓
          双Buffer切换，无缝衔接

即使DB宕机 → 靠囤积号码仍可运行一段时间
```

---

## 场景六：可重入分布式锁

### 设计要素

| 要素 | 说明 |
|---|---|
| Lock Key | 锁定的资源标识 |
| Owner ID | 持有者标识（线程ID/UUID） |
| 计数器 | 可重入次数 |
| 过期时间 | 防死锁 |
| 看门狗 | 自动续期 |

### Redis Hash 存储

```json
{
  "lock:resource_1": {
    "owner": "thread_123",
    "count": 3,
    "expires_at": "2026-06-24T18:00:00Z"
  }
}
```

### 核心操作

| 操作 | 逻辑 |
|---|---|
| 获取锁 | key不存在 → 创建(count=1)；owner匹配 → count++ |
| 释放锁 | count--，减到0 → 删除key |
| 锁续期 | 处理中更新expires_at（看门狗每10s续期30s） |
| 超时恢复 | 自动过期，防止死锁 |

### 脚手架已实现

```java
// framework-lock 已提供
@DistributedLock(
    key = "order:#{#orderId}",
    waitTime = 3,
    leaseTime = -1,  // -1 启用看门狗自动续期
    fallback = "handleLockFail"
)
public void processOrder(Long orderId) { ... }
```

---

## 场景七：QPS突增10倍

### 紧急止血

| 措施 | 动作 |
|---|---|
| 限流 | `@RateLimit` 设阈值，超限快速失败 |
| 降级 | `@CircuitBreaker` 熔断非核心服务，保下单+支付 |
| 扩容 | K8s HPA 自动增加容器实例 |

### 架构演进

```
读流量 ──→ 多级缓存
            ├─ L1 Caffeine 本地缓存 (<1ms)
            └─ L2 Redis 分布式缓存 (<3ms)
            └─ L3 DB 兜底

写流量 ──→ MQ 异步削峰
            请求 → Kafka/RabbitMQ → 消费者按DB承受速度消费

数据库 ──→ 读写分离 + 分库分表
            主库写 / 多从库读
            大表拆分 (ShardingSphere)
```

### 脚手架对应

| 能力 | 模块 | 注解 |
|---|---|---|
| 限流 | framework-rate-limiter | `@RateLimit` |
| 熔断降级 | framework-retry | `@CircuitBreaker(fallback="...")` |
| 多级缓存 | framework-cache | Caffeine + Redis |
| MQ削峰 | framework-mq | `mqProducer.send()` |
| 重试自愈 | framework-retry | `@Retry(strategy=EXPONENTIAL)` |

---

## 场景八：RPC框架设计

### 六大核心模块

```
调用方                                         服务方
  │                                              │
  ├─ 1.动态代理(拦截方法调用)                      │
  ├─ 2.序列化(对象→字节, Protobuf/Hessian) ──────→│
  ├─ 3.网络通信(Netty NIO) ─────────────────────→│
  ├─ 4.服务发现(注册中心 Nacos/ZK) ──────────────→│
  ├─ 5.负载均衡(轮询/随机/一致性哈希) ────────────→│
  │                                              ├─ 6.反序列化
  │                                              ├─ 7.反射调用
  │                                              ├─ 8.返回结果
  │←─────────────── 原路返回 ────────────────────│
```

### 关键设计

| 模块 | 选型 | 要点 |
|---|---|---|
| 动态代理 | JDK/CGLIB | 拦截方法调用，封装请求信息 |
| 序列化 | Protobuf/Hessian/Kryo | 二进制高效，解决TCP粘包 |
| 网络通信 | Netty NIO | 高并发低线程，Reactor模式 |
| 注册发现 | Nacos/Zookeeper | 客户端本地缓存地址列表 |
| 负载均衡 | 轮询/随机/一致性哈希 | Failover重试/Failfast快速失败 |
| SPI扩展 | 仿Dubbo SPI | 插件化，序列化/负载均衡可替换 |

---

## 场景九：微服务拆分

### 拆分策略

```
单体应用
    │
    ├─ 梳理模块依赖，按业务领域划分边界(DDD)
    │
    ├─ 绞杀者模式：新功能用微服务，老功能逐步迁移
    │
    ├─ 优先拆：业务独立、变更频繁、需单独扩展的模块
    │
    ├─ 数据库：先逻辑拆(不同schema) → 再物理拆(不同实例)
    │
    ├─ 公共代码抽SDK或基础服务
    │
    └─ 小步快跑 + 充分测试 + 灰度发布 + 回滚预案
```

### 核心组件

| 组件 | 选型 | 职责 |
|---|---|---|
| 注册中心 | Nacos | 服务注册与发现 |
| 通信 | OpenFeign / Dubbo | 服务间调用 |
| 熔断降级 | Resilience4j | 故障隔离 |
| 链路追踪 | SkyWalking | TraceID串联全链路 |
| 配置中心 | Nacos | 动态配置下发 |
| API网关 | Spring Cloud Gateway | 路由+鉴权+限流 |
| 消息队列 | RabbitMQ/Kafka | 异步解耦 |

---

## 场景十：API超时排查

### 排查流程

```
1. 确认范围
   ├─ 偶发 or 持续？
   ├─ 所有客户 or 个别客户？
   └─ 请求ID / 时间点？

2. 看监控大盘
   ├─ 响应时间（RT）趋势
   ├─ QPS 是否突增
   ├─ 错误率
   └─ CPU / 内存 / 网络IO

3. 查日志 + 链路追踪
   ├─ traceId 串联全链路（framework-web TraceIdFilter）
   ├─ SkyWalking 看各环节耗时
   └─ 定位慢在哪个服务/哪一步

4. 数据库排查
   ├─ 慢查询日志
   ├─ 索引是否生效
   ├─ 连接池是否耗尽
   └─ 锁等待（SHOW ENGINE INNODB STATUS）

5. 下游依赖
   ├─ 其他服务是否故障
   ├─ Redis/MQ 是否异常
   └─ 第三方接口是否变慢

6. 网络层面
   ├─ 丢包率
   ├─ DNS解析慢
   └─ 跨地域延迟

7. 代码变更
   ├─ 最近发布记录
   └─ 是否引入慢SQL/死循环

8. JVM/容器
   ├─ Full GC（jstat -gcutil）
   ├─ 线程死锁（jstack）
   └─ 容器CPU限制
```

### 脚手架对应

| 能力 | 模块 | 说明 |
|---|---|---|
| 链路追踪 | framework-web | `TraceIdFilter` 注入 traceId |
| 操作日志 | framework-log | `@OperationLog` 记录耗时 |
| 限流保护 | framework-rate-limiter | 防止超时引发雪崩 |
| 熔断降级 | framework-retry | 超时自动熔断 |

---

## 场景十一：Redis承压不足

### 读写分离

```
写 → Master ──复制──→ Slave1
                    ──复制──→ Slave2
                    ──复制──→ Slave3

读 ← Slave1 / Slave2 / Slave3 (轮询)
```

### Cluster 集群

```
16384 个槽位，分布到多个节点

写入: CRC16(key) % 16384 → 定位到某个节点
读取: 同样的 hash 定位

优点: 水平扩展、自动故障转移
缺点: 不支持跨槽位事务、多key操作受限
```

### 脚手架对应

| 能力 | 模块 |
|---|---|
| Redis封装 | framework-cache |
| 分布式锁 | framework-lock (Redisson Cluster 兼容) |
| 限流 | framework-rate-limiter (Redisson 分布式) |

---

## 场景十二：负载均衡

| 方案 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| 硬件(F5) | 性能强 | 成本百万级 | 超大型 |
| 软件(Nginx) | 灵活低成本 | 性能不及硬件 | 通用 |
| DNS | 简单 | 无法感知实时状态 | 地域分流 |
| CDN | 减少延迟 | 仅静态内容 | 静态资源 |

### Nginx 负载均衡算法

| 算法 | 配置 | 说明 |
|---|---|---|
| 轮询(默认) | `upstream { server a; server b; }` | 依次轮转 |
| 加权轮询 | `server a weight=3; server b weight=1;` | 按权重分配 |
| IP哈希 | `ip_hash;` | 同IP固定到同一后端 |
| 最少连接 | `least_conn;` | 分配给连接数最少的 |
| 一致性哈希 | `hash $request_uri consistent;` | 减少节点增删时的迁移 |

---

## 场景速查表

| 场景 | 核心方案 | 脚手架模块 |
|---|---|---|
| 秒杀 | 四层防线 + Lua原子扣减 + MQ削峰 | lock + idempotent + mq + rate-limiter |
| 短链 | Snowflake + Base62 + 302重定向 + 布隆过滤器 | tools + cache |
| 点赞 | Redis Set + MQ异步落库 + 本地缓存合并 | cache + mq |
| 订单超时 | MQ延迟队列 + 幂等消费 | mq |
| 分布式ID | 雪花算法 / 号段模式 | tools |
| 分布式锁 | Redisson + 看门狗 + 可重入 | lock |
| QPS突增 | 限流+降级+扩容+多级缓存+MQ削峰 | rate-limiter + retry + cache + mq |
| RPC框架 | 代理+序列化+Netty+注册发现+负载均衡 | - |
| 微服务拆分 | DDD+绞杀者+灰度+注册中心+网关 | - |
| API超时 | traceId链路+监控+慢SQL+GC排查 | web + log |
| Redis承压 | 读写分离+Cluster分片 | cache + lock |
| 负载均衡 | Nginx加权+IP哈希+一致性哈希 | - |
