package com.englishhub.modules.vocabulary.entity;

import com.englishhub.common.entity.AuditableEntity;
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
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

/** Từ trong sổ từ vựng cá nhân + trạng thái lặp lại ngắt quãng SM-2 (dùng ở module Flashcard). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "UserWords")
public class UserWord extends AuditableEntity {

    public enum Source { MANUAL, READING_LOOKUP, VOCAB_SET, LESSON }

    public enum Status { NEW, LEARNING, REVIEW, MASTERED, SUSPENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "WordId", nullable = false)
    private Word word;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.MANUAL;

    private Long sourceLessonId;

    @Nationalized
    @Column(length = 1000)
    private String contextSentence;

    @Nationalized
    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor = new BigDecimal("2.50");

    @Column(nullable = false)
    private Integer intervalDays = 0;

    @Column(nullable = false)
    private Integer repetitions = 0;

    @Column(nullable = false)
    private Integer lapseCount = 0;

    @Column(nullable = false)
    private Instant dueAt;

    private Instant lastReviewedAt;
}
