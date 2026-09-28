package basakan.fryday.service.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.controller.group.response.GroupInvitePreviewResponse;
import basakan.fryday.controller.group.response.GroupJoinResponse;
import basakan.fryday.controller.group.response.GroupSummaryResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_image_code_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class})
@DisplayName("그룹 그래픽 코드 배정")
class GroupImageCodeIntegrationTest {

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long joinerId;

    @BeforeEach
    void setUp() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUser("owner", "연우");
        joinerId = saveUser("joiner", "수정");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹을 만들면 그래픽 코드가 배정되어 저장된다")
    void createAssignsImageCode() {
        // given
        saveCategory(ownerId, "운동");

        // when
        GroupCreateResponse response = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), ownerId);

        // then
        assertThat(response.getImageCode()).isIn("01", "02", "03");
        assertThat(savedImageCode(response.getGroupId())).isEqualTo(response.getImageCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("배정된 그래픽 코드가 목록, 상세, 초대 미리보기, 참여 응답에 그대로 내려온다")
    void assignedImageCodeIsReturnedByEveryGroupResponse() {
        // given
        saveCategory(ownerId, "운동");
        Category joinerCategory = saveCategory(joinerId, "공부");

        GroupCreateResponse created = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), ownerId);
        String assigned = created.getImageCode();
        String inviteCode = inviteCodeOf(created.getGroupId());

        // when
        List<GroupSummaryResponse> myGroups = groupService.getMyGroups(ownerId).getGroups();
        GroupInvitePreviewResponse preview = groupService.previewByInviteCode(inviteCode, joinerId);
        GroupJoinResponse joined = groupService.join(
                new GroupJoinRequest(inviteCode, List.of(joinerCategory.getId())), joinerId);

        // then
        assertThat(groupService.getGroup(created.getGroupId(), ownerId).getImageCode()).isEqualTo(assigned);
        assertThat(myGroups).singleElement()
                .extracting(GroupSummaryResponse::getImageCode)
                .isEqualTo(assigned);
        assertThat(preview.getImageCode()).isEqualTo(assigned);
        assertThat(joined.getImageCode()).isEqualTo(assigned);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹을 여러 개 만들면 세 그래픽 코드가 모두 배정된다")
    void createAssignsEveryImageCodeAcrossGroups() {
        // given
        saveCategory(ownerId, "운동");

        // when
        Set<String> assigned = new HashSet<>();
        for (int count = 0; count < 100; count++) {
            assigned.add(groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), ownerId).getImageCode());
        }

        // then
        assertThat(assigned).containsExactlyInAnyOrder("01", "02", "03");
    }

    private String savedImageCode(Long groupId) {
        return fryGroupRepository.findById(groupId).orElseThrow().getImageCode();
    }

    private String inviteCodeOf(Long groupId) {
        return fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
    }

    private Long saveUser(String providerUserId, String nickname) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        user.setNickname(nickname);
        return userJpaRepository.saveAndFlush(user).getId();
    }

    private Category saveCategory(Long userId, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
    }
}
