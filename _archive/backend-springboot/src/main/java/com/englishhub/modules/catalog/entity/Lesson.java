package com.englishhub.modules.catalog.entity;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.entity.AuditableEntity;
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

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Lessons")
public class Lesson extends AuditableEntity {

    public enum MediaType { NONE, AUDIO, VIDEO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CourseId", nullable = false)
    private Course course;

    private Integer topicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LevelId", nullable = false)
    private Level level;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Skill skill;

    @Nationalized
    @Column(length = 500)
    private String summary;

    /** HTML: bài đọc, lý thuyết, đề bài. */
    @Nationalized
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MediaType mediaType = MediaType.NONE;

    @Nationalized
    @Column(length = 500)
    private String mediaUrl;

    private Integer durationSeconds;

    @Column(nullable = false)
    private Integer estimatedMinutes = 10;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "IsPublished", nullable = false)
    private boolean published;

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<LessonTranscriptSegment> transcript = new ArrayList<>();
}
