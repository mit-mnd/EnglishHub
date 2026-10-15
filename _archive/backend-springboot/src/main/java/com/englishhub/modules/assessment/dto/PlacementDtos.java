package com.englishhub.modules.assessment.dto;

import com.englishhub.modules.assessment.entity.Enums.AttemptStatus;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.common.domain.Skill;
import com.englishhub.modules.assessment.grading.AnswerPayload;
import com.englishhub.modules.catalog.dto.LevelResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response của luồng làm bài kiểm tra xếp lớp. */
public final class PlacementDtos {

    private PlacementDtos() {
    }

    // ---------- Request ----------

    public record AnswerRequest(
            @NotNull(message = "Thiếu questionId") Long questionId,
            @Size(max = 20) List<Long> selectedOptionIds,
            @Size(max = 20) List<@Size(max = 200) String> textAnswers,
            @Size(max = 20) Map<Long, @Size(max = 1000) String> matches,
            @Size(max = 20) List<Long> orderedOptionIds,
            @Size(max = 10000) String answerText) {

        public AnswerPayload toPayload() {
            return new AnswerPayload(selectedOptionIds, textAnswers, matches, orderedOptionIds, answerText);
        }
    }

    /** Có thể gửi kèm các câu trả lời cuối cùng chưa kịp autosave. */
    public record SubmitAttemptRequest(@Valid @Size(max = 200) List<AnswerRequest> answers) {
    }

    // ---------- Response ----------

    public record PlacementTestInfoResponse(
            Long testId,
            String title,
            String description,
            Integer durationMinutes,
            int totalQuestions,
            /** Bài đang làm dở (nếu có) để frontend hiển thị nút "Tiếp tục". */
            Long inProgressAttemptId,
            AttemptSummaryResponse latestResult) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OptionView(Long id, String content) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionView(
            Long id,
            int number,
            QuestionType type,
            Skill skill,
            String content,
            String passage,
            String mediaUrl,
            BigDecimal points,
            List<OptionView> options,
            /** MATCHING: danh sách vế phải (đã xáo trộn). */
            List<String> matchChoices,
            /** FILL_BLANK: số ô trống. */
            Integer blankCount,
            /** SPEAKING: câu mẫu cần đọc. */
            String referenceText,
            /** ESSAY: yêu cầu độ dài (số từ). */
            Integer minWords,
            Integer maxWords) {
    }

    public record AttemptResponse(
            Long attemptId,
            Long testId,
            String title,
            AttemptStatus status,
            Instant startedAt,
            Instant expiresAt,
            /** Giờ server để client tính đồng hồ đếm ngược chính xác. */
            Instant serverTime,
            List<QuestionView> questions,
            /** Câu trả lời đã lưu, key = questionId. */
            Map<Long, AnswerPayload> savedAnswers) {
    }

    public record SkillScore(Skill skill, BigDecimal score, BigDecimal maxScore, BigDecimal percentage) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewOptionView(Long id, String content, boolean correct, String matchText, Integer correctOrder) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionReview(
            Long questionId,
            int number,
            QuestionType type,
            Skill skill,
            String content,
            String passage,
            List<ReviewOptionView> options,
            List<List<String>> acceptedAnswers,
            AnswerPayload yourAnswer,
            Boolean correct,
            BigDecimal score,
            BigDecimal points,
            String explanation) {
    }

    public record PlacementResultResponse(
            Long attemptId,
            AttemptStatus status,
            Instant startedAt,
            Instant submittedAt,
            long durationSeconds,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            int correctCount,
            int totalQuestions,
            LevelResponse level,
            List<SkillScore> skillScores,
            List<QuestionReview> review) {
    }

    public record AttemptSummaryResponse(
            Long attemptId,
            AttemptStatus status,
            Instant startedAt,
            Instant submittedAt,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            LevelResponse level) {
    }
}
