package com.englishhub.modules.catalog.entity;

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

/** Một câu phụ đề có mốc thời gian (bấm vào để tua audio/video đến StartMs). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "LessonTranscriptSegments")
public class LessonTranscriptSegment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LessonId", nullable = false)
    private Lesson lesson;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Integer startMs;

    @Column(nullable = false)
    private Integer endMs;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String text;

    @Nationalized
    @Column(length = 1000)
    private String translation;
}
