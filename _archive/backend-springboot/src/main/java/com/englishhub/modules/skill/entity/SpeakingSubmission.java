package com.englishhub.modules.skill.entity;

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

import java.math.BigDecimal;

/** Một lần thu âm luyện nói và kết quả chấm phát âm. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SpeakingSubmissions")
public class SpeakingSubmission extends AuditableEntity {

    public enum Status { PENDING, SCORED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long lessonId;

    private Long questionId;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String referenceText;

    /** Khóa lưu trữ của file ghi âm (không phải URL công khai). */
    @Nationalized
    @Column(nullable = false, length = 500)
    private String audioUrl;

    @Nationalized
    @Column(length = 1000)
    private String recognizedText;

    @Column(precision = 5, scale = 2)
    private BigDecimal pronunciationScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal accuracyScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal fluencyScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal completenessScore;

    /** {"estimated":..,"words":[...]} - chi tiết từng từ để hiển thị lại. */
    @Nationalized
    private String detailJson;

    @Column(nullable = false, length = 30)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Nationalized
    @Column(length = 500)
    private String errorMessage;
}
