package basakan.fryday.service.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
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

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_delete_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@DisplayName("그룹 해체 통합")
class GroupDeleteIntegrationTest {

    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;

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
    @DisplayName("그룹을 해체하면 그룹·그룹원·공개 카테고리가 모두 사라진다")
    void deleteGroupRemovesEverything() {
        // given
        saveCategory("운동");
        saveCategory("공부");
        Long groupId = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID).getGroupId();
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(groupId).userId(MEMBER_ID).build());

        assertThat(groupPublicCategoryRepository.findAllByGroupIdAndUserId(groupId, OWNER_ID)).hasSize(2);
        assertThat(groupMemberRepository.countByGroupId(groupId)).isEqualTo(2);

        // when
        groupService.deleteGroup(groupId, OWNER_ID);

        // then
        assertThat(fryGroupRepository.findById(groupId)).isEmpty();
        assertThat(groupMemberRepository.countByGroupId(groupId)).isZero();
        assertThat(groupPublicCategoryRepository.findAllByGroupIdAndUserId(groupId, OWNER_ID)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("다른 그룹의 데이터는 해체에 영향받지 않는다")
    void deleteGroupDoesNotTouchOtherGroups() {
        // given
        saveCategory("운동");
        Long deletedGroupId = groupService.createGroup(new GroupCreateRequest("해체될 그룹"), OWNER_ID).getGroupId();
        Long survivingGroupId = groupService.createGroup(new GroupCreateRequest("남을 그룹"), OWNER_ID).getGroupId();

        // when
        groupService.deleteGroup(deletedGroupId, OWNER_ID);

        // then
        assertThat(fryGroupRepository.findById(survivingGroupId)).isPresent();
        assertThat(groupMemberRepository.countByGroupId(survivingGroupId)).isEqualTo(1);
        assertThat(groupPublicCategoryRepository.findAllByGroupIdAndUserId(survivingGroupId, OWNER_ID)).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("이름이 같은 그룹도 각각 다른 초대 코드로 만들어진다")
    void allowsDuplicateGroupNames() {
        // given
        saveCategory("운동");

        // when
        GroupCreateResponse first = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID);
        GroupCreateResponse second = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), MEMBER_ID);

        // then
        assertThat(first.getName()).isEqualTo(second.getName());
        assertThat(first.getInviteCode()).isNotEqualTo(second.getInviteCode());
        assertThat(first.getCreatedAt()).isNotNull();
    }

    private void saveCategory(String name) {
        categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(OWNER_ID).displayOrder(1L).build());
    }
}
