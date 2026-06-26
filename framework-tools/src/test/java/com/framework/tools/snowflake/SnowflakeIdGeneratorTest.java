package com.framework.tools.snowflake;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void rejectsWorkerIdOutsideTenBitRange() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
    }

    @Test
    void generatesIncreasingIdsAndEncodesWorkerId() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7);

        long firstId = generator.nextId();
        long secondId = generator.nextId();

        assertThat(secondId).isGreaterThan(firstId);
        assertThat((firstId >> 12) & 1023).isEqualTo(7);
    }
}
