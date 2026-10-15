package com.englishhub.modules.vocabulary.entity;

import com.englishhub.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/** Mục từ trong kho từ vựng (admin nhập hoặc cache từ dịch vụ từ điển ngoài). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Words")
public class Word extends AuditableEntity {

    public enum Source { ADMIN, DICTIONARY_API, USER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String headword;

    /** noun, verb, adjective... (chữ thường, khớp CHECK constraint). */
    @Column(nullable = false, length = 20)
    private String partOfSpeech = "other";

    @Nationalized
    @Column(length = 100)
    private String phoneticUk;

    @Nationalized
    @Column(length = 100)
    private String phoneticUs;

    @Nationalized
    @Column(length = 500)
    private String audioUrlUk;

    @Nationalized
    @Column(length = 500)
    private String audioUrlUs;

    /** Chuỗi rỗng = chưa có nghĩa tiếng Việt (từ lấy tự động từ từ điển Anh-Anh). */
    @Nationalized
    @Column(nullable = false, length = 500)
    private String meaningVi = "";

    @Nationalized
    @Column(length = 1000)
    private String definitionEn;

    @Nationalized
    @Column(length = 1000)
    private String example;

    @Nationalized
    @Column(length = 1000)
    private String exampleTranslation;

    @Nationalized
    @Column(length = 500)
    private String imageUrl;

    private Integer levelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.ADMIN;
}
