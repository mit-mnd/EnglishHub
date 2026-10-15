package com.englishhub.modules.vocabulary.entity;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "VocabularySetWords")
public class VocabularySetWord extends AuditableEntity {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("setId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SetId")
    private VocabularySet set;

    @MapsId("wordId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WordId")
    private Word word;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "SetId")
        private Long setId;

        @Column(name = "WordId")
        private Long wordId;
    }
}
