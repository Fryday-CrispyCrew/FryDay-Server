package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupPublicCategoryUpdateRequest;
import basakan.fryday.controller.group.response.GroupMemberCategoryTodosResponse;
import basakan.fryday.controller.group.response.GroupMemberTodoListResponse;
import basakan.fryday.controller.group.response.GroupMemberTodoResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.repository.todo.TodoRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_member_todo_read_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@DisplayName("그룹원 공개 투두 조회")
class GroupMemberTodoReadIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long memberId;
    private Long strangerId;
    private Long groupId;

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUser("owner", "연우");
        memberId = saveUser("member", "수정");
        strangerId = saveUser("stranger", "낯선이");

        saveCategory(ownerId, "운동", 1L);
        groupId = createGroup("바삭한 사람들", ownerId);
        join(groupId, memberId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개한 카테고리의 투두만 조회되고, 공개하지 않은 카테고리의 투두는 조회되지 않는다")
    void onlyPublicCategoryTodosAreReturned() {
        // given
        Category publicCategory = saveCategory(memberId, "공부", 1L);
        Category privateCategory = saveCategory(memberId, "일기", 2L);
        publish(groupId, memberId, publicCategory);

        saveTodo(publicCategory, "영단어 외우기", today());
        saveTodo(publicCategory, "수학 문제 풀기", today());
        saveTodo(privateCategory, "비밀 일기 쓰기", today());

        // when
        GroupMemberTodoListResponse response = groupService.getMemberTodos(groupId, memberId, ownerId);

        // then
        assertThat(response.getUserId()).isEqualTo(memberId);
        assertThat(response.getDate()).isEqualTo(today());
        assertThat(response.getCategories())
                .extracting(GroupMemberCategoryTodosResponse::getCategoryId)
                .containsExactly(publicCategory.getId());
        assertThat(allDescriptions(response))
                .containsExactlyInAnyOrder("영단어 외우기", "수학 문제 풀기")
                .doesNotContain("비밀 일기 쓰기");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 카테고리를 바꾸면 새로 공개한 카테고리의 투두만 조회된다")
    void changingPublicCategoriesChangesResult() {
        // given
        Category study = saveCategory(memberId, "공부", 1L);
        Category diary = saveCategory(memberId, "일기", 2L);
        publish(groupId, memberId, study);
        saveTodo(study, "영단어 외우기", today());
        saveTodo(diary, "일기 쓰기", today());

        // when
        groupService.updatePublicCategories(groupId, memberId,
                new GroupPublicCategoryUpdateRequest(List.of(diary.getId())));
        GroupMemberTodoListResponse response = groupService.getMemberTodos(groupId, memberId, ownerId);

        // then
        assertThat(response.getCategories())
                .extracting(GroupMemberCategoryTodosResponse::getCategoryId)
                .containsExactly(diary.getId());
        assertThat(allDescriptions(response)).containsExactly("일기 쓰기");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 설정은 그룹마다 독립적이어서, 다른 그룹에만 공개한 카테고리의 투두는 조회되지 않는다")
    void publicSettingIsIndependentPerGroup() {
        // given
        Category study = saveCategory(memberId, "공부", 1L);
        Category diary = saveCategory(memberId, "일기", 2L);
        Long otherGroupId = createGroup("다른 그룹", ownerId);
        join(otherGroupId, memberId);

        publish(groupId, memberId, study);
        publish(otherGroupId, memberId, diary);
        saveTodo(study, "영단어 외우기", today());
        saveTodo(diary, "일기 쓰기", today());

        // when
        GroupMemberTodoListResponse response = groupService.getMemberTodos(groupId, memberId, ownerId);
        GroupMemberTodoListResponse otherResponse = groupService.getMemberTodos(otherGroupId, memberId, ownerId);

        // then
        assertThat(allDescriptions(response)).containsExactly("영단어 외우기");
        assertThat(allDescriptions(otherResponse)).containsExactly("일기 쓰기");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("투두가 없는 공개 카테고리도 빈 목록으로 포함되고, 카테고리 순서를 따른다")
    void publicCategoryWithoutTodosIsIncludedInOrder() {
        // given
        Category study = saveCategory(memberId, "공부", 2L);
        Category workout = saveCategory(memberId, "운동", 1L);
        publish(groupId, memberId, study);
        publish(groupId, memberId, workout);
        saveTodo(study, "영단어 외우기", today());

        // when
        GroupMemberTodoListResponse response = groupService.getMemberTodos(groupId, memberId, ownerId);

        // then
        assertThat(response.getCategories())
                .extracting(GroupMemberCategoryTodosResponse::getName,
                        category -> category.getTodos().size())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("운동", 0),
                        org.assertj.core.groups.Tuple.tuple("공부", 1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 카테고리여도 오늘이 아닌 투두와 삭제된 투두는 조회되지 않는다")
    void otherDateAndDeletedTodosAreExcluded() {
        // given
        Category study = saveCategory(memberId, "공부", 1L);
        publish(groupId, memberId, study);
        saveTodo(study, "오늘 할 일", today());
        saveTodo(study, "내일 할 일", today().plusDays(1));
        Todo deleted = saveTodo(study, "지운 할 일", today());
        deleted.delete();
        todoRepository.saveAndFlush(deleted);

        // when
        GroupMemberTodoListResponse response = groupService.getMemberTodos(groupId, memberId, ownerId);

        // then
        assertThat(allDescriptions(response)).containsExactly("오늘 할 일");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 아닌 사용자는 조회할 수 없다")
    void strangerCannotReadMemberTodos() {
        assertThatThrownBy(() -> groupService.getMemberTodos(groupId, memberId, strangerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 아닌 사용자의 투두는 조회할 수 없다")
    void cannotReadTodosOfNonMember() {
        // given
        Category category = saveCategory(strangerId, "공부", 1L);
        saveTodo(category, "영단어 외우기", today());

        // when & then
        assertThatThrownBy(() -> groupService.getMemberTodos(groupId, strangerId, ownerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    private List<String> allDescriptions(GroupMemberTodoListResponse response) {
        return response.getCategories().stream()
                .flatMap(category -> category.getTodos().stream())
                .map(GroupMemberTodoResponse::getDescription)
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(KOREA_ZONE);
    }

    private Long createGroup(String name, Long userId) {
        return groupService.createGroup(new GroupCreateRequest(name), userId).getGroupId();
    }

    private void join(Long groupId, Long userId) {
        groupMemberRepository.saveAndFlush(GroupMember.builder().groupId(groupId).userId(userId).build());
    }

    private void publish(Long groupId, Long userId, Category category) {
        groupPublicCategoryRepository.saveAndFlush(GroupPublicCategory.builder()
                .groupId(groupId).userId(userId).categoryId(category.getId()).build());
    }

    private Long saveUser(String providerUserId, String nickname) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        user.setNickname(nickname);
        return userJpaRepository.saveAndFlush(user).getId();
    }

    private Category saveCategory(Long userId, String name, Long displayOrder) {
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(displayOrder).build());
    }

    private Todo saveTodo(Category category, String description, LocalDate date) {
        return todoRepository.saveAndFlush(Todo.builder()
                .description(description).category(category).date(date).displayOrder(1L).build());
    }
}
