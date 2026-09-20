package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupNameUpdateRequest;
import basakan.fryday.controller.group.request.GroupPublicCategoryUpdateRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.controller.group.response.GroupDetailResponse;
import basakan.fryday.controller.group.response.GroupMemberResponse;
import basakan.fryday.controller.group.response.GroupNameResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryListResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.domain.group.GroupRole;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.service.group.dto.GroupMemberDto;
import basakan.fryday.service.group.dto.GroupMemberTodoCountDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_CREATE_ATTEMPTS = 5;

    private final FryGroupRepository fryGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPublicCategoryRepository groupPublicCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final GroupCreator groupCreator;

    /**
     * 초대 코드는 발급 직전 중복 확인과 DB unique 제약 사이에 아주 좁은 경쟁 구간이 있다.
     * 동시 요청이 같은 코드를 뽑아 제약에 걸리면 사용자에게 오류를 돌려주지 않고 새 코드로 다시 만든다.
     * 재시도마다 새 트랜잭션이 필요하므로 이 메서드 자체는 트랜잭션 밖에서 돈다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GroupCreateResponse createGroup(GroupCreateRequest request, Long userId) {
        String name = GroupNameValidator.validateAndNormalize(request.getName());

        for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS; attempt++) {
            try {
                return groupCreator.create(name, userId);
            } catch (DataIntegrityViolationException inviteCodeCollision) {
                // 다른 요청이 같은 초대 코드를 선점했다. 다음 시도에서 새 코드를 뽑는다.
            }
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    public GroupDetailResponse getGroup(Long groupId, Long userId) {
        FryGroup group = findGroupJoinedBy(groupId, userId);
        LocalDate date = LocalDate.now(KOREA_ZONE);

        List<GroupMemberResponse> members = buildMemberResponses(group, date);
        int myPublicCategoryCount = (int) groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, userId);

        return GroupDetailResponse.of(group, GroupRole.of(group, userId), myPublicCategoryCount, date, members);
    }

    /** 그룹장을 맨 앞에 두고, 나머지는 참여 순서를 유지한다. */
    private List<GroupMemberResponse> buildMemberResponses(FryGroup group, LocalDate date) {
        Map<Long, GroupMemberTodoCountDto> todoCountsByUserId =
                groupPublicCategoryRepository.findTodoCountsByGroupAndDate(group.getId(), date).stream()
                        .collect(Collectors.toMap(GroupMemberTodoCountDto::getUserId, Function.identity()));

        return groupMemberRepository.findMembersWithNickname(group.getId()).stream()
                .map(member -> toMemberResponse(group, member, todoCountsByUserId))
                .sorted(Comparator.comparing(GroupMemberResponse::isOwner).reversed())
                .toList();
    }

    private GroupMemberResponse toMemberResponse(FryGroup group, GroupMemberDto member,
                                                 Map<Long, GroupMemberTodoCountDto> todoCountsByUserId) {
        return GroupMemberResponse.of(
                member,
                GroupRole.of(group, member.getUserId()),
                todoCountsByUserId.get(member.getUserId()));
    }

    @Transactional
    public GroupNameResponse updateGroupName(Long groupId, Long userId, GroupNameUpdateRequest request) {
        FryGroup group = findGroupOwnedBy(groupId, userId);

        group.updateName(GroupNameValidator.validateAndNormalize(request.getName()));

        return GroupNameResponse.from(group);
    }

    @Transactional
    public void deleteGroup(Long groupId, Long userId) {
        FryGroup group = findGroupOwnedBy(groupId, userId);

        groupPublicCategoryRepository.deleteAllByGroupId(groupId);
        groupMemberRepository.deleteAllByGroupId(groupId);
        fryGroupRepository.deleteGroupById(group.getId());
    }

    /**
     * 회원 탈퇴 정리. 내가 그룹장인 그룹은 해체하고, 그룹원으로 참여 중인 그룹에서는 내 흔적만 지운다.
     */
    @Transactional
    public void leaveAllGroups(Long userId) {
        for (FryGroup ownedGroup : fryGroupRepository.findAllByOwnerId(userId)) {
            groupPublicCategoryRepository.deleteAllByGroupId(ownedGroup.getId());
            groupMemberRepository.deleteAllByGroupId(ownedGroup.getId());
            fryGroupRepository.deleteGroupById(ownedGroup.getId());
        }

        groupPublicCategoryRepository.deleteAllByUserId(userId);
        groupMemberRepository.deleteAllByUserId(userId);
    }

    public GroupPublicCategoryListResponse getPublicCategories(Long groupId, Long userId) {
        findGroupJoinedBy(groupId, userId);

        Set<Long> publicCategoryIds = publicCategoryIds(groupId, userId);

        List<GroupPublicCategoryResponse> categories =
                categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId).stream()
                        .map(category -> GroupPublicCategoryResponse.of(
                                category, publicCategoryIds.contains(category.getId())))
                        .toList();

        return GroupPublicCategoryListResponse.from(categories);
    }

    /**
     * 공개 카테고리를 통째로 교체한다. 그룹마다 최소 1개는 공개해야 한다.
     */
    @Transactional
    public GroupPublicCategoryListResponse updatePublicCategories(Long groupId, Long userId,
                                                                  GroupPublicCategoryUpdateRequest request) {
        findGroupJoinedBy(groupId, userId);

        List<Long> categoryIds = request.getCategoryIds();
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PUBLIC_CATEGORY_REQUIRED);
        }

        List<Category> categories = findOwnedCategories(categoryIds, userId);

        groupPublicCategoryRepository.deleteAllByGroupIdAndUserId(groupId, userId);
        publishCategories(groupId, userId, categories);

        return getPublicCategories(groupId, userId);
    }

    private void publishCategories(Long groupId, Long userId, List<Category> categories) {
        groupPublicCategoryRepository.saveAll(categories.stream()
                .map(category -> GroupPublicCategory.builder()
                        .groupId(groupId)
                        .userId(userId)
                        .categoryId(category.getId())
                        .build())
                .toList());
    }

    /** 요청된 id 가 전부 내 소유의 살아있는 카테고리인지 한 번의 조회로 확인한다. */
    private List<Category> findOwnedCategories(List<Long> categoryIds, Long userId) {
        List<Long> requestedIds = categoryIds.stream().distinct().toList();
        List<Category> categories =
                categoryRepository.findAllByIdInAndUserIdAndDeletedAtIsNull(requestedIds, userId);

        if (categories.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return categories;
    }

    private Set<Long> publicCategoryIds(Long groupId, Long userId) {
        return groupPublicCategoryRepository.findAllByGroupIdAndUserId(groupId, userId).stream()
                .map(GroupPublicCategory::getCategoryId)
                .collect(Collectors.toSet());
    }

    private FryGroup findGroupOwnedBy(Long groupId, Long userId) {
        FryGroup group = findGroupJoinedBy(groupId, userId);
        if (!group.isOwner(userId)) {
            throw new BusinessException(ErrorCode.GROUP_OWNER_ONLY);
        }
        return group;
    }

    private FryGroup findGroupJoinedBy(Long groupId, Long userId) {
        return fryGroupRepository.findByIdAndMemberUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }
}
