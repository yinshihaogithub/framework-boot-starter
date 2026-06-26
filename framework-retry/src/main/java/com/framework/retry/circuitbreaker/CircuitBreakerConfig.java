package com.framework.retry.circuitbreaker;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 熔断器配置
 */
@Data
@Accessors(chain = true)
public class CircuitBreakerConfig {

    /** 失败率阈值百分比（0-100），超过触发熔断 */
    private double failureRateThreshold = 0.5;

    /** 慢调用阈值（毫秒），超过计为慢调用 */
    private long slowCallDurationThreshold = 2000L;

    /** 慢调用比例阈值（0-1），超过触发熔断 */
    private double slowCallRateThreshold = 0.8;

    /** 半开状态放行请求数 */
    private int permittedNumberOfCallsInHalfOpenState = 10;

    /** 滑动窗口大小（次数） */
    private int slidingWindowSize = 100;

    /** 滑动窗口类型：COUNT_BASED / TIME_BASED */
    private String slidingWindowType = "COUNT_BASED";

    /** 最小请求量，低于此值不计算失败率 */
    private int minimumNumberOfCalls = 10;

    /** 熔断持续时间（秒），半开后等待此时间再尝试 */
    private int waitDurationInOpenStateSeconds = 30;

    /** 自动从 OPEN → HALF_OPEN */
    private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
}
