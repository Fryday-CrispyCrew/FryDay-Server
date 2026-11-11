package basakan.fryday.domain.admin;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "admin_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_admin_account_login_id", columnNames = {"login_id"})
        }
)
public class AdminAccount extends BaseEntity {

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    // BCrypt 해시
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    private LocalDateTime lastLoginAt;

    @Builder
    private AdminAccount(String loginId, String password, String name) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
    }

    public void recordLogin(LocalDateTime loggedInAt) {
        this.lastLoginAt = loggedInAt;
    }
}
