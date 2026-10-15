package com.englishhub.modules.assessment.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.common.domain.Skill;
import com.englishhub.modules.catalog.entity.Level;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Câu hỏi trong ngân hàng câu hỏi. Cách lưu đáp án theo {@link QuestionType}:
 * SINGLE/MULTIPLE_CHOICE dùng QuestionOptions.IsCorrect; FILL_BLANK dùng AnswerKey (JSON);
 * MATCHING dùng QuestionOptions.MatchText; ORDERING dùng QuestionOptions.CorrectOrder.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Questions")
public class Question extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelId")
    private Level level;

    private Integer topicId;

    @Nationalized
    @Column(nullable = false)
    private String content;

    @Nationalized
    private String passage;

    @Nationalized
    @Column(length = 500)
    private String mediaUrl;

    @Nationalized
    private String answerKey;

    @Nationalized
    private String explanation;

    @Column(nullable = false, columnDefinition = "tinyint")
    private Integer difficulty = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultPoints = BigDecimal.ONE;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;

    private Long createdBy;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<QuestionOption> options = new ArrayList<>();
}
