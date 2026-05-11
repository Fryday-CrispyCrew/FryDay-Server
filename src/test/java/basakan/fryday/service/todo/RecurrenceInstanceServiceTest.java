package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.CancelRecurrenceRequest.CancelScope;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
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

        @Test
        @DisplayName("선택 Todo의 recurrenceId가 null로 변경된다")
        void recurrenceId_null로_변경() {
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(instance));

            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS, USER_ID);

            assertThat(instance.getRecurrenceId()).isNull();
        }

        @Test
        @DisplayName("override 값이 있으면 base 필드에 이관된다")
        void override_base_필드_이관() {
            instance.applyOverride("오버라이드 제목", "오버라이드 메모", null, null);
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(instance));

            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS, USER_ID);

            assertThat(instance.getDescription()).isEqualTo("오버라이드 제목");
            assertThat(instance.getMemo()).isEqualTo("오버라이드 메모");
        }

        @Test
        @DisplayName("override 필드가 null로 정리된다")
        void override_필드_null_정리() {
            instance.applyOverride("오버라이드 제목", "오버라이드 메모", true, null);
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(instance));

            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS, USER_ID);

            assertThat(instance.getOverrideTitle()).isNull();
            assertThat(instance.getOverrideMemo()).isNull();
            assertThat(instance.getOverrideIsAlarm()).isNull();
            assertThat(instance.isOverridden()).isFalse();
        }

        @Test
        @DisplayName("반복 투두가 아니면 NOT_RECURRING_TODO 예외")
        void 비반복_투두_예외() {
            Todo nonRecurring = Todo.builder()
                    .description("일반 투두").category(category)
                    .date(LocalDate.now()).displayOrder(1L).build();
            ReflectionTestUtils.setField(nonRecurring, "id", INSTANCE_ID);
            given(todoRepository.findById(INSTANCE_ID)).willReturn(Optional.of(nonRecurring));

            assertThatThrownBy(() -> service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS, USER_ID))
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

            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS_AND_FUTURE, USER_ID);

            assertThat(master.getEndDate()).isEqualTo(T.minusDays(1)); // 2026-05-09
            assertThat(master.getEndType()).isEqualTo(EndType.UNTIL);
        }

        @Test
        @DisplayName("T+1일 이후 인스턴스가 soft delete된다")
        void T_이후_인스턴스_soft_delete() {
            LocalDate T = instance.getDate();

            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS_AND_FUTURE, USER_ID);

            then(todoRepository).should()
                    .bulkSoftDeleteByRecurrenceIdAndDateGte(eq(RECURRENCE_ID), eq(T.plusDays(1)), any());
        }

        @Test
        @DisplayName("선택 Todo(T일)는 일반 Todo로 전환된다")
        void 선택_Todo_일반_전환() {
            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS_AND_FUTURE, USER_ID);

            assertThat(instance.getRecurrenceId()).isNull();
        }

        @Test
        @DisplayName("T-1일 이전 인스턴스는 유지된다 (bulkSoftDelete 호출 범위가 T+1부터)")
        void T_이전_인스턴스_유지() {
            LocalDate T = instance.getDate();

            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS_AND_FUTURE, USER_ID);

            // T+1 이후만 삭제 → T-1 이전은 삭제되지 않음을 인자로 검증
            then(todoRepository).should()
                    .bulkSoftDeleteByRecurrenceIdAndDateGte(eq(RECURRENCE_ID), eq(T.plusDays(1)), any());
        }

        @Test
        @DisplayName("master.startDate == T인 엣지케이스: terminateAt만 실행, 오류 없음")
        void startDate_equals_T_엣지케이스() {
            LocalDate T = instance.getDate();
            ReflectionTestUtils.setField(master, "startDate", T);

            service.cancelRecurrence(INSTANCE_ID, CancelScope.THIS_AND_FUTURE, USER_ID);

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
            service.cancelRecurrence(INSTANCE_ID, CancelScope.ALL, USER_ID);

            assertThat(instance.getRecurrenceId()).isNull();
        }

        @Test
        @DisplayName("선택 Todo는 soft delete되지 않는다")
        void 선택_Todo_soft_delete_안됨() {
            service.cancelRecurrence(INSTANCE_ID, CancelScope.ALL, USER_ID);

            assertThat(instance.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("나머지 모든 인스턴스가 soft delete된다")
        void 나머지_인스턴스_soft_delete() {
            service.cancelRecurrence(INSTANCE_ID, CancelScope.ALL, USER_ID);

            assertThat(other1.isDeleted()).isTrue();
            assertThat(other2.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("Master(Recurrence)가 삭제된다")
        void Master_삭제() {
            service.cancelRecurrence(INSTANCE_ID, CancelScope.ALL, USER_ID);

            then(recurrenceRepository).should().delete(master);
        }
    }
}
