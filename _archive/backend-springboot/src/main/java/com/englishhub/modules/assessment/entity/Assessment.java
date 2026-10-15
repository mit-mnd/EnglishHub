package com.englishhub.modules.assessment.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/** Một bài kiểm tra (bảng Tests). Đặt tên Assessment để không trùng annotation @Test của JUnit. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Tests")
public class Assessment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String title;

    @Nationalized
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestType testType;

    private Long lessonId;

    private Integer levelId;

    /** null = không giới hạn thời gian. */
    private Integer durationMinutes;

    @Column(precision = 5, scale = 2)
    private BigDecimal passingPercent;

    @Column(nullable = false)
    private boolean shuffleQuestions;

    private Integer maxAttempts;

    @Column(name = "IsPublished", nullable = false)
    private boolean published;

    private Long createdBy;

    @OneToMany(mappedBy = "assessment")
    @OrderBy("sortOrder ASC")
    private List<AssessmentQuestion> questions = new ArrayList<>();
}
