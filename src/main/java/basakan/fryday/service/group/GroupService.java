package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.request.GroupNameUpdateRequest;
import basakan.fryday.controller.group.request.GroupNotificationSettingRequest;
import basakan.fryday.controller.group.request.GroupPublicCategoryUpdateRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.controller.group.response.GroupDetailResponse;
import basakan.fryday.controller.group.response.GroupInvitePreviewResponse;
import basakan.fryday.controller.group.response.GroupJoinResponse;
import basakan.fryday.controller.group.response.GroupListResponse;
import basakan.fryday.controller.group.response.GroupMemberResponse;
import basakan.fryday.controller.group.response.GroupNameResponse;
import basakan.fryday.controller.group.response.GroupNotificationSettingResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryListResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryResponse;
import basakan.fryday.controller.group.response.GroupSummaryResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.domain.group.GroupRole;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.service.group.dto.GroupMemberDto;
import basakan.fryday.service.group.dto.GroupMemberTodoCountDto;
import basakan.fryday.service.group.event.GroupDisbandedEvent;
import basakan.fryday.service.group.event.GroupJoinedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final UserJpaRepository userJpaRepository;
    private final GroupCreator groupCreator;
    private final ApplicationEventPublisher eventPublisher;

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

    /** 참여 중인 그룹을 최근 가입순으로 내려준다. 참여한 그룹이 없으면 빈 목록이다. */
    public GroupListResponse getMyGroups(Long userId) {
        List<GroupSummaryResponse> groups = groupMemberRepository.findMyGroups(userId).stream()
                .map(group -> GroupSummaryResponse.of(group, userId))
                .toList();

        return GroupListResponse.from(groups);
    }

    /** 초대 코드 입력 팝업용. 참여 가능 여부까지 판단해 내려준다. */
    public GroupInvitePreviewResponse previewByInviteCode(String rawInviteCode, Long userId) {
        FryGroup group = fryGroupRepository.findByInviteCode(normalizeInviteCode(rawInviteCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));

        return GroupInvitePreviewResponse.of(
                group,
                (int) groupMemberRepository.countByGroupId(group.getId()),
                groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId));
    }

    /**
     * 초대 코드로 참여하면서 공개 카테고리까지 한 트랜잭션에서 확정한다.
     * 정원 검사와 그룹원 INSERT 사이에 다른 참여가 끼어들지 못하도록 그룹 행을 잠그고 시작한다.
     */
    @Transactional
    public GroupJoinResponse join(GroupJoinRequest request, Long userId) {
        FryGroup group = fryGroupRepository.findByInviteCodeForUpdate(normalizeInviteCode(request.getInviteCode()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));

        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new BusinessException(ErrorCode.GROUP_ALREADY_JOINED);
        }

        long memberCount = groupMemberRepository.countByGroupId(group.getId());
        if (memberCount >= FryGroup.MAX_MEMBER_COUNT) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }

        List<Category> categories = findOwnedCategories(request.getCategoryIds(), userId);

        saveMember(group.getId(), userId);
        publishCategories(group.getId(), userId, categories);

        eventPublisher.publishEvent(new GroupJoinedEvent(
                group.getId(),
                group.getName(),
                userJpaRepository.findById(userId).map(User::getNickname).orElse(null),
                groupMemberRepository.findNotifiableUserIds(group.getId(), userId)));

        return GroupJoinResponse.of(group, (int) memberCount + 1);
    }

    /**
     * 중복 참여는 앞선 검사에서 대부분 걸러지고, 그래도 빠져나간 요청은 uk_group_member 제약이 막는다.
     * saveAndFlush 로 제약 위반을 이 자리에서 바로 받아, 500 대신 이미 참여했다는 응답으로 돌려준다.
     */
    private void saveMember(Long groupId, Long userId) {
        try {
            groupMemberRepository.saveAndFlush(GroupMember.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .build());
        } catch (DataIntegrityViolationException alreadyJoined) {
            throw new BusinessException(ErrorCode.GROUP_ALREADY_JOINED);
        }
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

        // 아래 벌크 삭제가 그룹원 행을 지우므로 수신자는 그 전에 읽어둔다
        eventPublisher.publishEvent(new GroupDisbandedEvent(
                group.getName(), groupMemberRepository.findNotifiableUserIds(groupId, userId)));

        groupPublicCategoryRepository.deleteAllByGroupId(groupId);
        groupMemberRepository.deleteAllByGroupId(groupId);
        fryGroupRepository.deleteGroupById(group.getId());
    }

    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        FryGroup group = findGroupJoinedBy(groupId, userId);

        if (group.isOwner(userId)) {
            throw new BusinessException(ErrorCode.GROUP_OWNER_CANNOT_LEAVE);
        }

        groupPublicCategoryRepository.deleteAllByGroupIdAndUserId(groupId, userId);
        groupMemberRepository.deleteAllByGroupIdAndUserId(groupId, userId);
    }

    public GroupNotificationSettingResponse getNotificationSetting(Long groupId, Long userId) {
        return GroupNotificationSettingResponse.from(findMembership(groupId, userId));
    }

    @Transactional
    public GroupNotificationSettingResponse updateNotificationSetting(Long groupId, Long userId,
                                                                      GroupNotificationSettingRequest request) {
        GroupMember member = findMembership(groupId, userId);

        member.updateNotificationEnabled(request.getEnabled());

        return GroupNotificationSettingResponse.from(member);
    }

    private GroupMember findMembership(Long groupId, Long userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
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

    /** 초대 코드는 대문자로 발급되지만 입력은 대소문자를 가리지 않는다. */
    private String normalizeInviteCode(String rawInviteCode) {
        return rawInviteCode.trim().toUpperCase(Locale.ROOT);
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
