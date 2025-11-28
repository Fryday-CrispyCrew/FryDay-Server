package basakan.fryday.service.auth;

import basakan.fryday.repository.auth.client.SocialProviderClient;
import basakan.fryday.repository.auth.client.SocialProviderClientFactory;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.common.exception.auth.UserBlockedException;
import basakan.fryday.common.exception.auth.UserWithdrawnException;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.common.security.RefreshTokenRepository;
import basakan.fryday.domain.auth.SocialAccount;
import basakan.fryday.domain.auth.User;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import basakan.fryday.service.auth.dto.SocialLoginServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthAppService {

    private final AuthReadService authReadService;
    private final AuthWriteService authWriteService;
    private final SocialProviderClientFactory providerClientFactory;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public SocialLoginDto socialLogin(SocialLoginServiceDto serviceDto) {
        SocialProviderClient client = providerClientFactory.getClient(serviceDto.provider());

        SocialUserInfo socialUserInfo;
        try {
            socialUserInfo = client.verifyToken(serviceDto.accessToken(), serviceDto.idToken());
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }

        User user = authReadService.findSocialAccount(
                        socialUserInfo.provider(),
                        socialUserInfo.providerUserId()
                )
                .map(SocialAccount::getUser)
                .orElseGet(() -> authWriteService.createUserWithSocialAccount(socialUserInfo));

        validateUserStatus(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = refreshTokenRepository.generateAndSave(user.getId());

        return SocialLoginDto.from(user, socialUserInfo.provider(), accessToken, refreshToken);
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == User.Status.BLOCKED) {
            throw new UserBlockedException();
        }
        if (user.getStatus() == User.Status.WITHDRAWN) {
            throw new UserWithdrawnException();
        }
    }
}
