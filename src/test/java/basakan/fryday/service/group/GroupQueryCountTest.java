package basakan.fryday.service.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.response.GroupDetailResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.repository.todo.TodoRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 그룹 조회는 그룹원 수와 무관하게 고정된 쿼리 수로 끝나야 한다.
 * 기존 DailyResultService 처럼 반복 조회로 구현하면 그룹원 10명에서 쿼리가 선형 증가한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_query_count_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.generate_statistics=true",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class})
@DisplayName("그룹 API 쿼리 수")
class GroupQueryCountTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired private GroupService groupService;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUserWithTodos("owner", "연우");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 2명이든 정원 10명이든 조회 쿼리 수가 같다")
    void queryCountDoesNotGrowWithMemberCount() {
        // given
        Long smallGroupId = createGroupWith(1);
        Long fullGroupId = createGroupWith(9);

        // when
        long small = countQueries(smallGroupId);
        long full = countQueries(fullGroupId);

        // then
        assertThat(full).isEqualTo(small);
        assertThat(full).isLessThanOrEqualTo(4);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹 해체는 재조회 없이 조회 1회 + 삭제 3회로 끝난다")
    void deleteGroupDoesNotReSelect() {
        // given
        Long groupId = createGroupWith(2);

        // when
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        groupService.deleteGroup(groupId, ownerId);

        // then — 그룹 조회 1 + 공개카테고리/그룹원/그룹 삭제 3
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
        assertThat(fryGroupRepository.findById(groupId)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹 탈퇴는 조회 1회 + 삭제 2회로 끝난다")
    void leaveGroupDoesNotReSelect() {
        // given
        Long groupId = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), ownerId).getGroupId();
        Long memberId = saveUserWithTodos("leaver" + groupId, "떠날사람");
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(groupId).userId(memberId).build());
        Category category = categoryRepository
                .findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(memberId).get(0);
        groupPublicCategoryRepository.saveAndFlush(basakan.fryday.domain.group.GroupPublicCategory.builder()
                .groupId(groupId).userId(memberId).categoryId(category.getId()).build());

        // when
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        groupService.leaveGroup(groupId, memberId);

        // then — 그룹 조회 1 + 공개카테고리/그룹원 삭제 2
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, memberId)).isFalse();
    }

    private long countQueries(Long groupId) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        GroupDetailResponse response = groupService.getGroup(groupId, ownerId);
        assertThat(response.getMembers()).isNotEmpty();

        return statistics.getPrepareStatementCount();
    }

    private Long createGroupWith(int extraMembers) {
        Long groupId = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), ownerId).getGroupId();
        for (int i = 0; i < extraMembers; i++) {
            Long memberId = saveUserWithTodos("member" + groupId + "_" + i, "그룹원" + i);
            groupMemberRepository.saveAndFlush(
                    GroupMember.builder().groupId(groupId).userId(memberId).build());
            Category category = categoryRepository
                    .findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(memberId).get(0);
            groupPublicCategoryRepository.saveAndFlush(basakan.fryday.domain.group.GroupPublicCategory.builder()
                    .groupId(groupId).userId(memberId).categoryId(category.getId()).build());
        }
        return groupId;
    }

    private Long saveUserWithTodos(String providerUserId, String nickname) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        user.setNickname(nickname);
        Long userId = userJpaRepository.saveAndFlush(user).getId();

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("운동").color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
        Todo done = Todo.builder().description("완료")
                .category(category).date(LocalDate.now(KOREA_ZONE)).displayOrder(1L).build();
        done.toggleCompletion();
        todoRepository.saveAndFlush(done);
        todoRepository.saveAndFlush(Todo.builder().description("미완료")
                .category(category).date(LocalDate.now(KOREA_ZONE)).displayOrder(2L).build());
        return userId;
    }
}
