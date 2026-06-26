# framework-log

> 操作日志模块：注解式异步记录，含模块/操作/参数/结果/耗时/IP/链路 traceId。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-log</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 自动装配生效。`OperationLogAspect` 由 `@Component` 注册，`LogAsyncConfig` 配置异步线程池。
> 如需 DB 存储，请使用 MySQL 初始化脚本 `framework-log/src/main/resources/db/mysql/sys_operation_log.sql`，或执行工程根目录聚合脚本 `sql/mysql/framework_boot_starter_init.sql`。

## 配置

配置前缀：`framework.log`。

```yaml
framework:
  log:
    enabled: true
    api-sample-rate: 0
    retention-days: 30
    db-storage:
      enabled: false
```

`framework.log.*` 已注册为 Spring Boot 配置属性，开启 configuration processor 后 IDE 会提示这些配置项。

配置启动期会快速校验：`api-sample-rate` 必须在 `0..100` 之间，`retention-days` 必须大于 0。

## 注解参数

```java
@OperationLog(
    module = "用户管理",              // 模块名（必填）
    action = "新增用户",              // 操作描述（必填）
    type = LogType.INSERT,           // 操作类型，默认 OTHER
    saveParam = true,                // 是否记录请求参数，默认 true
    saveResult = false               // 是否记录返回结果，默认 false
)
```

### LogType 枚举

| 值 | 说明 |
|---|---|
| `INSERT` | 新增 |
| `UPDATE` | 修改 |
| `DELETE` | 删除 |
| `QUERY` | 查询 |
| `EXPORT` | 导出 |
| `IMPORT` | 导入 |
| `LOGIN` | 登录 |
| `LOGOUT` | 登出 |
| `OTHER` | 其他（默认） |

## 使用示例

### 基础用法

```java
@OperationLog(module = "用户管理", action = "新增用户", type = OperationLog.LogType.INSERT)
@PostMapping("/users")
public Result<Void> create(@RequestBody UserDTO dto) {
    userService.create(dto);
    return Result.success();
}
```

### 记录返回结果

```java
@OperationLog(module = "订单管理", action = "查询订单详情",
              type = OperationLog.LogType.QUERY, saveResult = true)
@GetMapping("/orders/{id}")
public Result<Order> getOrder(@PathVariable Long id) {
    return Result.success(orderService.getById(id));
}
```

### 不记录参数（敏感操作）

```java
@OperationLog(module = "账号管理", action = "重置密码",
              saveParam = false)  // 不记录密码参数
@PutMapping("/users/{id}/password")
public Result resetPassword(@PathVariable Long id, @RequestBody PasswordDTO dto) {
    return Result.success();
}
```

## 日志输出内容

日志以 JSON 格式输出到日志文件（INFO 级别）：

```json
{
  "module": "用户管理",
  "action": "新增用户",
  "type": "INSERT",
  "method": "UserController.create",
  "elapsedMs": 35,
  "success": true,
  "params": "{\"name\":\"张三\",\"age\":20}",
  "uri": "/api/users",
  "method": "POST",
  "ip": "192.168.1.100"
}
```

失败时额外包含 `error` 字段：
```json
{
  "module": "用户管理",
  "action": "新增用户",
  "success": false,
  "error": "手机号已存在",
  ...
}
```

## 工程约束

- 操作日志异步记录必须保留当前 `traceId`，避免跨线程断链。
- 采样率和日志保留天数必须启动期快速校验，避免采样/清理策略退化为不可解释状态。
- DB 存储是可选增强；缺少 `OperationLogMapper` 时跳过持久化，建表、写入和清理失败只能记录日志，不能影响业务主流程。
- 参数和返回值入日志前必须脱敏，支持 JSON 对象、JSON 数组和嵌套对象数组。
- API query string 入日志前必须按 key 脱敏，`password`、`token`、`secret` 等完整隐藏，手机号/身份证/邮箱等部分脱敏。
- 日志记录失败不能影响业务主流程。

## 异步线程池配置

| 参数 | 默认值 |
|---|---|
| 核心线程 | 2 |
| 最大线程 | 8 |
| 队列容量 | 1000 |
| 线程名前缀 | `log-async-` |
| 拒绝策略 | CallerRunsPolicy（调用者线程执行） |

日志记录走 `CompletableFuture.runAsync()` 异步执行，不阻塞业务。队列满时降级为同步执行。

## IP 获取

按优先级获取真实 IP：
1. `X-Forwarded-For` Header（取第一个）
2. `X-Real-IP` Header
3. `request.getRemoteAddr()`
