package com.englishhub.modules.vocabulary.dto;

import com.englishhub.modules.vocabulary.entity.Word;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** DTO quản trị kho từ và bộ từ vựng hệ thống. */
public final class AdminVocabularyDtos {

    public static final String POS_PATTERN =
            "noun|verb|adjective|adverb|pronoun|preposition|conjunction|interjection|phrase|other";
    private static final String URL_PATTERN = "^$|^(/media/|https://)\\S+$";
    private static final String SLUG_PATTERN = "^$|^[a-z0-9]+(-[a-z0-9]+)*$";
    private static final String URL_MESSAGE = "Phải là file đã tải lên hoặc link https";

    private AdminVocabularyDtos() {
    }

    public record WordRequest(
            @NotBlank @Size(max = 100) String headword,
            @NotBlank @Pattern(regexp = POS_PATTERN, message = "Từ loại không hợp lệ") String partOfSpeech,
            @Size(max = 100) String phoneticUk,
            @Size(max = 100) String phoneticUs,
            @Size(max = 500) @Pattern(regexp = URL_PATTERN, message = URL_MESSAGE) String audioUrlUk,
            @Size(max = 500) @Pattern(regexp = URL_PATTERN, message = URL_MESSAGE) String audioUrlUs,
            @NotBlank @Size(max = 500) String meaningVi,
            @Size(max = 1000) String definitionEn,
            @Size(max = 1000) String example,
            @Size(max = 1000) String exampleTranslation,
            @Size(max = 500) @Pattern(regexp = URL_PATTERN, message = URL_MESSAGE) String imageUrl,
            Integer levelId) {
    }

    public record AdminWord(
            Long id, String headword, String partOfSpeech, String phoneticUk, String phoneticUs, String audioUrlUk,
            String audioUrlUs, String meaningVi, String definitionEn, String example, String exampleTranslation,
            String imageUrl, Integer levelId, Word.Source source, long learnerCount, Instant updatedAt) {
    }

    public record SetRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 160) @Pattern(regexp = SLUG_PATTERN, message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
            @Size(max = 500) String description,
            Integer levelId,
            @Size(max = 500) @Pattern(regexp = URL_PATTERN, message = URL_MESSAGE) String coverImageUrl,
            boolean published) {
    }

    public record SetWordsRequest(@NotNull @Size(max = 500) List<@NotNull Long> wordIds) {
    }

    public record AdminSetRow(Long id, String name, String slug, Integer levelId, boolean published, long wordCount,
                              Instant updatedAt) {
    }

    public record SetWordView(Long wordId, String headword, String partOfSpeech, String phonetic, String meaningVi,
                              int sortOrder) {
    }

    public record AdminSetDetail(Long id, String name, String slug, String description, Integer levelId,
                                 String coverImageUrl, boolean published, Instant updatedAt, List<SetWordView> words) {
    }
}
