package basakan.fryday.common.exception;

import basakan.fryday.common.ErrorCode;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("API 에러 지표")
class GlobalExceptionHandlerMetricsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
    }

    @Test
    @DisplayName("비즈니스 예외는 에러 코드와 상태 코드별로 센다")
    void countsBusinessErrorsByCode() {
        // given
        double before = count("business", "GROUP_NOT_FOUND");

        // when
        handler.handleBusinessException(new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        // then
        assertThat(count("business", "GROUP_NOT_FOUND")).isEqualTo(before + 1);
        assertThat(registry.get("api.errors").tag("code", "GROUP_NOT_FOUND").counter().getId().getTag("status"))
                .isEqualTo("404");
    }

    @Test
    @DisplayName("입력 검증 실패는 비즈니스 예외와 따로 센다")
    void countsValidationErrorsSeparately() {
        // given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        given(exception.getBindingResult()).willReturn(new BeanPropertyBindingResult(new Object(), "request"));
        double before = count("validation", "INVALID_INPUT_VALUE");

        // when
        handler.handleMethodArgumentNotValidException(exception);

        // then
        assertThat(count("validation", "INVALID_INPUT_VALUE")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("한 번도 나지 않은 에러 코드도 0으로 노출한다")
    void registersAllErrorCodesUpFront() {
        assertThat(registry.find("api.errors").tag("type", "business").counters()).hasSize(ErrorCode.values().length);
    }

    private double count(String type, String code) {
        return registry.get("api.errors").tag("type", type).tag("code", code).counter().count();
    }
}
