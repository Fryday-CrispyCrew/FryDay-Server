package basakan.fryday.repository.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.group.dto.GroupMemberDto;
import basakan.fryday.service.group.dto.GroupMemberTodoCountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_aggregation_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@DisplayName("그룹 집계 쿼리")
class GroupAggregationQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;

    private Long groupId;
    private Long ownerId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        ownerId = saveUser("owner", "연우");
        memberId = saveUser("member", "수정");
        groupId = saveGroup(ownerId);
        joinGroup(groupId, ownerId);
        joinGroup(groupId, memberId);
    }

    @Test
    @DisplayName("공개한 카테고리의 오늘 투두만 그룹원별로 집계된다")
    void aggregatesOnlyPublicCategoryTodos() {
        // given
        Category ownerPublic = saveCategory(ownerId, "공개");
        Category ownerPrivate = saveCategory(ownerId, "비공개");
        publish(ownerPublic, ownerId);

        saveTodo(ownerPublic, TODAY, Todo.Status.COMPLETED);
        saveTodo(ownerPublic, TODAY, Todo.Status.IN_PROGRESS);
        saveTodo(ownerPrivate, TODAY, Todo.Status.COMPLETED);

        // when
        Map<Long, GroupMemberTodoCountDto> counts = todoCounts(TODAY);

        // then
        assertThat(counts.get(ownerId).getTotalCount()).isEqualTo(2);
        assertThat(counts.get(ownerId).getCompletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("그룹원마다 자기가 공개한 카테고리 기준으로 따로 집계된다")
    void aggregatesPerMemberIndependently() {
        // given
        Category ownerA = saveCategory(ownerId, "운동");
        Category ownerB = saveCategory(ownerId, "공부");
        Category memberA = saveCategory(memberId, "취미");
        publish(ownerA, ownerId);
        publish(ownerB, ownerId);
        publish(memberA, memberId);

        saveTodo(ownerA, TODAY, Todo.Status.COMPLETED);
        saveTodo(ownerB, TODAY, Todo.Status.IN_PROGRESS);
        saveTodo(memberA, TODAY, Todo.Status.COMPLETED);
        saveTodo(memberA, TODAY, Todo.Status.COMPLETED);

        // when
        Map<Long, GroupMemberTodoCountDto> counts = todoCounts(TODAY);

        // then
        assertThat(counts.get(ownerId).getTotalCount()).isEqualTo(2);
        assertThat(counts.get(ownerId).getCompletedCount()).isEqualTo(1);
        assertThat(counts.get(memberId).getTotalCount()).isEqualTo(2);
        assertThat(counts.get(memberId).getCompletedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제된 카테고리의 투두는 집계에서 빠진다")
    void excludesDeletedCategory() {
        // given
        Category deleted = saveCategory(ownerId, "삭제될 카테고리");
        publish(deleted, ownerId);
        saveTodo(deleted, TODAY, Todo.Status.COMPLETED);
        deleted.delete();
        categoryRepository.saveAndFlush(deleted);

        // when & then
        assertThat(todoCounts(TODAY)).doesNotContainKey(ownerId);
    }

    @Test
    @DisplayName("삭제된 투두는 집계에서 빠진다")
    void excludesDeletedTodo() {
        // given
        Category category = saveCategory(ownerId, "운동");
        publish(category, ownerId);
        saveTodo(category, TODAY, Todo.Status.COMPLETED);
        Todo deleted = saveTodo(category, TODAY, Todo.Status.IN_PROGRESS);
        deleted.delete();
        todoRepository.saveAndFlush(deleted);

        // when
        Map<Long, GroupMemberTodoCountDto> counts = todoCounts(TODAY);

        // then
        assertThat(counts.get(ownerId).getTotalCount()).isEqualTo(1);
        assertThat(counts.get(ownerId).getCompletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("어제와 내일 투두는 오늘 집계에 들어가지 않는다")
    void excludesOtherDates() {
        // given
        Category category = saveCategory(ownerId, "운동");
        publish(category, ownerId);
        saveTodo(category, YESTERDAY, Todo.Status.COMPLETED);
        saveTodo(category, TOMORROW, Todo.Status.COMPLETED);
        saveTodo(category, TODAY, Todo.Status.IN_PROGRESS);

        // when
        Map<Long, GroupMemberTodoCountDto> counts = todoCounts(TODAY);

        // then
        assertThat(counts.get(ownerId).getTotalCount()).isEqualTo(1);
        assertThat(counts.get(ownerId).getCompletedCount()).isZero();
    }

    @Test
    @DisplayName("다른 그룹의 공개 설정은 섞이지 않는다")
    void isolatesGroups() {
        // given
        Long otherGroupId = saveGroup(ownerId);
        joinGroup(otherGroupId, ownerId);

        Category onlyInThisGroup = saveCategory(ownerId, "이 그룹만");
        Category onlyInOtherGroup = saveCategory(ownerId, "저 그룹만");
        publish(onlyInThisGroup, ownerId);
        groupPublicCategoryRepository.saveAndFlush(GroupPublicCategory.builder()
                .groupId(otherGroupId).userId(ownerId).categoryId(onlyInOtherGroup.getId()).build());

        saveTodo(onlyInThisGroup, TODAY, Todo.Status.COMPLETED);
        saveTodo(onlyInOtherGroup, TODAY, Todo.Status.COMPLETED);
        saveTodo(onlyInOtherGroup, TODAY, Todo.Status.COMPLETED);

        // when & then
        assertThat(todoCounts(TODAY).get(ownerId).getTotalCount()).isEqualTo(1);
        assertThat(todoCounts(otherGroupId, TODAY).get(ownerId).getTotalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("투두가 하나도 없는 그룹원은 집계 결과에 나타나지 않는다")
    void memberWithoutTodosIsAbsentFromAggregation() {
        // given
        Category category = saveCategory(ownerId, "운동");
        publish(category, ownerId);
        saveTodo(category, TODAY, Todo.Status.COMPLETED);

        // when & then — 서비스가 그룹원 목록 기준으로 0/0을 채워야 한다는 근거
        assertThat(todoCounts(TODAY)).containsKey(ownerId).doesNotContainKey(memberId);
    }

    @Test
    @DisplayName("그룹원 목록은 닉네임과 함께 참여 순서대로 조회된다")
    void findsMembersInJoinOrder() {
        // when
        List<GroupMemberDto> members = groupMemberRepository.findMembersWithNickname(groupId);

        // then
        assertThat(members).extracting(GroupMemberDto::getUserId).containsExactly(ownerId, memberId);
        assertThat(members).extracting(GroupMemberDto::getNickname).containsExactly("연우", "수정");
        assertThat(members.get(0).getJoinedAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴한 사용자는 그룹원 목록에서 제외된다")
    void excludesWithdrawnUser() {
        // given
        User withdrawn = userJpaRepository.findById(memberId).orElseThrow();
        withdrawn.withdraw();
        userJpaRepository.saveAndFlush(withdrawn);

        // when
        List<GroupMemberDto> members = groupMemberRepository.findMembersWithNickname(groupId);

        // then
        assertThat(members).extracting(GroupMemberDto::getUserId).containsExactly(ownerId);
    }

    private Map<Long, GroupMemberTodoCountDto> todoCounts(LocalDate date) {
        return todoCounts(groupId, date);
    }

    private Map<Long, GroupMemberTodoCountDto> todoCounts(Long targetGroupId, LocalDate date) {
        return groupPublicCategoryRepository.findTodoCountsByGroupAndDate(targetGroupId, date).stream()
                .collect(Collectors.toMap(GroupMemberTodoCountDto::getUserId, Function.identity()));
    }

    private Long saveUser(String providerUserId, String nickname) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        user.setNickname(nickname);
        return userJpaRepository.saveAndFlush(user).getId();
    }

    private Long saveGroup(Long owner) {
        FryGroup group = fryGroupRepository.saveAndFlush(FryGroup.builder()
                .name("바삭한 사람들")
                .inviteCode(randomCode())
                .ownerId(owner)
                .build());
        return group.getId();
    }

    private void joinGroup(Long targetGroupId, Long userId) {
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(targetGroupId).userId(userId).build());
    }

    private Category saveCategory(Long userId, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
    }

    private void publish(Category category, Long userId) {
        groupPublicCategoryRepository.saveAndFlush(GroupPublicCategory.builder()
                .groupId(groupId).userId(userId).categoryId(category.getId()).build());
    }

    private Todo saveTodo(Category category, LocalDate date, Todo.Status status) {
        Todo todo = Todo.builder()
                .description("투두").category(category).date(date).displayOrder(1L).build();
        if (status == Todo.Status.COMPLETED) {
            todo.toggleCompletion();
        }
        return todoRepository.saveAndFlush(todo);
    }

    private int codeSequence = 0;

    private String randomCode() {
        return String.format("FRY%03d", codeSequence++);
    }
}
