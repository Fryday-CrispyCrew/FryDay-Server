package basakan.fryday.service.todo;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.todo.response.TodoListResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.user.UserReadService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:todo_list_read_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({
        JpaConfig.class,
        TodoService.class,
        RecurrenceOccurrenceWriter.class,
        RecurrenceOccurrenceMaterializeService.class,
        RecurrenceOccurrenceCalculator.class,
        UserReadService.class
})
@DisplayName("투두 목록 조회 통합")
class TodoListReadIntegrationTest {

    @Autowired private TodoService todoService;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("요청 범위의 영속성 컨텍스트 없이도 카테고리 ID를 담아 조회한다")
    void getTodoListWithoutSurroundingTransaction() {
        // given
        Long userId = userJpaRepository.save(User.createNewUser(AuthProvider.KAKAO, "todo-list", "t@t.com")).getId();
        Category category = categoryRepository.save(
                Category.builder().name("운동").color(CategoryColor.BR).userId(userId).displayOrder(1L).build());
        LocalDate today = LocalDate.now();
        todoRepository.save(Todo.builder().description("스쿼트").category(category).date(today).displayOrder(1L).build());

        // when
        List<TodoListResponse> responses = todoService.getTodoList(userId, today, null);

        // then
        assertThat(responses).extracting(TodoListResponse::getCategoryId).containsExactly(category.getId());
    }
}
