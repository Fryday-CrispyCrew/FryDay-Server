package basakan.fryday.controller.group;

import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.service.group.GroupSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupEventStreamController {

    private final GroupSseService groupSseService;

    // nginx는 기본으로 응답을 버퍼에 모았다가 내려보내서 이벤트가 늦게 도착한다. 이 응답만 버퍼링을 끈다.
    @GetMapping(value = "/{groupId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamGroupEvents(@PathVariable Long groupId,
                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .body(groupSseService.connect(groupId, userId));
    }

    // SSE 클라이언트는 Accept: text/event-stream만 보내므로 전역 핸들러의 JSON 에러 응답을 쓸 수 없다.
    // 그대로 두면 404가 500으로 바뀌어 나간다. 여기서는 본문 없이 상태 코드만 내려준다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Void> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus()).build();
    }
}
