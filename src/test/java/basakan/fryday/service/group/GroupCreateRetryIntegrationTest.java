package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.FryGroup;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 초대 코드 충돌 재시도를 목이 아닌 실제 DB unique 제약으로 검증한다.
 * 재시도마다 새 트랜잭션이 열리지 않으면 첫 충돌에서 rollback-only 가 되어 이 테스트는 실패한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_create_retry_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupService.class, GroupCreator.class})
@DisplayName("초대 코드 충돌 재시도")
class GroupCreateRetryIntegrationTest {

    private static final Long OWNER_ID = 1L;
    private static final String TAKEN_CODE = "FRY123";
    private static final String FREE_CODE = "ZZZ999";

    /** 실제 생성기는 이미 쓰인 코드를 피하므로, 충돌을 만들려면 코드 발급을 고정해야 한다. */
    @MockitoBean
    private InviteCodeGenerator inviteCodeGenerator;

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();

        categoryRepository.saveAndFlush(Category.builder()
                .name("운동").color(CategoryColor.OR).userId(OWNER_ID).displayOrder(1L).build());

        // 이미 쓰이고 있는 초대 코드를 하나 심어 둔다
        fryGroupRepository.saveAndFlush(FryGroup.builder()
                .name("선점한 그룹").inviteCode(TAKEN_CODE).ownerId(99L).build());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("이미 쓰인 코드가 나와도 다음 코드로 다시 만들어 사용자는 성공한다")
    void retriesOnRealUniqueConstraintViolation() {
        // given — 첫 시도는 이미 선점된 코드, 두 번째는 빈 코드
        given(inviteCodeGenerator.generate()).willReturn(TAKEN_CODE, FREE_CODE);

        // when
        GroupCreateResponse response = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID);

        // then — 재시도로 성공하고, 실패한 시도의 흔적은 남지 않는다
        assertThat(response.getInviteCode()).isEqualTo(FREE_CODE);
        assertThat(fryGroupRepository.count()).isEqualTo(2);
        assertThat(groupMemberRepository.countByGroupId(response.getGroupId())).isEqualTo(1);
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(response.getGroupId(), OWNER_ID))
                .isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("계속 같은 코드만 나오면 재시도를 소진하고 그룹은 하나도 만들어지지 않는다")
    void givesUpAfterAllRetriesWithoutLeavingData() {
        // given — 항상 이미 선점된 코드만 나온다
        given(inviteCodeGenerator.generate()).willReturn(TAKEN_CODE);

        // when & then
        assertThatThrownBy(() -> groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVITE_CODE_GENERATION_FAILED.getMessage());

        // 선점 그룹 하나만 남고, 실패한 시도의 그룹원/공개 카테고리도 남지 않는다
        assertThat(fryGroupRepository.count()).isEqualTo(1);
        assertThat(groupMemberRepository.count()).isZero();
        assertThat(groupPublicCategoryRepository.count()).isZero();
    }
}
