package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupNotificationSettingRequest;
import basakan.fryday.controller.group.response.GroupNotificationSettingResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.repository.CategoryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_notification_setting_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@DisplayName("그룹 알림 수신 설정")
class GroupNotificationSettingIntegrationTest {

    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long STRANGER_ID = 3L;

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;

    /** DB_CLOSE_DELAY=-1 + 롤백 없는 트랜잭션이라 테스트 간 데이터가 남는다. */
    @BeforeEach
    void clearGroupData() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("참여 직후에는 알림 수신이 켜져 있다")
    void notificationIsEnabledByDefault() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        addMember(groupId, MEMBER_ID);

        // when
        GroupNotificationSettingResponse response = groupService.getNotificationSetting(groupId, MEMBER_ID);

        // then
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("알림 수신을 끄면 다시 조회해도 꺼져 있다")
    void updatedSettingIsPersisted() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        addMember(groupId, MEMBER_ID);

        // when
        groupService.updateNotificationSetting(groupId, MEMBER_ID, new GroupNotificationSettingRequest(false));

        // then
        assertThat(groupService.getNotificationSetting(groupId, MEMBER_ID).isEnabled()).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("한 그룹의 설정은 다른 그룹과 다른 그룹원에게 영향을 주지 않는다")
    void settingIsIsolatedPerGroupAndMember() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        Long otherGroupId = createGroup("눅눅한 사람들");
        addMember(groupId, MEMBER_ID);
        addMember(otherGroupId, MEMBER_ID);

        // when
        groupService.updateNotificationSetting(groupId, MEMBER_ID, new GroupNotificationSettingRequest(false));

        // then
        assertThat(groupService.getNotificationSetting(otherGroupId, MEMBER_ID).isEnabled()).isTrue();
        assertThat(groupService.getNotificationSetting(groupId, OWNER_ID).isEnabled()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 아니면 알림 설정을 조회하거나 바꿀 수 없다")
    void strangerCannotAccessSetting() {
        // given
        Long groupId = createGroup("바삭한 사람들");

        // when & then
        assertThatThrownBy(() -> groupService.getNotificationSetting(groupId, STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
        assertThatThrownBy(() -> groupService.updateNotificationSetting(
                groupId, STRANGER_ID, new GroupNotificationSettingRequest(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
    }

    private Long createGroup(String name) {
        categoryRepository.saveAndFlush(Category.builder()
                .name("운동").color(CategoryColor.OR).userId(OWNER_ID).displayOrder(1L).build());
        return groupService.createGroup(new GroupCreateRequest(name), OWNER_ID).getGroupId();
    }

    private void addMember(Long groupId, Long userId) {
        groupMemberRepository.saveAndFlush(GroupMember.builder().groupId(groupId).userId(userId).build());
    }
}
