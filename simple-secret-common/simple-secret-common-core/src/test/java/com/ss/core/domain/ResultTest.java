package com.ss.core.domain;

import com.ss.core.http.HttpStatusCodes;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证通用响应对象的公共 API 和边界行为。 */
class ResultTest {

    @Test
    void shouldCreateSuccessfulResultsWithoutStringOverloadAmbiguity() {
        Result<Void> empty = Result.ok();
        Result<String> stringData = Result.ok("payload");
        Result<Void> messageOnly = Result.okMessage("completed");
        Result<Integer> messageAndData = Result.ok("completed", 7);

        assertThat(empty.getCode()).isEqualTo(HttpStatusCodes.OK);
        assertThat(empty.getMessage()).isEqualTo("操作成功");
        assertThat(empty.getData()).isNull();
        assertThat(stringData.getData()).isEqualTo("payload");
        assertThat(stringData.getMessage()).isEqualTo("操作成功");
        assertThat(messageOnly.getMessage()).isEqualTo("completed");
        assertThat(messageAndData.getMessage()).isEqualTo("completed");
        assertThat(messageAndData.getData()).isEqualTo(7);
    }

    @Test
    void shouldCreateFailureAndWarningResults() {
        Result<Void> empty = Result.fail();
        Result<String> stringData = Result.fail("payload");
        Result<Void> messageOnly = Result.failMessage("invalid");
        Result<Integer> messageAndData = Result.fail("invalid", 9);
        Result<Void> custom = Result.fail(422, "unprocessable");
        Result<String> warning = Result.warn("check", "payload");

        assertThat(empty.getCode()).isEqualTo(HttpStatusCodes.INTERNAL_SERVER_ERROR);
        assertThat(empty.getMessage()).isEqualTo("操作失败");
        assertThat(stringData.getData()).isEqualTo("payload");
        assertThat(messageOnly.getMessage()).isEqualTo("invalid");
        assertThat(messageAndData.getData()).isEqualTo(9);
        assertThat(custom.getCode()).isEqualTo(422);
        assertThat(warning.getCode()).isEqualTo(HttpStatusCodes.WARNING);
        assertThat(warning.getMessage()).isEqualTo("check");
        assertThat(warning.getData()).isEqualTo("payload");
    }

    @Test
    void shouldCheckStatusWithoutThrowingForNull() {
        assertThat(Result.isSuccess(Result.ok())).isTrue();
        assertThat(Result.isError(Result.ok())).isFalse();
        assertThat(Result.isSuccess(null)).isFalse();
        assertThat(Result.isError(null)).isTrue();
    }

    @Test
    void shouldRemainAPlainSerializableJavaBean() {
        Result<String> result = new Result<>();
        result.setCode(201);
        result.setMessage("created");
        result.setData("id-1");

        assertThat(result).isInstanceOf(Serializable.class);
        assertThat(result.getCode()).isEqualTo(201);
        assertThat(result.getMessage()).isEqualTo("created");
        assertThat(result.getData()).isEqualTo("id-1");
    }
}
