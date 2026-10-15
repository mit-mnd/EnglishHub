package com.englishhub.modules.assessment.entity;

import com.englishhub.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/** Câu hỏi thuộc một bài test (bảng TestQuestions), kèm thứ tự và điểm riêng. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TestQuestions")
public class AssessmentQuestion extends AuditableEntity {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("testId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TestId")
    private Assessment assessment;

    @MapsId("questionId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QuestionId")
    private Question question;

    @Column(nullable = false)
    private Integer sortOrder;

    /** null = dùng Questions.DefaultPoints. */
    @Column(precision = 5, scale = 2)
    private BigDecimal points;

    public BigDecimal effectivePoints() {
        return points != null ? points : question.getDefaultPoints();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "TestId")
        private Long testId;

        @Column(name = "QuestionId")
        private Long questionId;
    }
}
