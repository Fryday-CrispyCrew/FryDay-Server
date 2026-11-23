package basakan.fryday.controller.admin.response;

import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class GroupRetentionResponse {

    private final List<Snapshot> snapshots;
    // 모든 Day 0 을 더한 값. 한 사람이 여러 Day 0 에 들어갈 수 있다
    private final Comparison total;

    public GroupRetentionResponse(List<Snapshot> snapshots, Comparison total) {
        this.snapshots = snapshots;
        this.total = total;
    }

    @Getter
    @Builder
    public static class Snapshot {

        private final LocalDate day0;
        private final Segment group;
        private final Segment nonGroup;
    }

    @Getter
    @Builder
    public static class Comparison {

        private final Segment group;
        private final Segment nonGroup;
    }

    @Getter
    @Builder
    public static class Segment {

        private final int size;
        // Day 0 까지 7일 중 앱을 쓴 날 수의 평균. 인원이 없으면 null
        private final Double avgActiveDaysBefore;
        private final List<RetentionDay> retention;
    }
}
