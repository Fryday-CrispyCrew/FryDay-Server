package basakan.fryday.controller.admin.response;

import lombok.Getter;

@Getter
public class BroadcastPushResponse {

    private final int sentCount;

    public BroadcastPushResponse(int sentCount) {
        this.sentCount = sentCount;
    }
}
