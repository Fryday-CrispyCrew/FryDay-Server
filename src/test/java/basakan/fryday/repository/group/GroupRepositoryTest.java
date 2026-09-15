package basakan.fryday.repository.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_repository_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import(JpaConfig.class)
@DisplayName("그룹 리포지토리")
class GroupRepositoryTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private FryGroupRepository fryGroupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private GroupPublicCategoryRepository groupPublicCategoryRepository;

    @Test
    @DisplayName("그룹을 저장하면 생성/수정 시각이 자동으로 채워진다")
    void saveGroupFillsAuditingFields() {
        // given
        FryGroup group = FryGroup.builder().name("바삭한 사람들").inviteCode("FRY123").ownerId(OWNER_ID).build();

        // when
        FryGroup saved = fryGroupRepository.saveAndFlush(group);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("동일한 초대 코드로 그룹을 두 번 저장하면 제약 위반이 발생한다")
    void duplicateInviteCodeIsRejected() {
        // given
        fryGroupRepository.saveAndFlush(
                FryGroup.builder().name("그룹A").inviteCode("FRY123").ownerId(OWNER_ID).build());

        // when & then
        assertThatThrownBy(() -> fryGroupRepository.saveAndFlush(
                FryGroup.builder().name("그룹B").inviteCode("FRY123").ownerId(OTHER_USER_ID).build()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("초대 코드 존재 여부를 확인할 수 있다")
    void existsByInviteCode() {
        // given
        fryGroupRepository.saveAndFlush(
                FryGroup.builder().name("그룹A").inviteCode("FRY123").ownerId(OWNER_ID).build());

        // when & then
        assertThat(fryGroupRepository.existsByInviteCode("FRY123")).isTrue();
        assertThat(fryGroupRepository.existsByInviteCode("ZZZ999")).isFalse();
    }

    @Test
    @DisplayName("같은 그룹에 같은 사용자를 두 번 넣으면 제약 위반이 발생한다")
    void duplicateGroupMemberIsRejected() {
        // given
        Long groupId = saveGroupWithOwner().getId();

        // when & then
        assertThatThrownBy(() -> groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(groupId).userId(OWNER_ID).build()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("그룹원은 알림 수신이 기본으로 켜진 상태로 저장된다")
    void memberNotificationIsEnabledByDefault() {
        // given
        Long groupId = saveGroupWithOwner().getId();

        // when
        GroupMember member = groupMemberRepository.findAll().stream()
                .filter(m -> m.getGroupId().equals(groupId))
                .findFirst()
                .orElseThrow();

        // then
        assertThat(member.isNotificationEnabled()).isTrue();
    }

    @Test
    @DisplayName("그룹원이면 그룹을 조회할 수 있고, 그룹원이 아니면 조회되지 않는다")
    void findByIdAndMemberUserId() {
        // given
        Long groupId = saveGroupWithOwner().getId();

        // when
        Optional<FryGroup> foundByMember = fryGroupRepository.findByIdAndMemberUserId(groupId, OWNER_ID);
        Optional<FryGroup> foundByStranger = fryGroupRepository.findByIdAndMemberUserId(groupId, OTHER_USER_ID);

        // then
        assertThat(foundByMember).isPresent();
        assertThat(foundByStranger).isEmpty();
    }

    @Test
    @DisplayName("그룹원 수를 셀 수 있다")
    void countByGroupId() {
        // given
        Long groupId = saveGroupWithOwner().getId();
        groupMemberRepository.saveAndFlush(GroupMember.builder().groupId(groupId).userId(OTHER_USER_ID).build());

        // when & then
        assertThat(groupMemberRepository.countByGroupId(groupId)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 그룹에서 같은 사용자가 같은 카테고리를 두 번 공개할 수 없다")
    void duplicatePublicCategoryIsRejected() {
        // given
        Long groupId = saveGroupWithOwner().getId();
        groupPublicCategoryRepository.saveAndFlush(
                GroupPublicCategory.builder().groupId(groupId).userId(OWNER_ID).categoryId(10L).build());

        // when & then
        assertThatThrownBy(() -> groupPublicCategoryRepository.saveAndFlush(
                GroupPublicCategory.builder().groupId(groupId).userId(OWNER_ID).categoryId(10L).build()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("그룹 해체 시 공개 카테고리와 그룹원을 그룹 단위로 삭제할 수 있다")
    void deleteAllByGroupId() {
        // given
        Long groupId = saveGroupWithOwner().getId();
        groupPublicCategoryRepository.saveAndFlush(
                GroupPublicCategory.builder().groupId(groupId).userId(OWNER_ID).categoryId(10L).build());

        // when
        groupPublicCategoryRepository.deleteAllByGroupId(groupId);
        groupMemberRepository.deleteAllByGroupId(groupId);
        fryGroupRepository.deleteById(groupId);

        // then
        assertThat(groupPublicCategoryRepository.findAllByGroupIdAndUserId(groupId, OWNER_ID)).isEmpty();
        assertThat(groupMemberRepository.countByGroupId(groupId)).isZero();
        assertThat(fryGroupRepository.findById(groupId)).isEmpty();
    }

    private FryGroup saveGroupWithOwner() {
        FryGroup group = fryGroupRepository.saveAndFlush(
                FryGroup.builder().name("바삭한 사람들").inviteCode("FRY123").ownerId(OWNER_ID).build());
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(group.getId()).userId(OWNER_ID).build());
        return group;
    }
}
