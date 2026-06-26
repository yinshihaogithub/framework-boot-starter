# demo

> 示例启动模块，演示 framework-boot-starter 全部能力。

## 启动

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.13+ / Kafka / RocketMQ（三选一或多选，demo 默认 RabbitMQ）

### 2. 启动 MySQL 和 Redis

```bash
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=framework_demo mysql:8.0
docker run -d --name redis -p 6379:6379 redis:7
```

Demo 默认使用 MySQL。`application.yml` 已配置 `spring.sql.init`，从工程根目录或 `demo` 目录启动时会自动尝试执行根目录聚合脚本 `sql/mysql/framework_boot_starter_init.sql` 初始化框架表。

如果以打包产物或其他工作目录启动，可手动执行：

```bash
mysql -uroot -proot framework_demo < ../sql/mysql/framework_boot_starter_init.sql
```

### 3. 启动 RabbitMQ（可选，demo 默认 MQ provider）

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

### 4. 编译

```bash
cd framework-boot-starter
mvn clean install -DskipTests
```

### 5. 启动 Demo

```bash
cd demo
mvn spring-boot:run
```

### 6. 访问

- Swagger UI：http://localhost:8080/swagger-ui.html

## 示例接口

| 接口 | 方法 | 路径 | 演示能力 |
|---|---|---|---|
| 公开接口 | GET | `/api/demo/public` | 白名单放行 |
| 需要登录 | GET | `/api/demo/authed` | `@RequireLogin` |
| 权限校验 | POST | `/api/demo/create` | `@RequirePermission("user:add")` |
| 分布式锁 | PUT | `/api/demo/lock/{id}` | `@DistributedLock` |
| 幂等防重 | POST | `/api/demo/order` | `@Idempotent` |
| 限流 | GET | `/api/demo/rate-limit` | `@RateLimit`（每分钟10次/IP） |
| 操作日志 | POST | `/api/demo/log` | `@OperationLog` |
| MQ 发送 | POST | `/api/demo/mq/send` | `MqProducer.send` |
| 延迟消息 | POST | `/api/demo/mq/delay` | `MqProducer.sendWithDelay` |
| 重试示例 | GET | `/api/demo/retry` | `@Retry`（指数退避，3次，含 fallback） |
| 熔断降级 | GET | `/api/demo/circuit-breaker` | `@CircuitBreaker`（含 fallback） |
| 全能力组合 | POST | `/api/demo/combo` | 登录+权限+锁+幂等+限流+日志 |

## 测试示例

### 公开接口

```bash
curl http://localhost:8080/api/demo/public
# {"code":200,"message":"success","data":"这是公开接口，无需登录","timestamp":...}
```

### 限流测试

```bash
# 连续请求 11 次，第 11 次会被限流
for i in $(seq 1 11); do
  curl http://localhost:8080/api/demo/rate-limit
done
# 第 11 次: {"code":3001,"message":"请求过于频繁，请稍后再试","data":null}
```

### 幂等测试

```bash
# 10 秒内相同 orderNo 只能提交一次
curl -X POST http://localhost:8080/api/demo/order \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORD001"}'
# 第一次: {"code":200,"message":"success","data":"订单创建成功：ORD001"}

curl -X POST http://localhost:8080/api/demo/order \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORD001"}'
# 第二次: {"code":3003,"message":"请勿重复提交订单","data":null}
```

### 分布式锁测试

```bash
curl -X PUT http://localhost:8080/api/demo/lock/1
# {"code":200,"message":"success","data":"分布式锁处理完成 id=1"}
```

### 重试测试

```bash
curl http://localhost:8080/api/demo/retry
# 观察日志：3次重试（1s→2s→4s），最终返回 fallback
# {"code":500,"message":"重试耗尽，返回降级结果","data":null}
```

## 配置文件

参见 `src/main/resources/application.yml`，包含 MySQL、MySQL 初始化脚本、Redis、RabbitMQ、framework 鉴权等配置。`framework.mq.provider` 可切换为 `RABBIT` / `KAFKA` / `ROCKET`。
