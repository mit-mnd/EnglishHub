package com.englishhub.modules.learningpath.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.catalog.entity.Level;
import com.englishhub.modules.user.entity.LearningGoal;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "LearningPaths")
public class LearningPath extends AuditableEntity {

    public enum Source { PLACEMENT, GOAL, MANUAL }

    public enum Status { ACTIVE, COMPLETED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LearningGoal goalType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LevelId", nullable = false)
    private Level level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private Integer dailyMinutes = 15;

    private LocalDate targetDate;

    private Long placementAttemptId;

    @OneToMany(mappedBy = "path", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<LearningPathItem> items = new ArrayList<>();

    public void addItem(LearningPathItem item) {
        item.setPath(this);
        items.add(item);
    }

    public Optional<LearningPathItem> findItem(Long itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst();
    }

    public Optional<LearningPathItem> findItemByLesson(Long lessonId) {
        return items.stream().filter(i -> i.getLesson().getId().equals(lessonId)).findFirst();
    }
}
