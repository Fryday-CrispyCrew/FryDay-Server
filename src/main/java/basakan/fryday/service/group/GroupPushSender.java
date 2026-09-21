package basakan.fryday.service.group;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPushSender {

    private final PushService pushService;
    private final UserJpaRepository userJpaRepository;

    public void send(List<Long> recipientIds, String title, String body, Map<String, String> data) {
        if (recipientIds.isEmpty()) {
            return;
        }
        for (User user : userJpaRepository.findAllById(recipientIds)) {
            try {
                pushService.sendToUser(user, title, body, data);
            } catch (RuntimeException e) {
                log.warn("그룹 알림 발송 실패: userId={}", user.getId(), e);
            }
        }
    }
}
