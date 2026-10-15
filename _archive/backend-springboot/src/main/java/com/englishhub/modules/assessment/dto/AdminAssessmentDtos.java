package com.englishhub.modules.assessment.dto;

import com.englishhub.common.domain.Skill;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTO quản trị ngân hàng câu hỏi và bài test. */
public final class AdminAssessmentDtos {

    private static final String URL_PATTERN = "^$|^(/media/|https://)\\S+$";

    private AdminAssessmentDtos() {
    }

    // ------------------------------------------------------------------ Câu hỏi

    public record OptionInput(
            @NotBlank @Size(max = 1000) String content,
            boolean correct,
            @Size(max = 1000) String matchText,
            @Min(1) @Max(20) Integer correctOrder) {
    }

    public record QuestionRequest(
            @NotNull QuestionType questionType,
            @NotNull Skill skill,
            Integer levelId,
            @NotBlank @Size(max = 4000) String content,
            @Size(max = 20000) String passage,
            @Size(max = 500) @Pattern(regexp = URL_PATTERN, message = "Media phải là file đã tải lên hoặc link https") String mediaUrl,
            @Size(max = 4000) String answerKey,
            @Size(max = 4000) String explanation,
            @NotNull @Min(1) @Max(5) Integer difficulty,
            @NotNull @DecimalMin("0.25") @DecimalMax("100") BigDecimal defaultPoints,
            boolean active,
            @Size(max = 12) List<@Valid OptionInput> options) {
    }

    public record OptionView(Long id, String content, boolean correct, String matchText, Integer correctOrder) {
    }

    public record AdminQuestionRow(
            Long id, QuestionType questionType, Skill skill, String levelCode, String preview, int difficulty,
            BigDecimal defaultPoints, boolean active, long testCount, Instant updatedAt) {
    }

    public record QuestionUsage(long answers, List<String> tests) {
    }

    public record AdminQuestionDetail(
            Long id, QuestionType questionType, Skill skill, Integer levelId, String content, String passage,
            String mediaUrl, String answerKey, String explanation, int difficulty, BigDecimal defaultPoints,
            boolean active, List<OptionView> options, QuestionUsage usage, Instant updatedAt) {
    }

    public record ActiveRequest(boolean active) {
    }

    // ------------------------------------------------------------------ Bài test

    public record TestRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull TestType testType,
            Long lessonId,
            Integer levelId,
            @Min(1) @Max(600) Integer durationMinutes,
            @DecimalMin("0") @DecimalMax("100") BigDecimal passingPercent,
            boolean shuffleQuestions,
            @Min(1) @Max(1000) Integer maxAttempts,
            boolean published) {
    }

    public record TestQuestionInput(@NotNull Long questionId, @DecimalMin("0.25") @DecimalMax("100") BigDecimal points) {
    }

    public record TestQuestionsRequest(@NotNull @Size(max = 200) List<@Valid TestQuestionInput> items) {
    }

    public record TestQuestionView(
            Long questionId, int sortOrder, BigDecimal points, BigDecimal effectivePoints, QuestionType questionType,
            Skill skill, String preview, boolean active) {
    }

    public record AdminTestRow(
            Long id, String title, TestType testType, Long lessonId, String lessonTitle, Integer levelId,
            Integer durationMinutes, boolean published, boolean activePlacement, long questionCount, long attemptCount,
            Instant updatedAt) {
    }

    public record AdminTestDetail(
            Long id, String title, String description, TestType testType, Long lessonId, String lessonTitle,
            Integer levelId, Integer durationMinutes, BigDecimal passingPercent, boolean shuffleQuestions,
            Integer maxAttempts, boolean published, boolean activePlacement, long attemptCount, BigDecimal totalPoints,
            List<TestQuestionView> questions, Instant updatedAt) {
    }
}
