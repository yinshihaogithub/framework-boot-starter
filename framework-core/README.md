# framework-core

> 核心基础层，所有模块的根依赖。提供统一响应、异常体系、业务码、全局常量。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 核心类

| 类 | 说明 |
|---|---|
| `Result<T>` | 统一响应体，含 `code`/`message`/`data`/`timestamp`/`traceId` |
| `PageResult<T>` | 分页响应体，含 `records`/`total`/`pageNum`/`pageSize`/`pages` |
| `ResultCode` | 业务码枚举 |
| `BusinessException` | 业务异常基类（带业务码） |
| `AuthException` | 鉴权异常 |
| `PermissionException` | 权限异常 |
| `ParamException` | 参数校验异常 |
| `FrameworkConstants` | 框架通用常量（Header名、Redis前缀、默认分页） |

## 使用示例

### 统一响应

```java
// 成功
return Result.success(user);
return Result.success();

// 失败
return Result.fail("用户不存在");
return Result.fail(ResultCode.PARAM_ERROR);
return Result.fail(10001, "自定义错误");
```

### 分页响应

```java
List<User> records = userService.list(pageNum, pageSize);
long total = userService.count();
return Result.success(PageResult.of(records, total, pageNum, pageSize));

// 空结果
return Result.success(PageResult.empty(pageNum, pageSize));
```

### 抛出业务异常

```java
// 直接抛出，全局异常处理器会转为 Result
throw new BusinessException("用户不存在");
throw new BusinessException(ResultCode.PERMISSION_DENIED);
throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单状态异常");

// 鉴权/权限/参数异常
throw new AuthException("Token无效");
throw new PermissionException("缺少user:add权限");
throw new ParamException("手机号格式错误");
```

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1719235200000,
  "traceId": "6c8f6c8f6c8f4a3b9b2f6c8f6c8f6c8f"
}
```

`traceId` 从当前 MDC 读取；没有链路上下文时保持为空，不会在后台任务或单元测试里额外生成。
外部传入的 `traceId` 只接受 1-128 位字母、数字、`.`、`_`、`:`、`-`；包含空白、换行、控制字符或超长时会被丢弃并重新生成，避免污染日志、响应头和下游链路。

## 业务码规范

| 码段 | 含义 |
|---|---|
| 200 | 成功 |
| 400-499 | 客户端错误（400参数/401未登录/403无权限/404不存在/405方法不允许） |
| 500-599 | 服务端错误 |
| 1000-1999 | 通用业务错误（1001参数校验/1002业务失败/1003重复提交） |
| 2000-2999 | 鉴权权限错误（2001Token无效/2002Token过期/2003登录失败/2004账号锁定/2005权限不足） |
| 3000-3999 | 各模块自定义（3001限流/3002锁失败/3003幂等拦截） |
