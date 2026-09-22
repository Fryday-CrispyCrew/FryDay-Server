package basakan.fryday.controller.group;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupInteractionRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.request.GroupNameUpdateRequest;
import basakan.fryday.controller.group.request.GroupNotificationSettingRequest;
import basakan.fryday.controller.group.request.GroupPublicCategoryUpdateRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.controller.group.response.GroupDetailResponse;
import basakan.fryday.controller.group.response.GroupInvitePreviewResponse;
import basakan.fryday.controller.group.response.GroupJoinResponse;
import basakan.fryday.controller.group.response.GroupListResponse;
import basakan.fryday.controller.group.response.GroupNameResponse;
import basakan.fryday.controller.group.response.GroupNotificationSettingResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryListResponse;
import basakan.fryday.service.group.GroupInteractionService;
import basakan.fryday.service.group.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupInteractionService groupInteractionService;

    @PostMapping
    public ApiResponse<GroupCreateResponse> createGroup(@Valid @RequestBody GroupCreateRequest request,
                                                        @AuthenticationPrincipal Long userId) {
        GroupCreateResponse response = groupService.createGroup(request, userId);
        return ApiResponse.success(response, "그룹이 생성되었습니다.");
    }

    @GetMapping
    public ApiResponse<GroupListResponse> getMyGroups(@AuthenticationPrincipal Long userId) {
        GroupListResponse response = groupService.getMyGroups(userId);
        return ApiResponse.success(response);
    }

    @GetMapping("/invite/{inviteCode}")
    public ApiResponse<GroupInvitePreviewResponse> getGroupByInviteCode(@PathVariable String inviteCode,
                                                                        @AuthenticationPrincipal Long userId) {
        GroupInvitePreviewResponse response = groupService.previewByInviteCode(inviteCode, userId);
        return ApiResponse.success(response);
    }

    @PostMapping("/join")
    public ApiResponse<GroupJoinResponse> joinGroup(@Valid @RequestBody GroupJoinRequest request,
                                                    @AuthenticationPrincipal Long userId) {
        GroupJoinResponse response = groupService.join(request, userId);
        return ApiResponse.success(response, "그룹에 참여했습니다.");
    }

    @GetMapping("/{groupId}")
    public ApiResponse<GroupDetailResponse> getGroup(@PathVariable Long groupId,
                                                     @AuthenticationPrincipal Long userId) {
        GroupDetailResponse response = groupService.getGroup(groupId, userId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{groupId}/name")
    public ApiResponse<GroupNameResponse> updateGroupName(@PathVariable Long groupId,
                                                          @Valid @RequestBody GroupNameUpdateRequest request,
                                                          @AuthenticationPrincipal Long userId) {
        GroupNameResponse response = groupService.updateGroupName(groupId, userId, request);
        return ApiResponse.success(response, "그룹 이름이 변경되었습니다.");
    }

    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long groupId,
                                         @AuthenticationPrincipal Long userId) {
        groupService.deleteGroup(groupId, userId);
        return ApiResponse.success(null, "그룹이 해체되었습니다.");
    }

    @DeleteMapping("/{groupId}/members/me")
    public ApiResponse<Void> leaveGroup(@PathVariable Long groupId,
                                        @AuthenticationPrincipal Long userId) {
        groupService.leaveGroup(groupId, userId);
        return ApiResponse.success(null, "그룹에서 탈퇴했습니다.");
    }

    @GetMapping("/{groupId}/members/me/notification")
    public ApiResponse<GroupNotificationSettingResponse> getNotificationSetting(@PathVariable Long groupId,
                                                                               @AuthenticationPrincipal Long userId) {
        GroupNotificationSettingResponse response = groupService.getNotificationSetting(groupId, userId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{groupId}/members/me/notification")
    public ApiResponse<GroupNotificationSettingResponse> updateNotificationSetting(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupNotificationSettingRequest request,
            @AuthenticationPrincipal Long userId) {
        GroupNotificationSettingResponse response = groupService.updateNotificationSetting(groupId, userId, request);
        return ApiResponse.success(response, "그룹 알림 설정이 변경되었습니다.");
    }

    @PostMapping("/{groupId}/members/{targetUserId}/interactions")
    public ApiResponse<Void> interact(@PathVariable Long groupId,
                                      @PathVariable Long targetUserId,
                                      @Valid @RequestBody GroupInteractionRequest request,
                                      @AuthenticationPrincipal Long userId) {
        groupInteractionService.interact(groupId, targetUserId, request, userId);
        return ApiResponse.success(null, "상호작용을 보냈습니다.");
    }

    @GetMapping("/{groupId}/public-categories")
    public ApiResponse<GroupPublicCategoryListResponse> getPublicCategories(@PathVariable Long groupId,
                                                                            @AuthenticationPrincipal Long userId) {
        GroupPublicCategoryListResponse response = groupService.getPublicCategories(groupId, userId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{groupId}/public-categories")
    public ApiResponse<GroupPublicCategoryListResponse> updatePublicCategories(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupPublicCategoryUpdateRequest request,
            @AuthenticationPrincipal Long userId) {
        GroupPublicCategoryListResponse response = groupService.updatePublicCategories(groupId, userId, request);
        return ApiResponse.success(response, "공개 카테고리가 변경되었습니다.");
    }
}
