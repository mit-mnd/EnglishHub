package com.englishhub.modules.skill.dto;

import com.englishhub.integration.ai.speech.PronunciationScorer.WordScore;
import com.englishhub.integration.ai.writing.WritingEvaluator.WritingFeedback;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SkillDtos {

    private SkillDtos() {
    }

    /** Phần text đi kèm file ghi âm (multipart). Cần questionId HOẶC referenceText. */
    public record SpeakingRequest(
            Long questionId,
            Long lessonId,
            @Size(max = 300, message = "Câu mẫu tối đa 300 ký tự") String referenceText,
            @Size(max = 1000) String clientTranscript) {
    }

    public record SpeakingResultResponse(
            Long id,
            String referenceText,
            String recognizedText,
            String provider,
            boolean estimated,
            BigDecimal pronunciationScore,
            BigDecimal accuracyScore,
            BigDecimal fluencyScore,
            BigDecimal completenessScore,
            List<WordScore> words,
            List<String> tips,
            String audioUrl,
            Instant createdAt) {
    }

    public record WritingRequest(
            @NotBlank(message = "Bạn chưa viết nội dung") @Size(max = 5000, message = "Bài viết tối đa 5000 ký tự") String text,
            @Size(max = 2000) String prompt,
            @Min(1) @Max(1000) Integer minWords,
            @Min(1) @Max(2000) Integer maxWords,
            Long lessonId) {
    }

    public record WritingResultResponse(Long submissionId, WritingFeedback feedback) {
    }
}
