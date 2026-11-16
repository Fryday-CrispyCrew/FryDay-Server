package basakan.fryday.controller.admin.response;

import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ExistingRetentionResponse {

    private final LocalDate day0;
    // 이 기간에 하루라도 앱을 쓴 사람이 기존 사용자다
    private final LocalDate activeFrom;
    private final LocalDate activeTo;
    private final int size;
    private final List<RetentionDay> retention;
}
