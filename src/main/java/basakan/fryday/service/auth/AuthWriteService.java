package basakan.fryday.service.auth;

import basakan.fryday.repository.auth.client.SocialUserInfo;
import basakan.fryday.domain.auth.SocialAccount;
import basakan.fryday.domain.auth.User;
import basakan.fryday.repository.auth.SocialAccountJpaRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthWriteService {

    private final UserJpaRepository userRepository;
    private final SocialAccountJpaRepository socialAccountRepository;

    public User createUserWithSocialAccount(SocialUserInfo socialUserInfo) {
        User newUser = User.createNewUser();
        User savedUser = userRepository.save(newUser);

        SocialAccount socialAccount = SocialAccount.create(
                socialUserInfo.provider(),
                socialUserInfo.providerUserId(),
                savedUser
        );
        socialAccountRepository.save(socialAccount);

        return savedUser;
    }
}
