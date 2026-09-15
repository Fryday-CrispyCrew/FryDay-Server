package basakan.fryday.service.user;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.security.RefreshTokenRepository;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.service.group.GroupCreator;
import basakan.fryday.service.group.GroupService;
import basakan.fryday.service.group.InviteCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 탈퇴 전체 경로가 그룹 데이터까지 정리하는지 검증한다.
 * {@code GroupService.leaveAllGroups} 를 직접 부르지 않고 {@code UserWriteService.withdraw()} 를 통째로 태워,
 * 벌크 삭제가 같은 트랜잭션의 User 갱신을 깨뜨리지 않는지도 함께 확인한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:user_withdraw_group_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, UserWriteService.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class})
@DisplayName("회원 탈퇴와 그룹 정리")
class UserWithdrawGroupIntegrationTest {

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired private UserWriteService userWriteService;
    @Autowired private GroupService groupService;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;

    private Long leavingUserId;
    private Long remainingUserId;

    @BeforeEach
    void setUp() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        leavingUserId = saveUser("leaving", "연우");
        remainingUserId = saveUser("remaining", "수정");
        saveCategory(leavingUserId, "운동");
        saveCategory(remainingUserId, "공부");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("탈퇴하면 내가 만든 그룹은 해체되고, 참여만 하던 그룹에서는 나만 빠진다")
    void withdrawDisbandsOwnedGroupsAndLeavesJoinedOnes() {
        // given
        Long ownedGroupId = groupService.createGroup(
                new GroupCreateRequest("내가 만든 그룹"), leavingUserId).getGroupId();
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(ownedGroupId).userId(remainingUserId).build());

        Long joinedGroupId = groupService.createGroup(
                new GroupCreateRequest("남이 만든 그룹"), remainingUserId).getGroupId();
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(joinedGroupId).userId(leavingUserId).build());

        // when
        userWriteService.withdraw(userJpaRepository.findById(leavingUserId).orElseThrow());

        // then — 내가 만든 그룹은 흔적 없이 사라진다 (남아 있던 다른 그룹원의 공개 설정까지)
        assertThat(fryGroupRepository.findById(ownedGroupId)).isEmpty();
        assertThat(groupMemberRepository.countByGroupId(ownedGroupId)).isZero();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(ownedGroupId, leavingUserId)).isZero();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(ownedGroupId, remainingUserId)).isZero();

        // then — 참여만 하던 그룹은 남고 나만 빠진다
        assertThat(fryGroupRepository.findById(joinedGroupId)).isPresent();
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(joinedGroupId, leavingUserId)).isFalse();
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(joinedGroupId, remainingUserId)).isTrue();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(joinedGroupId, leavingUserId)).isZero();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(joinedGroupId, remainingUserId)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹 정리 중 벌크 삭제가 있어도 탈퇴 상태 변경이 그대로 저장된다")
    void withdrawStillPersistsAccountStatus() {
        // given
        groupService.createGroup(new GroupCreateRequest("내가 만든 그룹"), leavingUserId);

        // when
        userWriteService.withdraw(userJpaRepository.findById(leavingUserId).orElseThrow());

        // then
        User withdrawn = userJpaRepository.findById(leavingUserId).orElseThrow();
        assertThat(withdrawn.getAccountStatus()).isEqualTo(User.AccountStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
    }

    private Long saveUser(String providerUserId, String nickname) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        user.setNickname(nickname);
        return userJpaRepository.saveAndFlush(user).getId();
    }

    private void saveCategory(Long userId, String name) {
        categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
    }
}
