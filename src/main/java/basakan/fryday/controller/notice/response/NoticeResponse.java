package basakan.fryday.controller.notice.response;

import basakan.fryday.domain.notice.Notice;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class NoticeResponse {

    private Long id;
    private String content;
    private LocalDate noticeDate;

    public static NoticeResponse from(Notice notice) {
        return NoticeResponse.builder()
                .id(notice.getId())
                .content(notice.getContent())
                .noticeDate(notice.getNoticeDate())
                .build();
    }
}
