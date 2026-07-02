# 部署运行手册

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8+

## 初始化 MySQL

```sql
CREATE DATABASE IF NOT EXISTS framework_admin
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -uroot -proot framework_admin < sql/mysql/framework_boot_starter_init.sql
```

默认连接配置位于 `admin-service/src/main/resources/application.yml`：

- `jdbc:mysql://localhost:3306/framework_admin`
- 用户名：`root`
- 密码：`root`

如本地 MySQL 账号不同，优先修改本地运行参数或本地配置，不提交个人密码。

## Compose 一键启动

仓库已经提供 `docker-compose.yml` 和 `.env.example`。第一版推荐用它启动 MySQL、Redis、后端和前端：

```bash
cp .env.example .env
./scripts/start-compose.sh
```

默认端口：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8081`
- MySQL：`localhost:3306`
- Redis：`localhost:6379`

停止：

```bash
./scripts/stop-compose.sh
```

如需本地启动 MQ 中间件样例，使用 Compose 的 `mq` profile：

```bash
./scripts/start-mq.sh
```

默认端口：

- RabbitMQ：`5672`，管理台 `15672`
- Kafka：`9092`
- RocketMQ nameserver：`9876`
- RocketMQ broker：`10909`、`10911`

## 启动后端

```bash
mvn -pl admin-service -am spring-boot:run
```

后端默认端口：

```text
http://localhost:8081
```

健康检查：

```bash
curl http://localhost:8081/actuator/health
```

期望返回：

```json
{"status":"UP"}
```

## 启动前端

```bash
npm --prefix frontend/admin-web install
npm --prefix frontend/admin-web run dev
```

前端默认端口：

```text
http://localhost:5173
```

默认登录账号：

```text
admin / Admin@123
```

## 构建与测试

后端全量测试：

```bash
mvn test
```

后端打包：

```bash
mvn package -DskipTests
```

前端生产构建：

```bash
npm --prefix frontend/admin-web run build
```

GitHub Actions 已提供基础 CI：

- 后端：`mvn -q test`
- 前端：`npm ci && npm run build`
- 交付入口：脚本语法、脚本可执行权限、Docker Compose 配置解析

## 本地烟测

后端启动后，可以执行后台核心接口烟测：

```bash
./scripts/smoke-admin.sh
```

默认配置：

- `ADMIN_BASE_URL=http://127.0.0.1:8081`
- `ADMIN_USERNAME=admin`
- `ADMIN_PASSWORD=Admin@123`

脚本会登录后检查认证、看板、会话、MQ、本地消息、日志、监控、通知、文件和 Excel 接口。

## 核心接口烟测

登录：

```bash
curl -sS -X POST http://localhost:8081/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123","deviceId":"manual-smoke"}'
```

拿到 `accessToken` 后，将请求头设置为：

```text
Authorization: Bearer <accessToken>
```

建议检查：

- `/admin/auth/me`
- `/admin/dashboard`
- `/admin/sessions?pageNum=1&pageSize=5`
- `/admin/mq/stats`
- `/admin/local-messages?pageNum=1&pageSize=5`
- `/admin/logs?pageNum=1&pageSize=5`
- `/admin/monitor/health`
- `/admin/notify/stats`
- `/admin/files?pageNum=1&pageSize=5`
- `/admin/excel/tasks?pageNum=1&pageSize=5`

## 常见问题

### 登录失败

- 确认 MySQL 已初始化 `sql/mysql/framework_boot_starter_init.sql`。
- 确认数据库名是 `framework_admin`。
- 确认后端正在监听 `8081`。
- 确认默认账号是 `admin / Admin@123`。

### 前端页面能打开但接口失败

- 确认后端端口 `8081` 正常。
- 确认登录后请求头存在 `Authorization: Bearer <token>`。
- 打开浏览器控制台查看接口返回的 `traceId`，再去后端日志中定位同一个链路。

### 表缺失

- 重新执行聚合初始化脚本。
- 若只启动部分模块，确认对应模块的 `autoCreateTable` 配置是否开启。

### MQ 功能无真实中间件

第一版后台管理重点是消息链路、失败消息、死信和人工补偿的工程骨架。接入真实 RocketMQ、Kafka、RabbitMQ 时，需要按环境补充 broker 地址、topic 和消费者配置。
