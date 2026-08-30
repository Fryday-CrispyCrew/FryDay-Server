package basakan.fryday.controller.todo.request;

import basakan.fryday.domain.todo.RecurrenceScope;
import basakan.fryday.domain.todo.RecurrenceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class InstanceEditRequest {

    @NotNull
    private RecurrenceScope scope;

    @NotNull
    @Valid
    private Payload payload;

    @Getter
    @NoArgsConstructor
    public static class Payload {
        // content 필드
        // title/memo 는 부분 수정(partial patch) 규약을 따른다.
        //   - null(또는 필드 생략): 해당 필드를 변경하지 않음(기존 값 유지)
        //   - 빈 문자열(""): 값을 삭제. 반복 인스턴스는 빈 override 로 기록되어 마스터 값을 다시 상속하지 않음
        private String title;
        private String memo;
        private Boolean isAlarmEnabled;
        private LocalTime alarmTime;

        // rule 필드 (scope=THIS 에서는 무시됨)
        private RecurrenceType type;
        private List<String> frequencyValues;
        private LocalDate startDate;  // scope=ALL 에서만 사용
        private LocalDate endDate;
    }
}
