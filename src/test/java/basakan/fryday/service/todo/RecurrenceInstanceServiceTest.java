package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.todo.RecurrenceScope;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurrenceInstanceService - cancelRecurrence")
class RecurrenceInstanceServiceTest {

    @Mock private TodoRepository todoRepository;
    @Mock private RecurrenceRepository recurrenceRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TodoAlarmRepository todoAlarmRepository;
    @Mock private RecurrenceOccurrenceCalculator occurrenceCalculator;

    @InjectMocks
    private RecurrenceInstanceService service;

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long RECURRENCE_ID = 10L;
    private static final long INSTANCE_ID = 100L;

    private Category category;
    private Recurrence master;
    private Todo instance;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .name("업무").color(CategoryColor.BR).userId(USER_ID).build();
        ReflectionTestUtils.setField(category, "id", 1L);

        master = Recurrence.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .description("마스터 제목")
                .type(RecurrenceType.DAILY)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .endType(EndType.UNTIL)
                .lastGeneratedDate(LocalDate.of(2026, 1, 1))
                .build();
        ReflectionTestUtils.setField(master, "id", RECURRENCE_ID);

        instance = Todo.builder()
                .description("마스터 제목")
                .category(category)
                .date(LocalDate.of(2026, 5, 10))
                .displayOrder(1L)
                .recurrenceId(RECURRENCE_ID)
                .build();
        ReflectionTestUtils.setField(instance, "id", INSTANCE_ID);
    }

    // ── cancelThis ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelThis")
    class CancelThis {

        @BeforeEach
        void setUpMock() {
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(instance));
            lenient().when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("원본 인스턴스는 recurrenceId를 유지한 채 soft delete된다 (materializer 재생성 방지)")
        void 원본_인스턴스_soft_delete_recurrenceId_유지() {
            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID);

            assertThat(instance.isDeleted()).isTrue();
            assertThat(instance.getRecurrenceId()).isEqualTo(RECURRENCE_ID);
        }

        @Test
        @DisplayName("새 독립 Todo가 recurrenceId null로 저장된다")
        void 새_독립_Todo_저장() {
            ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID);

            then(todoRepository).should().save(captor.capture());
            assertThat(captor.getValue().getRecurrenceId()).isNull();
        }

        @Test
        @DisplayName("override 값이 있으면 새 Todo의 description/memo에 반영된다")
        void override_새_Todo에_반영() {
            instance.applyOverride("오버라이드 제목", "오버라이드 메모", null, null);
            ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID);

            then(todoRepository).should().save(captor.capture());
            assertThat(captor.getValue().getDescription()).isEqualTo("오버라이드 제목");
            assertThat(captor.getValue().getMemo()).isEqualTo("오버라이드 메모");
        }

        @Test
        @DisplayName("override 없으면 base memo가 새 Todo에 그대로 이관된다")
        void base_memo_이관() {
            Todo instanceWithMemo = Todo.builder()
                    .description("마스터 제목")
                    .category(category)
                    .date(LocalDate.of(2026, 5, 10))
                    .displayOrder(1L)
                    .recurrenceId(RECURRENCE_ID)
                    .memo("기본 메모")
                    .build();
            ReflectionTestUtils.setField(instanceWithMemo, "id", INSTANCE_ID);
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(instanceWithMemo));
            ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID);

            then(todoRepository).should().save(captor.capture());
            assertThat(captor.getValue().getMemo()).isEqualTo("기본 메모");
        }

        @Test
        @DisplayName("완료 상태가 새 Todo에 유지된다")
        void 완료_상태_유지() {
            instance.toggleCompletion();
            ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID);

            then(todoRepository).should().save(captor.capture());
            assertThat(captor.getValue().isCompleted()).isTrue();
        }

        @Test
        @DisplayName("알람이 있으면 새 독립 Todo로 이관된다")
        void 알람_이관() {
            TodoAlarm alarm = mock(TodoAlarm.class);
            given(todoAlarmRepository.findByTodoId(INSTANCE_ID)).willReturn(Optional.of(alarm));
            ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID);

            then(todoRepository).should().save(captor.capture());
            then(alarm).should().reassignTo(captor.getValue());
        }

        @Test
        @DisplayName("알람이 없으면 이관 없이 정상 완료된다")
        void 알람_없으면_정상_완료() {
            given(todoAlarmRepository.findByTodoId(INSTANCE_ID)).willReturn(Optional.empty());

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID);

            then(todoAlarmRepository).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("반복 투두가 아니면 NOT_RECURRING_TODO 예외")
        void 비반복_투두_예외() {
            Todo nonRecurring = Todo.builder()
                    .description("일반 투두").category(category)
                    .date(LocalDate.now()).displayOrder(1L).build();
            ReflectionTestUtils.setField(nonRecurring, "id", INSTANCE_ID);
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(nonRecurring));

            assertThatThrownBy(() -> service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RECURRING_TODO);
        }
    }

    // ── cancelThisAndFuture ───────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelThisAndFuture")
    class CancelThisAndFuture {

        @BeforeEach
        void setUpMocks() {
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(instance));
            given(recurrenceRepository.findById(RECURRENCE_ID)).willReturn(Optional.of(master));
        }

        @Test
        @DisplayName("Master의 endDate가 T-1일로 수정된다")
        void master_endDate_T_마이너스_1() {
            LocalDate T = instance.getDate(); // 2026-05-10

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS_AND_FUTURE, USER_ID);

            assertThat(master.getEndDate()).isEqualTo(T.minusDays(1)); // 2026-05-09
            assertThat(master.getEndType()).isEqualTo(EndType.UNTIL);
        }

        @Test
        @DisplayName("T+1일 이후 인스턴스가 soft delete된다")
        void T_이후_인스턴스_soft_delete() {
            LocalDate T = instance.getDate();

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS_AND_FUTURE, USER_ID);

            then(todoRepository).should()
                    .bulkSoftDeleteByRecurrenceIdAndDateGte(eq(RECURRENCE_ID), eq(T.plusDays(1)), any());
        }

        @Test
        @DisplayName("선택 Todo(T일)는 일반 Todo로 전환된다")
        void 선택_Todo_일반_전환() {
            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS_AND_FUTURE, USER_ID);

            assertThat(instance.getRecurrenceId()).isNull();
        }

        @Test
        @DisplayName("T-1일 이전 인스턴스는 유지된다 (bulkSoftDelete 호출 범위가 T+1부터)")
        void T_이전_인스턴스_유지() {
            LocalDate T = instance.getDate();

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS_AND_FUTURE, USER_ID);

            // T+1 이후만 삭제 → T-1 이전은 삭제되지 않음을 인자로 검증
            then(todoRepository).should()
                    .bulkSoftDeleteByRecurrenceIdAndDateGte(eq(RECURRENCE_ID), eq(T.plusDays(1)), any());
        }

        @Test
        @DisplayName("master.startDate == T인 엣지케이스: terminateAt만 실행, 오류 없음")
        void startDate_equals_T_엣지케이스() {
            LocalDate T = instance.getDate();
            ReflectionTestUtils.setField(master, "startDate", T);

            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.THIS_AND_FUTURE, USER_ID);

            // endDate = T-1 < startDate = T 이지만 예외 없이 실행됨
            assertThat(master.getEndDate()).isEqualTo(T.minusDays(1));
        }
    }

    // ── cancelAll ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelAll")
    class CancelAll {

        private Todo other1;
        private Todo other2;

        @BeforeEach
        void setUpOthers() {
            other1 = Todo.builder()
                    .description("마스터 제목").category(category)
                    .date(LocalDate.of(2026, 5, 11)).displayOrder(1L)
                    .recurrenceId(RECURRENCE_ID).build();
            ReflectionTestUtils.setField(other1, "id", 101L);

            other2 = Todo.builder()
                    .description("마스터 제목").category(category)
                    .date(LocalDate.of(2026, 5, 12)).displayOrder(1L)
                    .recurrenceId(RECURRENCE_ID).build();
            ReflectionTestUtils.setField(other2, "id", 102L);

            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(instance));
            given(recurrenceRepository.findById(RECURRENCE_ID)).willReturn(Optional.of(master));
            given(todoRepository.findAllByRecurrenceId(RECURRENCE_ID))
                    .willReturn(List.of(instance, other1, other2));
        }

        @Test
        @DisplayName("선택 Todo의 recurrenceId가 null로 변경된다")
        void 선택_Todo_recurrenceId_null() {
            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.ALL, USER_ID);

            assertThat(instance.getRecurrenceId()).isNull();
        }

        @Test
        @DisplayName("선택 Todo는 soft delete되지 않는다")
        void 선택_Todo_soft_delete_안됨() {
            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.ALL, USER_ID);

            assertThat(instance.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("나머지 모든 인스턴스가 soft delete된다")
        void 나머지_인스턴스_soft_delete() {
            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.ALL, USER_ID);

            assertThat(other1.isDeleted()).isTrue();
            assertThat(other2.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("Master(Recurrence)가 삭제된다")
        void Master_삭제() {
            service.cancelRecurrence(INSTANCE_ID, RecurrenceScope.ALL, USER_ID);

            then(recurrenceRepository).should().delete(master);
        }
    }
}
