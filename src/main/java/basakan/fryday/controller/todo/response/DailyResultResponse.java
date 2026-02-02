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
        this.bowlType = dailyResult.getBowlType() != null ? dailyResult.getBowlType().getCode() : null;
    }

    public DailyResultResponse(LocalDate date, String bowlType) {
        this.date = date;
        this.bowlType = bowlType;
    }

    public static DailyResultResponse from(DailyResult dailyResult) {
        return new DailyResultResponse(dailyResult);
    }

    public static DailyResultResponse of(LocalDate date, String bowlType) {
        return new DailyResultResponse(date, bowlType);
    }
}
