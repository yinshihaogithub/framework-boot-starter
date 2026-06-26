package com.framework.core.result;

import com.framework.core.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void resultCarriesCurrentTraceIdWhenAvailable() {
        TraceContext.putTraceId("trace-response-1");

        Result<String> result = Result.success("ok");

        assertThat(result.getTraceId()).isEqualTo("trace-response-1");
    }

    @Test
    void resultDoesNotGenerateTraceIdOutsideTraceContext() {
        Result<Void> result = Result.fail("failed");

        assertThat(result.getTraceId()).isNull();
        assertThat(TraceContext.getTraceId()).isNull();
    }
}
