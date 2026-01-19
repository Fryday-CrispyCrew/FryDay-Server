package basakan.fryday.service.user;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.common.exception.auth.UserBlockedException;
import basakan.fryday.common.exception.auth.UserWithdrawnException;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.common.security.RefreshTokenRepository;
import basakan.fryday.common.security.UserContext;
import basakan.fryday.controller.user.response.NicknameCheckResponse;
import basakan.fryday.controller.user.response.RefreshTokenResponse;
import basakan.fryday.domain.user.Agreement;
import basakan.fryday.domain.user.User;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.client.SocialProviderClient;
import basakan.fryday.repository.auth.client.SocialProviderClientFactory;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import basakan.fryday.service.auth.dto.SocialLoginServiceDto;
import basakan.fryday.service.CategoryService;
import basakan.fryday.service.fcm.UserDeviceReadService;
import basakan.fryday.service.fcm.UserDeviceWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserAppService {

    private final UserReadService userReadService;
    private final UserWriteService userWriteService;
    private final SocialProviderClientFactory providerClientFactory;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDeviceReadService userDeviceReadService;
    private final UserDeviceWriteService userDeviceWriteService;
    private final CategoryService categoryService;

    public void agreeConsent(boolean privacyRequired) {
        Long userId = UserContext.getCurrentUserId();
        User user = userReadService.findById(userId);

        Agreement agreement = userReadService.findAgreementByUser(user)
                .orElseGet(() -> Agreement.create(user, privacyRequired, false, false));

        userWriteService.agreeConsent(user, agreement, privacyRequired);
    }

    public void agreeMarketing(boolean marketingOptional) {
        Long userId = UserContext.getCurrentUserId();
        User user = userReadService.findById(userId);

        Agreement agreement = userReadService.findAgreementByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGREEMENT_NOT_FOUND));

        userWriteService.agreeMarketing(user, agreement, marketingOptional);
    }

    public void completeOnboarding() {
        Long userId = UserContext.getCurrentUserId();
        User user = userReadService.findById(userId);
        userWriteService.completeOnboarding(user);

        // 온보딩 완료 시 기본 카테고리 생성
        categoryService.initDefaultCategories(userId);
    }

    public void setNickname(String nickname) {
        Long userId = UserContext.getCurrentUserId();
        User user = userReadService.findById(userId);
        userWriteService.setNickname(user, nickname);
    }

    public void withdraw() {
        Long userId = UserContext.getCurrentUserId();
        User user = userReadService.findById(userId);
        userWriteService.withdraw(user);
    }

    public void updateNickname(String nickname) {
        Long userId = UserContext.getCurrentUserId();
        User user = userReadService.findById(userId);
        userWriteService.updateNickname(user, nickname);
    }

    public void updateNotificationSettings(boolean pushNotificationEnabled) {
        Long userId = UserContext.getCurrentUserId();
        User user = userReadService.findById(userId);

        Agreement agreement = userReadService.findAgreementByUser(user)
                .orElseGet(() -> Agreement.create(user, false, pushNotificationEnabled, false));

        userWriteService.updateNotificationSettings(agreement, pushNotificationEnabled);
    }

    public NicknameCheckResponse checkNicknameAvailability(String nickname) {
        if (nickname == null || nickname.length() < 2 || nickname.length() > 10) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME_LENGTH);
        }

        if (userReadService.existsByNickname(nickname)) {
            return NicknameCheckResponse.unavailable();
        }
        return NicknameCheckResponse.available();
    }

    public SocialLoginDto socialLogin(SocialLoginServiceDto serviceDto) {
        SocialProviderClient client = providerClientFactory.getClient(serviceDto.provider());

        SocialUserInfo socialUserInfo;
        try {
            socialUserInfo = client.getUserInfo(serviceDto.accessToken(), serviceDto.idToken());
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
            log.warn("Invalid provider token: provider={}, status={}", serviceDto.provider(), e.getStatusCode());
            throw new InvalidProviderTokenException();
        } catch (WebClientResponseException e) {
            log.error("Provider API error: provider={}, status={}, body={}",
                    serviceDto.provider(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.INTERNAL_AUTH_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error during social login: provider={}", serviceDto.provider(), e);
            throw new BusinessException(ErrorCode.INTERNAL_AUTH_ERROR);
        }

        User user;

        // 1. 탈퇴한 사용자가 있는지 확인 (재가입 처리)
        Optional<User> withdrawnUser = userReadService.findWithdrawnUserByProviderAndProviderUserId(
                socialUserInfo.provider(), socialUserInfo.providerUserId());

        if (withdrawnUser.isPresent()) {
            // 탈퇴한 사용자 재가입 처리 (7일 체크 후 기존 계정 삭제 + 신규 생성)
            user = userWriteService.handleWithdrawnUserReregister(withdrawnUser.get(), socialUserInfo);
        } else {
            // 2. ACTIVE 상태인 사용자 확인 (기존 로그인) 또는 신규 생성
            user = userReadService.findActiveUserByProviderAndProviderUserId(
                            socialUserInfo.provider(),
                            socialUserInfo.providerUserId()
                    ).orElseGet(() -> userWriteService.createUser(socialUserInfo));

            validateUserStatus(user);
        }

        userDeviceReadService.findByUserIdAndDeviceId(user.getId(), serviceDto.deviceId())
                .ifPresentOrElse(
                        existingDevice -> {
                            userDeviceWriteService.activateDevice(existingDevice);
                            if (serviceDto.fcmToken() != null) {
                                userDeviceWriteService.updateFcmToken(existingDevice, serviceDto.fcmToken());
                            }
                        },
                        () -> userDeviceWriteService.createOrActivateDevice(
                                user.getId(),
                                serviceDto.deviceId(),
                                serviceDto.deviceType(),
                                serviceDto.deviceName(),
                                serviceDto.fcmToken()
                        )
                );

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole(), user.getAccountStatus());
        String refreshToken = refreshTokenRepository.generateAndSave(user.getId(), serviceDto.deviceId());

        return SocialLoginDto.from(user, socialUserInfo.provider(), accessToken, refreshToken, serviceDto.deviceId());
    }

    public void updateDeviceFcmToken(String deviceId, String fcmToken) {
        Long userId = UserContext.getCurrentUserId();

        UserDevice device = userDeviceReadService.findByDeviceId(deviceId);

        // 디바이스 소유자 검증
        if (!device.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
        }

        userDeviceWriteService.updateFcmToken(device, fcmToken);
    }

    public void logout(String deviceId, String refreshToken) {
        Long userId = UserContext.getCurrentUserId();

        refreshTokenRepository.delete(refreshToken);

        UserDevice device = userDeviceReadService.findByDeviceId(deviceId);
        if (device.getUserId().equals(userId)) {
            userDeviceWriteService.deactivateDevice(device);
        }
    }

    public RefreshTokenResponse refreshAccessToken(String oldRefreshToken) {
        Long userId = refreshTokenRepository.getUserId(oldRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        String deviceId = refreshTokenRepository.getDeviceId(oldRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        User user = userReadService.findById(userId);
        validateUserStatus(user);

        refreshTokenRepository.delete(oldRefreshToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole(), user.getAccountStatus());
        String newRefreshToken = refreshTokenRepository.generateAndSave(user.getId(), deviceId);

        return RefreshTokenResponse.of(newAccessToken, newRefreshToken);
    }

    private void validateUserStatus(User user) {
        if (user.getAccountStatus() == User.AccountStatus.BLOCKED) {
            throw new UserBlockedException();
        }
        if (user.getAccountStatus() == User.AccountStatus.WITHDRAWN) {
            throw new UserWithdrawnException();
        }
    }
}
