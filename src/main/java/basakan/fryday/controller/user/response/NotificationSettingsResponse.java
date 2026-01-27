package basakan.fryday.controller.user.response;

public record NotificationSettingsResponse(
        boolean pushNotificationEnabled,
        boolean marketingAgreed
) {
    public static NotificationSettingsResponse of(boolean pushNotificationEnabled, boolean marketingAgreed) {
        return new NotificationSettingsResponse(pushNotificationEnabled, marketingAgreed);
    }
}
