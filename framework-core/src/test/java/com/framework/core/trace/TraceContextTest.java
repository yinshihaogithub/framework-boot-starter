package com.framework.core.trace;

import com.framework.core.constant.FrameworkConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getOrCreateTraceIdStoresIncomingTraceIdInMdc() {
        String traceId = TraceContext.getOrCreateTraceId("trace-from-gateway");

        assertThat(traceId).isEqualTo("trace-from-gateway");
        assertThat(MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY)).isEqualTo("trace-from-gateway");
    }

    @Test
    void getOrCreateTraceIdGeneratesTraceIdWhenMissing() {
        String traceId = TraceContext.getOrCreateTraceId(" ");

        assertThat(traceId).isNotBlank();
        assertThat(MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY)).isEqualTo(traceId);
    }

    @Test
    void wrapPropagatesCapturedMdcAndRestoresPreviousContext() {
        MDC.put(FrameworkConstants.TRACE_ID_MDC_KEY, "parent-trace");
        Runnable wrapped = TraceContext.wrap(() -> {
            assertThat(MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY)).isEqualTo("parent-trace");
            MDC.put(FrameworkConstants.TRACE_ID_MDC_KEY, "child-trace");
        });

        MDC.put(FrameworkConstants.TRACE_ID_MDC_KEY, "caller-trace");
        wrapped.run();

        assertThat(MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY)).isEqualTo("caller-trace");
    }

    @Test
    void taskDecoratorPropagatesTraceContext() {
        MDC.put(FrameworkConstants.TRACE_ID_MDC_KEY, "decorated-trace");
        TraceContextTaskDecorator decorator = new TraceContextTaskDecorator();
        AtomicReference<String> observed = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() ->
                observed.set(MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY)));

        MDC.clear();
        decorated.run();

        assertThat(observed).hasValue("decorated-trace");
        assertThat(MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY)).isNull();
    }
}
