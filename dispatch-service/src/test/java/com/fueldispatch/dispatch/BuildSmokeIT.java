package com.fueldispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Proves that Failsafe runs {@code *IT} classes during {@code verify} (FND-1.4). */
class BuildSmokeIT {

    @Test
    void integrationTests_whenVerifyRuns_executeOnJava21() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }
}
