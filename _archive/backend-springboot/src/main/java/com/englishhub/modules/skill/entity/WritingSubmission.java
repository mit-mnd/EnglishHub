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

/** Một bài viết đã nộp và nhận xét tự động. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "WritingSubmissions")
public class WritingSubmission extends AuditableEntity {

    public enum Status { PENDING, SCORED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long lessonId;

    private Long questionId;

    @Nationalized
    @Column(length = 2000)
    private String prompt;

    @Nationalized
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Integer wordCount = 0;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Nationalized
    private String correctedContent;

    @Nationalized
    private String feedbackJson;

    @Column(nullable = false, length = 30)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Nationalized
    @Column(length = 500)
    private String errorMessage;
}
