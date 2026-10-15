package com.englishhub.modules.assessment.entity;

import com.englishhub.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "AttemptAnswers")
public class AttemptAnswer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "AttemptId", nullable = false)
    private TestAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "QuestionId", nullable = false)
    private Question question;

    /** JSON mảng id đã chọn (choice) hoặc thứ tự id đã sắp (ordering). */
    @Column(length = 200)
    private String selectedOptionIds;

    /** JSON cho FILL_BLANK/MATCHING, văn bản thuần cho ESSAY/SPEAKING. */
    @Nationalized
    private String answerText;

    /** null = chưa chấm (ESSAY/SPEAKING chờ AI hoặc giáo viên). */
    @Column(name = "IsCorrect")
    private Boolean correct;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Nationalized
    private String feedback;

    private Instant answeredAt;
}
