package basakan.fryday.service.user;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.exception.auth.UserReregisterNotAllowedException;
import basakan.fryday.domain.user.Agreement;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.DailyResultRepository;
import basakan.fryday.repository.auth.AgreementJpaRepository;
import basakan.fryday.repository.auth.UserDeviceRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.common.security.RefreshTokenRepository;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import basakan.fryday.repository.report.MonthlyReportRepository;
import basakan.fryday.repository.todo.RecurrenceExceptionRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserWriteService {

    private final UserJpaRepository userJpaRepository;
    private final AgreementJpaRepository agreementRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final TodoAlarmRepository todoAlarmRepository;
    private final RecurrenceExceptionRepository recurrenceExceptionRepository;
    private final TodoRepository todoRepository;
    private final RecurrenceRepository recurrenceRepository;
    private final CategoryRepository categoryRepository;
    private final MonthlyReportRepository monthlyReportRepository;
    private final DailyResultRepository dailyResultRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public void agreeConsent(User user, Agreement agreement, boolean privacyAgreed) {
        agreement.updateConsent(privacyAgreed, false);
        agreementRepository.save(agreement);
        user.completeAgreementStep();
        userJpaRepository.save(user);
    }

    public void agreeMarketing(User user, Agreement agreement, boolean marketingAgreed) {
        agreement.updateMarketingAgreement(marketingAgreed);
        agreementRepository.save(agreement);
        user.completeMarketingStep();
        userJpaRepository.save(user);
    }

    public void completeOnboarding(User user) {
        user.completeOnboardingStep();
        userJpaRepository.save(user);
    }

    public void setNickname(User user, String nickname) {
        validateNicknameLength(nickname);
        validateNicknameDuplicate(nickname);

        user.setNickname(nickname);
        userJpaRepository.save(user);
    }

    public void updateNickname(User user, String nickname) {
        validateNicknameLength(nickname);

        if (!user.getNickname().equals(nickname)) {
            validateNicknameDuplicate(nickname);
        }

        user.updateNickname(nickname);
        userJpaRepository.save(user);
    }

    public void withdraw(User user) {
        Long userId = user.getId();

        // Redis refresh token 삭제
        refreshTokenRepository.deleteAllByUserId(userId);

        // 연관 데이터 삭제 (FK 순서 고려)
        todoAlarmRepository.deleteAllByUserId(userId);
        recurrenceExceptionRepository.deleteAllByUserId(userId);
        todoRepository.deleteAllByUserId(userId);
        recurrenceRepository.deleteAllByUserId(userId);
        categoryRepository.deleteAllByUserId(userId);
        monthlyReportRepository.deleteAllByUserId(userId);
        dailyResultRepository.deleteAllByUserId(userId);
        userDeviceRepository.deleteAllByUserId(userId);
        agreementRepository.deleteByUser(user);

        // 계정 상태 변경 (7일 후 스케줄러가 User 삭제)
        user.withdraw();
        userJpaRepository.save(user);
    }


    private void validateNicknameLength(String nickname) {
        if (nickname == null || nickname.length() < 2 || nickname.length() > 10) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME_LENGTH);
        }
    }

    private void validateNicknameDuplicate(String nickname) {
        if (userJpaRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    public User createUser(SocialUserInfo socialUserInfo) {
        User newUser = User.createNewUser(
                socialUserInfo.provider(),
                socialUserInfo.providerUserId()
        );
        return userJpaRepository.save(newUser);
    }

    public User handleWithdrawnUserReregister(User withdrawnUser, SocialUserInfo socialUserInfo) {
        // 재가입 가능 여부 확인 (7일 경과 여부)
        if (!withdrawnUser.canReregister()) {
            throw new UserReregisterNotAllowedException();
        }

        // 7일이 지났으므로 기존 계정 삭제 후 신규 생성
        userJpaRepository.delete(withdrawnUser);
        userJpaRepository.flush(); // 즉시 삭제 반영

        return createUser(socialUserInfo);
    }
}
