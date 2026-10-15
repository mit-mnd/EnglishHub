package com.englishhub.modules.user.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.catalog.entity.Level;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String fullName;

    @Nationalized
    @Column(length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CurrentLevelId")
    private Level currentLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LearningGoal learningGoal = LearningGoal.GENERAL;

    @Column(nullable = false)
    private Integer dailyGoalMinutes = 15;

    @Column(nullable = false, length = 50)
    private String timeZone = "Asia/Ho_Chi_Minh";

    @Column(nullable = false)
    private boolean reminderEnabled = true;

    private LocalTime reminderTime;

    @Column(nullable = false)
    private boolean emailNotificationEnabled = false;

    private Instant placementCompletedAt;

    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "UserRoles",
            joinColumns = @JoinColumn(name = "UserId"),
            inverseJoinColumns = @JoinColumn(name = "RoleId"))
    private Set<Role> roles = new HashSet<>();

    public Set<String> roleCodes() {
        return roles.stream().map(r -> r.getCode().name()).collect(Collectors.toSet());
    }

    public boolean hasRole(RoleCode code) {
        return roles.stream().anyMatch(r -> r.getCode() == code);
    }
}
