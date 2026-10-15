package com.englishhub.modules.vocabulary.entity;

import com.englishhub.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.ArrayList;
import java.util.List;

/** Bộ từ vựng theo chủ đề (OwnerUserId null = bộ hệ thống do admin tạo). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "VocabularySets")
public class VocabularySet extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 170)
    private String slug;

    @Nationalized
    @Column(length = 500)
    private String description;

    private Integer levelId;

    private Integer topicId;

    private Long ownerUserId;

    @Column(name = "IsPublished", nullable = false)
    private boolean published;

    @Nationalized
    @Column(length = 500)
    private String coverImageUrl;

    @OneToMany(mappedBy = "set")
    @OrderBy("sortOrder ASC")
    private List<VocabularySetWord> words = new ArrayList<>();
}
