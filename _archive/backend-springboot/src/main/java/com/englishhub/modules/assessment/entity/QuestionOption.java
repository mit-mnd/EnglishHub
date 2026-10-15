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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "QuestionOptions")
public class QuestionOption extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "QuestionId", nullable = false)
    private Question question;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "IsCorrect", nullable = false)
    private boolean correct;

    /** Vế phải đúng (MATCHING). */
    @Nationalized
    @Column(length = 1000)
    private String matchText;

    /** Vị trí đúng, bắt đầu từ 1 (ORDERING). */
    private Integer correctOrder;

    @Column(nullable = false)
    private Integer sortOrder = 0;
}
