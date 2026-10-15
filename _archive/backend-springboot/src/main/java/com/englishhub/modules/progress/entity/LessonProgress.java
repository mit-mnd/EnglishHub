package com.englishhub.modules.progress.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.catalog.entity.Lesson;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Tiến độ của một học viên với một bài học (1 dòng / user / bài). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "LessonProgresses")
public class LessonProgress extends AuditableEntity {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LessonId", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal bestScore;

    private Integer lastPositionMs;

    @Column(nullable = false)
    private Integer timeSpentSeconds = 0;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }
}
