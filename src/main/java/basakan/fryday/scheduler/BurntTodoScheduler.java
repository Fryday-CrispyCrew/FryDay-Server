package basakan.fryday.scheduler;

import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 투두 일일 정책 스케줄러
 * - 매일 자정(00:00)에 실행
 * - 어제 날짜의 미완료 투두를 "탄 튀김" 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BurntTodoScheduler {

    private final TodoRepository todoRepository;

    /**
     * 매일 자정 실행 (서울 시간 기준)
     * Cron 표현식: "초 분 시 일 월 요일"
     * "0 0 0 * * *" = 매일 00시 00분 00초
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void processBurntTodos() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Burnt Todo Processing start: {}", yesterday);

        // 어제 날짜의 미완료 투두를 탄 튀김 상태로 일괄 업데이트 (벌크 UPDATE)
        int burntCount = todoRepository.updateBurntStatusByDate(yesterday, Todo.Status.IN_PROGRESS);

        log.info("Burnt Todo Processing end: totalBurnt={}", burntCount);
    }
}
