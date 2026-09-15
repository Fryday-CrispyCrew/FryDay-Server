package basakan.fryday.service.group;

import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 그룹 한 건을 만드는 트랜잭션 단위.
 * 초대 코드가 동시 요청과 겹치면 이 트랜잭션만 롤백되고 {@link GroupService}가 새 코드로 다시 부른다.
 * 재시도마다 트랜잭션이 새로 열려야 하므로 {@code GroupService} 와 별도 빈으로 둔다.
 */
@Service
@RequiredArgsConstructor
public class GroupCreator {

    private final FryGroupRepository fryGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPublicCategoryRepository groupPublicCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public GroupCreateResponse create(String name, Long userId) {
        // saveAndFlush 로 초대 코드 중복을 즉시 드러내, 뒤따르는 INSERT 를 헛되이 실행하지 않는다.
        FryGroup group = fryGroupRepository.saveAndFlush(FryGroup.builder()
                .name(name)
                .inviteCode(inviteCodeGenerator.generate())
                .ownerId(userId)
                .build());

        groupMemberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .userId(userId)
                .build());

        publishAllOwnedCategories(group.getId(), userId);

        return GroupCreateResponse.from(group);
    }

    /** 그룹마다 최소 1개는 공개해야 하므로, 그룹을 만든 시점에는 내 카테고리를 전부 공개로 등록한다. */
    private void publishAllOwnedCategories(Long groupId, Long userId) {
        List<GroupPublicCategory> publicCategories =
                categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId).stream()
                        .map(category -> GroupPublicCategory.builder()
                                .groupId(groupId)
                                .userId(userId)
                                .categoryId(category.getId())
                                .build())
                        .toList();

        groupPublicCategoryRepository.saveAll(publicCategories);
    }
}
