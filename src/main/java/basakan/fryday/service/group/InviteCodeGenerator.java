package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.repository.group.FryGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 그룹 초대 코드 발급기. 영문 대문자 + 숫자 6자리이며, 이미 사용 중인 코드가 나오면 다시 뽑는다.
 */
@Component
@RequiredArgsConstructor
public class InviteCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int MAX_ATTEMPTS = 5;

    private final FryGroupRepository fryGroupRepository;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!fryGroupRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(FryGroup.INVITE_CODE_LENGTH);
        for (int i = 0; i < FryGroup.INVITE_CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
