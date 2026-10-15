package com.englishhub.modules.assessment.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.assessment.entity.Enums.AttemptStatus;
import com.englishhub.modules.catalog.entity.Level;
import com.englishhub.modules.user.entity.User;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TestAttempts")
public class TestAttempt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TestId", nullable = false)
    private Assessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant expiresAt;

    private Instant submittedAt;

    @Column(precision = 7, scale = 2)
    private BigDecimal score;

    @Column(precision = 7, scale = 2)
    private BigDecimal maxScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResultLevelId")
    private Level resultLevel;

    @Nationalized
    private String skillScoresJson;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttemptAnswer> answers = new ArrayList<>();

    /** Hết giờ khi đã quá ExpiresAt + thời gian ân hạn (bù độ trễ mạng). */
    public boolean isExpired(Instant now, Duration gracePeriod) {
        return expiresAt != null && now.isAfter(expiresAt.plus(gracePeriod));
    }

    public Optional<AttemptAnswer> findAnswer(Long questionId) {
        return answers.stream().filter(a -> a.getQuestion().getId().equals(questionId)).findFirst();
    }

    public void addAnswer(AttemptAnswer answer) {
        answer.setAttempt(this);
        answers.add(answer);
    }
}
