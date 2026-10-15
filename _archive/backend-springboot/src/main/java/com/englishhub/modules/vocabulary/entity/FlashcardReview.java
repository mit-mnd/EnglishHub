package com.englishhub.modules.vocabulary.entity;

import com.englishhub.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Nhật ký mỗi lần ôn một thẻ (để thống kê và tinh chỉnh thuật toán sau này). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "FlashcardReviews")
public class FlashcardReview extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userWordId;

    @Column(nullable = false)
    private Long userId;

    /** Chất lượng SM-2 (0-5): AGAIN=1, HARD=3, GOOD=4, EASY=5. */
    @Column(nullable = false, columnDefinition = "tinyint")
    private Integer quality;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal prevEaseFactor;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal newEaseFactor;

    @Column(nullable = false)
    private Integer prevIntervalDays;

    @Column(nullable = false)
    private Integer newIntervalDays;

    private Integer responseTimeMs;

    @Column(nullable = false)
    private Instant reviewedAt;
}
