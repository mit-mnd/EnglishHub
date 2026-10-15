package com.englishhub.modules.notification.entity;

import com.englishhub.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Notifications")
public class Notification extends AuditableEntity {

    public enum Type { STUDY_REMINDER, REVIEW_DUE, STREAK, ACHIEVEMENT, PATH_UPDATED, SYSTEM }

    public enum Channel { IN_APP, EMAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String title;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String message;

    /** Route frontend khi bấm vào thông báo, vd. /app/vocabulary/review */
    @Nationalized
    @Column(length = 300)
    private String link;

    @Column(name = "IsRead", nullable = false)
    private boolean read;

    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel = Channel.IN_APP;
}
