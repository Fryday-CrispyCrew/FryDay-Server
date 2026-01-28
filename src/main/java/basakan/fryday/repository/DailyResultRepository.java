package basakan.fryday.repository;

import basakan.fryday.domain.DailyResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyResultRepository extends JpaRepository<DailyResult, Long> {
    Optional<DailyResult> findByUserIdAndDate(Long userId, LocalDate date);

    List<DailyResult> findAllByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    void deleteAllByUserId(Long userId);
}
