package com.englishhub.modules.catalog.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.user.entity.LearningGoal;
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

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Courses")
public class Course extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Nationalized
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LevelId", nullable = false)
    private Level level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LearningGoal goalType = LearningGoal.GENERAL;

    @Nationalized
    @Column(length = 500)
    private String thumbnailUrl;

    @Column(name = "IsPublished", nullable = false)
    private boolean published;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    private Long createdBy;

    @OneToMany(mappedBy = "course")
    @OrderBy("sortOrder ASC, id ASC")
    private List<Lesson> lessons = new ArrayList<>();
}
