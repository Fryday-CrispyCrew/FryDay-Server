package basakan.fryday.controller.notice;

import basakan.fryday.controller.notice.response.NoticeResponse;
import basakan.fryday.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ResponseEntity<List<NoticeResponse>> getNotices() {
        List<NoticeResponse> responses = noticeService.getNotices();
        return ResponseEntity.ok(responses);
    }
}
