package basakan.fryday.service;

import basakan.fryday.controller.notice.response.NoticeResponse;
import basakan.fryday.repository.notice.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public List<NoticeResponse> getNotices() {
        return noticeRepository.findAllByOrderByNoticeDateDesc()
                .stream()
                .map(NoticeResponse::from)
                .toList();
    }
}
