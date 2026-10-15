package com.englishhub.modules.vocabulary.dto;

import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.entity.Word;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class VocabularyDtos {

    private VocabularyDtos() {
    }

    public record WordResponse(
            Long id,
            String headword,
            String partOfSpeech,
            String phoneticUk,
            String phoneticUs,
            String audioUrlUk,
            String audioUrlUs,
            String meaningVi,
            String definitionEn,
            String example,
            String exampleTranslation,
            boolean saved,
            Long userWordId) {

        public static WordResponse of(Word w, UserWord saved) {
            return new WordResponse(w.getId(), w.getHeadword(), w.getPartOfSpeech(), w.getPhoneticUk(),
                    w.getPhoneticUs(), w.getAudioUrlUk(), w.getAudioUrlUs(), w.getMeaningVi(), w.getDefinitionEn(),
                    w.getExample(), w.getExampleTranslation(), saved != null, saved == null ? null : saved.getId());
        }
    }

    /**
     * @param matchedHeadword từ gốc tìm được (vd. tra "souvenirs" -> "souvenir")
     * @param source          LOCAL (kho nội bộ) | DICTIONARY_API | NONE (không tìm thấy)
     */
    public record LookupResponse(String query, String matchedHeadword, String source, List<WordResponse> entries) {
    }

    public record SaveWordRequest(
            @NotNull(message = "Thiếu wordId") Long wordId,
            Long sourceLessonId,
            @Size(max = 1000) String contextSentence,
            @Size(max = 500) String note) {
    }

    public record UpdateNoteRequest(@Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    public record UserWordResponse(
            Long id,
            WordResponse word,
            UserWord.Source source,
            UserWord.Status status,
            String note,
            String contextSentence,
            Long sourceLessonId,
            String sourceLessonTitle,
            Instant dueAt,
            Instant createdAt) {
    }
}
