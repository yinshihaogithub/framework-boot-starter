package com.framework.demo.controller;

import com.framework.core.result.Result;
import com.framework.idempotent.annotation.Idempotent;
import com.framework.lock.annotation.DistributedLock;
import com.framework.log.annotation.OperationLog;
import com.framework.mq.core.MessageWrapper;
import com.framework.mq.producer.MqProducer;
import com.framework.ratelimiter.annotation.RateLimit;
import com.framework.retry.annotation.Retry;
import com.framework.security.annotation.RequireLogin;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 示例 Controller —— 演示脚手架全部能力
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@Tag(name = "示例接口", description = "演示脚手架全部能力")
public class DemoController {

    @Autowired(required = false)
    private MqProducer mqProducer;

    /**
     * 公开接口（白名单）
     */
    @Operation(summary = "公开接口")
    @GetMapping("/public")
    public Result<String> publicApi() {
        return Result.success("这是公开接口，无需登录");
    }

    /**
     * 需要登录
     */
    @Operation(summary = "需要登录的接口")
    @RequireLogin
    @GetMapping("/authed")
    public Result<String> authedApi() {
        return Result.success("登录成功，可以访问");
    }

    /**
     * 需要特定权限
     */
    @Operation(summary = "需要 user:add 权限")
    @RequirePermission("user:add")
    @PostMapping("/create")
    public Result<String> create(@RequestBody Map<String, Object> body) {
        return Result.success("创建成功：" + body.get("name"));
    }

    /**
     * 分布式锁
     */
    @Operation(summary = "分布式锁示例")
    @DistributedLock(key = "demo:#{#id}", waitTime = 3, message = "正在处理中，请稍候")
    @PutMapping("/lock/{id}")
    public Result<String> withLock(@PathVariable Long id) {
        log.info("[分布式锁] 获取锁成功，处理业务 id={}", id);
        try {
            Thread.sleep(2000); // 模拟业务耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Result.success("分布式锁处理完成 id=" + id);
    }

    /**
     * 幂等性
     */
    @Operation(summary = "幂等性示例")
    @Idempotent(key = "demo:#{#body.get('orderNo')}", expire = 10, message = "请勿重复提交订单")
    @PostMapping("/order")
    public Result<String> createOrder(@RequestBody Map<String, Object> body) {
        return Result.success("订单创建成功：" + body.get("orderNo"));
    }

    /**
     * 限流
     */
    @Operation(summary = "限流示例（每分钟10次）")
    @RateLimit(limit = 10, window = 60, limitType = RateLimit.LimitType.IP)
    @GetMapping("/rate-limit")
    public Result<String> rateLimited() {
        return Result.success("请求成功");
    }

    /**
     * 操作日志
     */
    @Operation(summary = "操作日志示例")
    @OperationLog(module = "示例模块", action = "执行业务操作", type = OperationLog.LogType.UPDATE)
    @PostMapping("/log")
    public Result<String> withLog(@RequestBody Map<String, Object> body) {
        return Result.success("操作完成：" + body.get("name"));
    }

    /**
     * 全部能力组合
     */
    @Operation(summary = "全部能力组合示例")
    @RequireLogin
    @RequirePermission("demo:all")
    @DistributedLock(key = "demo:combo:#{#body.get('id')}")
    @Idempotent(expire = 5)
    @RateLimit(limit = 20, window = 60)
    @OperationLog(module = "示例模块", action = "组合操作", saveResult = true)
    @PostMapping("/combo")
    public Result<String> combo(@RequestBody Map<String, Object> body) {
        return Result.success("组合操作完成：" + body.get("id"));
    }

    /**
     * 消息队列发送示例
     */
    @Operation(summary = "MQ消息发送示例")
    @PostMapping("/mq/send")
    public Result<String> sendMq(@RequestBody Map<String, Object> body) {
        if (mqProducer == null) {
            return Result.fail("MQ未启用");
        }
        String orderNo = (String) body.get("orderNo");
        mqProducer.send("demo.exchange", "demo.routing.key", orderNo, body);
        return Result.success("消息已发送: " + orderNo);
    }

    /**
     * 延迟消息示例
     */
    @Operation(summary = "MQ延迟消息示例")
    @PostMapping("/mq/delay")
    public Result<String> sendDelayMq(@RequestBody Map<String, Object> body) {
        if (mqProducer == null) {
            return Result.fail("MQ未启用");
        }
        long delayMs = Long.parseLong(body.getOrDefault("delayMs", "5000").toString());
        mqProducer.sendWithDelay("demo.delay.exchange", "demo.delay.key", body, delayMs);
        return Result.success("延迟消息已发送，延迟 " + delayMs + "ms");
    }

    /**
     * 重试示例（指数退避）
     */
    @Operation(summary = "重试示例（指数退避，最多3次）")
    @Retry(maxAttempts = 3, strategy = Retry.RetryStrategy.EXPONENTIAL,
            initialInterval = 1000, multiplier = 2.0,
            fallback = "retryFallback")
    @GetMapping("/retry")
    public Result<String> withRetry() {
        log.info("[重试示例] 执行业务方法，模拟失败...");
        throw new RuntimeException("模拟业务异常");
    }

    /**
     * 重试回调方法
     */
    public Result<String> retryFallback() {
        return Result.fail("重试耗尽，返回降级结果");
    }

    /**
     * 熔断降级示例
     */
    @Operation(summary = "熔断降级示例")
    @com.framework.retry.annotation.CircuitBreaker(
            name = "demo-service", failureRate = 0.5, timeout = 1000,
            fallback = "circuitBreakerFallback")
    @GetMapping("/circuit-breaker")
    public Result<String> withCircuitBreaker() {
        log.info("[熔断示例] 执行业务方法");
        throw new RuntimeException("模拟服务异常");
    }

    /**
     * 熔断降级回调方法
     */
    public Result<String> circuitBreakerFallback() {
        return Result.fail("服务熔断中，返回降级结果");
    }
}
