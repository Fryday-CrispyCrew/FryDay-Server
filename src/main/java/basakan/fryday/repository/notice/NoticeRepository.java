package basakan.fryday.repository.notice;

import basakan.fryday.domain.notice.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findAllByOrderByNoticeDateDesc();
}
