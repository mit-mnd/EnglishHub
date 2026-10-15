package com.englishhub.modules.learningpath.entity;

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

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "LearningPathItems")
public class LearningPathItem extends AuditableEntity {

    /**
     * LOCKED = "sắp tới" (vẫn mở học được, chỉ là chưa tới lượt theo gợi ý);
     * AVAILABLE = bài nên học tiếp theo; IN_PROGRESS = đã bắt đầu; COMPLETED / SKIPPED = đã xong.
     */
    public enum Status { LOCKED, AVAILABLE, IN_PROGRESS, COMPLETED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PathId", nullable = false)
    private LearningPath path;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LessonId", nullable = false)
    private Lesson lesson;

    @Column(nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.LOCKED;

    /** Học viên tự thêm vào lộ trình. */
    @Column(name = "IsCustom", nullable = false)
    private boolean custom;

    private LocalDate plannedDate;

    private Instant completedAt;

    public boolean isDone() {
        return status == Status.COMPLETED || status == Status.SKIPPED;
    }
}
