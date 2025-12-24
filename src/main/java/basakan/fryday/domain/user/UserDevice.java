package basakan.fryday.domain.user;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"userId", "deviceId"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String deviceId;

    @Column(length = 500)
    private String fcmToken;

    @Column(length = 20)
    private String deviceType;  // iOS, Android, Web

    @Column(length = 100)
    private String deviceName;  // "iPhone 14 Pro", "Galaxy S23" 등

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime lastUsedAt;

    @Builder
    public UserDevice(Long userId, String deviceId, String fcmToken,
                      String deviceType, String deviceName) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.fcmToken = fcmToken;
        this.deviceType = deviceType;
        this.deviceName = deviceName;
        this.isActive = true;
        this.lastUsedAt = LocalDateTime.now();
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
        this.lastUsedAt = LocalDateTime.now();
    }

    public void activate() {
        this.isActive = true;
        this.lastUsedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
    }
}
