package com.englishhub.modules.assessment.dto;

import com.englishhub.common.domain.Skill;
import com.englishhub.integration.ai.writing.WritingEvaluator.WritingFeedback;
import com.englishhub.modules.assessment.dto.PlacementDtos.AttemptResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.QuestionReview;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import com.englishhub.modules.assessment.grading.AnswerPayload;
import com.englishhub.modules.skill.dto.SkillDtos.SpeakingResultResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bài tập / quiz gắn với bài học: chấm từng câu ngay khi trả lời (học qua phản hồi tức thì). */
public final class ExerciseDtos {

    private ExerciseDtos() {
    }

    public record ExerciseSummary(
            Long testId,
            String title,
            String description,
            TestType testType,
            int questionCount,
            Integer durationMinutes,
            BigDecimal passingPercent,
            Set<Skill> skills,
            int attempts,
            BigDecimal bestPercentage,
            boolean passed,
            Long inProgressAttemptId) {
    }

    /**
     * Câu trả lời cần chấm. SPEAKING: gửi speakingSubmissionId (đã thu âm & chấm qua /speaking/submissions);
     * ESSAY: gửi answerText; các dạng khác như Placement Test.
     */
    public record CheckAnswerRequest(
            @NotNull(message = "Thiếu questionId") Long questionId,
            @Size(max = 20) List<Long> selectedOptionIds,
            @Size(max = 20) List<@Size(max = 200) String> textAnswers,
            @Size(max = 20) Map<Long, @Size(max = 1000) String> matches,
            @Size(max = 20) List<Long> orderedOptionIds,
            @Size(max = 5000, message = "Bài viết tối đa 5000 ký tự") String answerText,
            Long speakingSubmissionId) {

        public AnswerPayload toPayload() {
            return new AnswerPayload(selectedOptionIds, textAnswers, matches, orderedOptionIds, answerText);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckResult(
            Long questionId,
            Boolean correct,
            BigDecimal score,
            BigDecimal points,
            /** Đáp án đúng + lời giải (chỉ trả sau khi đã chấm). */
            QuestionReview review,
            WritingFeedback writing,
            SpeakingResultResponse speaking) {
    }

    public record ExerciseAttemptResponse(
            AttemptResponse attempt,
            BigDecimal passingPercent,
            /** Các câu đã chấm (khi tiếp tục bài đang làm dở), key = questionId. */
            Map<Long, CheckResult> checked) {
    }

    public record ExerciseResult(
            Long attemptId,
            Long testId,
            String title,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal percentage,
            BigDecimal passingPercent,
            boolean passed,
            int correctCount,
            int answeredCount,
            int totalQuestions,
            boolean lessonCompleted,
            List<QuestionReview> review) {
    }
}
