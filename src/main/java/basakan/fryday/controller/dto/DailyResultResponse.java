package basakan.fryday.controller.todo.response;

import basakan.fryday.domain.DailyResult;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class DailyResultResponse {

    private final LocalDate date;
    private final String bowlType;

    public DailyResultResponse(DailyResult dailyResult) {
        this.date = dailyResult.getDate();
        this.bowlType = dailyResult.getBowlType().getCode();
    }

    public static DailyResultResponse from(DailyResult dailyResult) {
        return new DailyResultResponse(dailyResult);
    }
}
