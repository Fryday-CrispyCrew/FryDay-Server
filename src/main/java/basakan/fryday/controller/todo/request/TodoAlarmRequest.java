package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TodoAlarmRequest(
        @NotNull(message = "알림 시간은 필수입니다.")
        LocalDateTime notifyAt
) {
}
