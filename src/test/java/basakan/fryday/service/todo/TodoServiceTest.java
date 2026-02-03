package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceExceptionRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TodoAlarmRepository todoAlarmRepository;

    @Mock
    private RecurrenceRepository recurrenceRepository;

    @Mock
    private RecurrenceExceptionRepository recurrenceExceptionRepository;

    @Mock
    private RecurrenceOccurrenceMaterializeService materializeService;

    @Mock
    private RecurrenceOccurrenceCalculator occurrenceCalculator;

    @InjectMocks
    private TodoService todoService;

    private static final Long USER_ID = 1L;
    private static final Long TODO_ID = 100L;
    private static final Long CATEGORY_ID = 1L;

    private Category category;
    private Todo inProgressTodo;
    private Todo completedTodo;
    private LocalDate today;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        tomorrow = today.plusDays(1);

        category = Category.builder().name("운동").color(CategoryColor.BR).userId(USER_ID).build();
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);

        inProgressTodo = Todo.builder()
                .description("스쿼트 100개")
                .category(category)
                .date(today)
                .displayOrder(1L)
                .build();
        ReflectionTestUtils.setField(inProgressTodo, "id", TODO_ID);

        completedTodo = Todo.builder()
                .description("런닝 30분")
                .category(category)
                .date(today)
                .displayOrder(2L)
                .memo("완료 메모")
                .build();
        ReflectionTestUtils.setField(completedTodo, "id", TODO_ID + 1);
        completedTodo.toggleCompletion();
    }

    @Nested
    @DisplayName("postponeToTomorrow")
    class PostponeToTomorrow {

        @Test
        @DisplayName("미완료 투두 - 이동 (날짜만 변경)")
        void 미완료_투두_이동() {
            // given
            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(inProgressTodo));

            // when
            var result = todoService.postponeToTomorrow(TODO_ID, USER_ID);

            // then
            assertThat(result.getId()).isEqualTo(TODO_ID);
            assertThat(result.getDate()).isEqualTo(tomorrow);
            assertThat(result.getStatus()).isEqualTo(Todo.Status.IN_PROGRESS.name());
            assertThat(inProgressTodo.getDate()).isEqualTo(tomorrow);
            then(todoRepository).should().findById(TODO_ID);
        }

        @Test
        @DisplayName("완료 투두 - 복사 (새 투두 생성)")
        void 완료_투두_복사() {
            // given
            Todo copiedTodo = Todo.builder()
                    .description(completedTodo.getDescription())
                    .category(category)
                    .date(tomorrow)
                    .displayOrder(1L)
                    .memo(completedTodo.getMemo())
                    .build();
            ReflectionTestUtils.setField(copiedTodo, "id", 999L);

            given(todoRepository.findById(completedTodo.getId())).willReturn(Optional.of(completedTodo));
            given(todoRepository.findMaxDisplayOrder(USER_ID, tomorrow)).willReturn(null);
            given(todoRepository.save(any(Todo.class))).willReturn(copiedTodo);

            // when
            var result = todoService.postponeToTomorrow(completedTodo.getId(), USER_ID);

            // then - 복사된 새 투두 반환
            assertThat(result.getId()).isEqualTo(999L);
            assertThat(result.getDate()).isEqualTo(tomorrow);
            assertThat(result.getDescription()).isEqualTo("런닝 30분");
            assertThat(result.getStatus()).isEqualTo(Todo.Status.IN_PROGRESS.name()); // 복사본은 미완료
            assertThat(result.getMemo()).isEqualTo("완료 메모");
            // 원본은 그대로 유지 (완료 상태, 원래 날짜)
            assertThat(completedTodo.getDate()).isEqualTo(today);
            assertThat(completedTodo.isCompleted()).isTrue();
            then(todoRepository).should().save(any(Todo.class));
        }

        @Test
        @DisplayName("실패 - 오늘 날짜가 아닌 투두")
        void 실패_오늘_날짜가_아님() {
            // given - 어제 날짜의 투두
            Todo yesterdayTodo = Todo.builder()
                    .description("어제 투두")
                    .category(category)
                    .date(today.minusDays(1))
                    .displayOrder(1L)
                    .build();
            ReflectionTestUtils.setField(yesterdayTodo, "id", TODO_ID);
            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(yesterdayTodo));

            // when & then
            assertThatThrownBy(() -> todoService.postponeToTomorrow(TODO_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TODO_NOT_TODAY);
        }

        @Test
        @DisplayName("미완료 반복 투두 - 이동")
        void 미완료_반복투두_이동() {
            // given
            Long recurrenceId = 10L;
            Todo recurringTodo = Todo.builder()
                    .description("반복 운동")
                    .category(category)
                    .date(today)
                    .displayOrder(1L)
                    .recurrenceId(recurrenceId)
                    .build();
            ReflectionTestUtils.setField(recurringTodo, "id", TODO_ID);

            Recurrence recurrence = Recurrence.builder()
                    .userId(USER_ID)
                    .categoryId(CATEGORY_ID)
                    .description("반복 운동")
                    .type(RecurrenceType.DAILY)
                    .startDate(today)
                    .lastGeneratedDate(today)
                    .build();

            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(recurringTodo));
            given(recurrenceRepository.findById(recurrenceId)).willReturn(Optional.of(recurrence));
            given(recurrenceExceptionRepository.findByRecurrenceIdAndOccurrenceDate(recurrenceId, today))
                    .willReturn(Optional.empty());
            given(recurrenceExceptionRepository.findByRecurrenceId(recurrenceId)).willReturn(List.of());
            given(occurrenceCalculator.calculateOccurrences(any(), eq(tomorrow), eq(tomorrow), any()))
                    .willReturn(List.of(tomorrow));

            // when
            var result = todoService.postponeToTomorrow(TODO_ID, USER_ID);

            // then
            assertThat(result.getId()).isEqualTo(TODO_ID);
            assertThat(result.getDate()).isEqualTo(tomorrow);
            assertThat(recurringTodo.getDate()).isEqualTo(tomorrow);
        }

        @Test
        @DisplayName("완료 반복 투두 - 복사 (recurrenceId 없이 새 투두)")
        void 완료_반복투두_복사() {
            // given
            Long recurrenceId = 10L;
            Todo completedRecurring = Todo.builder()
                    .description("완료된 반복")
                    .category(category)
                    .date(today)
                    .displayOrder(1L)
                    .recurrenceId(recurrenceId)
                    .build();
            ReflectionTestUtils.setField(completedRecurring, "id", TODO_ID);
            completedRecurring.toggleCompletion();

            Todo copiedTodo = Todo.builder()
                    .description(completedRecurring.getDescription())
                    .category(category)
                    .date(tomorrow)
                    .displayOrder(1L)
                    .build();
            ReflectionTestUtils.setField(copiedTodo, "id", 888L);

            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(completedRecurring));
            given(todoRepository.findMaxDisplayOrder(USER_ID, tomorrow)).willReturn(null);
            given(todoRepository.save(any(Todo.class))).willReturn(copiedTodo);

            // when
            var result = todoService.postponeToTomorrow(TODO_ID, USER_ID);

            // then - 복사본은 recurrenceId 없음
            assertThat(result.getId()).isEqualTo(888L);
            assertThat(result.getDate()).isEqualTo(tomorrow);
            assertThat(result.getStatus()).isEqualTo(Todo.Status.IN_PROGRESS.name());
            then(todoRepository).should().save(any(Todo.class));
        }

        @Test
        @DisplayName("실패 - 투두 없음")
        void 실패_투두_없음() {
            given(todoRepository.findById(TODO_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> todoService.postponeToTomorrow(TODO_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TODO_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("moveToToday")
    class MoveToToday {

        @Test
        @DisplayName("미완료 투두 - 이동")
        void 미완료_투두_이동() {
            // given - 내일 날짜 투두
            Todo futureTodo = Todo.builder()
                    .description("내일 할 일")
                    .category(category)
                    .date(tomorrow)
                    .displayOrder(1L)
                    .build();
            ReflectionTestUtils.setField(futureTodo, "id", TODO_ID);
            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(futureTodo));

            // when
            var result = todoService.moveToToday(TODO_ID, USER_ID);

            // then
            assertThat(result.getId()).isEqualTo(TODO_ID);
            assertThat(result.getDate()).isEqualTo(today);
            assertThat(futureTodo.getDate()).isEqualTo(today);
        }

        @Test
        @DisplayName("완료 투두 - 복사")
        void 완료_투두_복사() {
            // given - 어제 완료한 투두
            Todo yesterdayCompleted = Todo.builder()
                    .description("어제 완료")
                    .category(category)
                    .date(today.minusDays(1))
                    .displayOrder(1L)
                    .memo("메모")
                    .build();
            ReflectionTestUtils.setField(yesterdayCompleted, "id", TODO_ID);
            yesterdayCompleted.toggleCompletion();

            Todo copiedTodo = Todo.builder()
                    .description(yesterdayCompleted.getDescription())
                    .category(category)
                    .date(today)
                    .displayOrder(1L)
                    .memo(yesterdayCompleted.getMemo())
                    .build();
            ReflectionTestUtils.setField(copiedTodo, "id", 777L);

            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(yesterdayCompleted));
            given(todoRepository.findMaxDisplayOrder(USER_ID, today)).willReturn(5L);
            given(todoRepository.save(any(Todo.class))).willReturn(copiedTodo);

            // when
            var result = todoService.moveToToday(TODO_ID, USER_ID);

            // then
            assertThat(result.getId()).isEqualTo(777L);
            assertThat(result.getDate()).isEqualTo(today);
            assertThat(result.getDescription()).isEqualTo("어제 완료");
            assertThat(result.getStatus()).isEqualTo(Todo.Status.IN_PROGRESS.name());
            assertThat(yesterdayCompleted.getDate()).isEqualTo(today.minusDays(1));
            assertThat(yesterdayCompleted.isCompleted()).isTrue();
            then(todoRepository).should().save(any(Todo.class));
        }

        @Test
        @DisplayName("미완료 반복 투두 - 이동")
        void 미완료_반복투두_이동() {
            // given
            Long recurrenceId = 10L;
            Todo recurringTodo = Todo.builder()
                    .description("반복 투두")
                    .category(category)
                    .date(tomorrow)
                    .displayOrder(1L)
                    .recurrenceId(recurrenceId)
                    .build();
            ReflectionTestUtils.setField(recurringTodo, "id", TODO_ID);

            Recurrence recurrence = Recurrence.builder()
                    .userId(USER_ID)
                    .categoryId(CATEGORY_ID)
                    .description("반복 투두")
                    .type(RecurrenceType.DAILY)
                    .startDate(today)
                    .lastGeneratedDate(tomorrow)
                    .build();

            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(recurringTodo));
            given(recurrenceRepository.findById(recurrenceId)).willReturn(Optional.of(recurrence));
            given(recurrenceExceptionRepository.findByRecurrenceIdAndOccurrenceDate(recurrenceId, tomorrow))
                    .willReturn(Optional.empty());
            given(recurrenceExceptionRepository.findByRecurrenceId(recurrenceId)).willReturn(List.of());
            given(occurrenceCalculator.calculateOccurrences(any(), eq(today), eq(today), any()))
                    .willReturn(List.of(today));

            // when
            var result = todoService.moveToToday(TODO_ID, USER_ID);

            // then
            assertThat(result.getId()).isEqualTo(TODO_ID);
            assertThat(result.getDate()).isEqualTo(today);
        }

        @Test
        @DisplayName("완료 반복 투두 - 복사")
        void 완료_반복투두_복사() {
            // given
            Long recurrenceId = 10L;
            Todo completedRecurring = Todo.builder()
                    .description("완료된 반복")
                    .category(category)
                    .date(today.minusDays(2))
                    .displayOrder(1L)
                    .recurrenceId(recurrenceId)
                    .build();
            ReflectionTestUtils.setField(completedRecurring, "id", TODO_ID);
            completedRecurring.toggleCompletion();

            Todo copiedTodo = Todo.builder()
                    .description(completedRecurring.getDescription())
                    .category(category)
                    .date(today)
                    .displayOrder(1L)
                    .build();
            ReflectionTestUtils.setField(copiedTodo, "id", 666L);

            given(todoRepository.findById(TODO_ID)).willReturn(Optional.of(completedRecurring));
            given(todoRepository.findMaxDisplayOrder(USER_ID, today)).willReturn(null);
            given(todoRepository.save(any(Todo.class))).willReturn(copiedTodo);

            // when
            var result = todoService.moveToToday(TODO_ID, USER_ID);

            // then
            assertThat(result.getId()).isEqualTo(666L);
            assertThat(result.getDate()).isEqualTo(today);
            assertThat(result.getStatus()).isEqualTo(Todo.Status.IN_PROGRESS.name());
        }

        @Test
        @DisplayName("실패 - 투두 없음")
        void 실패_투두_없음() {
            given(todoRepository.findById(TODO_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> todoService.moveToToday(TODO_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TODO_NOT_FOUND);
        }
    }
}
