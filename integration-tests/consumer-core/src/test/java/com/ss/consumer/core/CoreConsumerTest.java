package com.ss.consumer.core;

import com.ss.core.domain.Result;
import com.ss.core.http.HttpStatusCodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the public core API from a BOM-managed dependency. */
class CoreConsumerTest {

    @Test
    void usesCoreResultWithoutExplicitDependencyVersion() {
        Result<String> result = Result.ok("ready");

        assertThat(result.getCode()).isEqualTo(HttpStatusCodes.OK);
        assertThat(result.getData()).isEqualTo("ready");
        assertThat(Result.isSuccess(result)).isTrue();
    }
}
