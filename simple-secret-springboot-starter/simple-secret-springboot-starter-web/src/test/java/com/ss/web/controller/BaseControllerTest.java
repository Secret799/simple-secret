package com.ss.web.controller;

import com.ss.core.domain.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 验证基础控制器的公共 API 和输入边界。 */
class BaseControllerTest {

    private final TestBaseController controller = new TestBaseController();

    @Test
    void shouldConvertPositiveRowsToSuccessResult() {
        assertThat(controller.toResultForRows(1).getCode()).isEqualTo(200);
    }

    @Test
    void shouldConvertNonPositiveRowsToFailureResult() {
        assertThat(controller.toResultForRows(0).getCode()).isEqualTo(500);
        assertThat(controller.toResultForRows(-1).getCode()).isEqualTo(500);
    }

    @Test
    void shouldConvertSuccessFlagToResult() {
        assertThat(controller.toResultForSuccess(true).getCode()).isEqualTo(200);
        assertThat(controller.toResultForSuccess(false).getCode()).isEqualTo(500);
    }

    @Test
    void shouldPrefixValidRedirectUrl() {
        assertThat(controller.redirect("/login")).isEqualTo("redirect:/login");
    }

    @Test
    void shouldRejectInvalidRedirectUrls() {
        assertThatIllegalArgumentException().isThrownBy(() -> controller.redirect(null));
        assertThatIllegalArgumentException().isThrownBy(() -> controller.redirect("   "));
        assertThatIllegalArgumentException().isThrownBy(() -> controller.redirect("/login\r"));
        assertThatIllegalArgumentException().isThrownBy(() -> controller.redirect("/login\n"));
    }

    private static final class TestBaseController extends BaseController {

        private Result<Void> toResultForRows(int rows) {
            return toResult(rows);
        }

        private Result<Void> toResultForSuccess(boolean success) {
            return toResult(success);
        }
    }
}
