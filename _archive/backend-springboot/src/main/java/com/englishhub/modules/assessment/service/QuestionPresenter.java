package com.englishhub.modules.assessment.service;

import com.englishhub.modules.assessment.dto.PlacementDtos.AttemptResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.OptionView;
import com.englishhub.modules.assessment.dto.PlacementDtos.QuestionReview;
import com.englishhub.modules.assessment.dto.PlacementDtos.QuestionView;
import com.englishhub.modules.assessment.dto.PlacementDtos.ReviewOptionView;
import com.englishhub.modules.assessment.entity.Assessment;
import com.englishhub.modules.assessment.entity.AssessmentQuestion;
import com.englishhub.modules.assessment.entity.AttemptAnswer;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.entity.QuestionOption;
import com.englishhub.modules.assessment.entity.TestAttempt;
import com.englishhub.modules.assessment.grading.AnswerCodec;
import com.englishhub.modules.assessment.grading.AnswerGrader;
import com.englishhub.modules.assessment.grading.AnswerPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Chuyển câu hỏi/bài làm sang DTO, dùng chung cho Placement Test và bài tập trong bài học.
 * Đề gửi xuống client KHÔNG chứa đáp án (trừ câu mẫu SPEAKING - là nội dung cần đọc).
 */
@Component
@RequiredArgsConstructor
public class QuestionPresenter {

    /** Yêu cầu độ dài bài viết (ESSAY), lưu trong AnswerKey dạng {"minWords":80,"maxWords":120}. */
    public record EssayConfig(Integer minWords, Integer maxWords) {
    }

    private final AnswerGrader grader;
    private final AnswerCodec codec;
    private final ObjectMapper objectMapper;

    public AttemptResponse attemptResponse(TestAttempt attempt, Assessment test, List<AssessmentQuestion> questions,
                                           Instant now) {
        List<AssessmentQuestion> ordered = new ArrayList<>(questions);
        if (test.isShuffleQuestions()) {
            // Seed theo attemptId: thứ tự cố định khi học viên tải lại trang
            Collections.shuffle(ordered, new Random(attempt.getId()));
        }
        List<QuestionView> views = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            views.add(view(ordered.get(i), i + 1, attempt.getId()));
        }
        Map<Long, AnswerPayload> saved = new LinkedHashMap<>();
        for (AttemptAnswer answer : attempt.getAnswers()) {
            Question q = answer.getQuestion();
            saved.put(q.getId(), codec.read(answer, q.getQuestionType()));
        }
        return new AttemptResponse(attempt.getId(), test.getId(), test.getTitle(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getExpiresAt(), now, views, saved);
    }

    public QuestionView view(AssessmentQuestion aq, int number, long seed) {
        Question q = aq.getQuestion();
        QuestionType type = q.getQuestionType();
        List<OptionView> options = switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE, ORDERING, MATCHING ->
                    q.getOptions().stream().map(o -> new OptionView(o.getId(), o.getContent())).toList();
            default -> null;
        };
        List<String> matchChoices = null;
        if (type == QuestionType.MATCHING) {
            matchChoices = new ArrayList<>(q.getOptions().stream().map(QuestionOption::getMatchText).toList());
            Collections.shuffle(matchChoices, new Random(seed * 31 + q.getId()));
        }
        EssayConfig essay = type == QuestionType.ESSAY ? essayConfig(q) : null;
        return new QuestionView(q.getId(), number, type, q.getSkill(), q.getContent(), q.getPassage(),
                q.getMediaUrl(), aq.effectivePoints(), options, matchChoices,
                type == QuestionType.FILL_BLANK ? grader.blankCount(q) : null,
                type == QuestionType.SPEAKING ? q.getAnswerKey() : null,
                essay == null ? null : essay.minWords(), essay == null ? null : essay.maxWords());
    }

    public QuestionReview review(AssessmentQuestion aq, int number, AttemptAnswer answer) {
        Question q = aq.getQuestion();
        return new QuestionReview(
                q.getId(), number, q.getQuestionType(), q.getSkill(), q.getContent(), q.getPassage(),
                q.getOptions().isEmpty() ? null : q.getOptions().stream()
                        .map(o -> new ReviewOptionView(o.getId(), o.getContent(), o.isCorrect(), o.getMatchText(),
                                o.getCorrectOrder()))
                        .toList(),
                q.getQuestionType() == QuestionType.FILL_BLANK ? grader.acceptedBlankAnswers(q) : null,
                answer != null ? codec.read(answer, q.getQuestionType()) : null,
                answer != null ? answer.getCorrect() : Boolean.FALSE,
                answer != null && answer.getScore() != null ? answer.getScore() : BigDecimal.ZERO,
                aq.effectivePoints(),
                q.getExplanation());
    }

    public EssayConfig essayConfig(Question q) {
        if (q.getAnswerKey() == null || q.getAnswerKey().isBlank()) {
            return new EssayConfig(null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(q.getAnswerKey());
            return new EssayConfig(node.hasNonNull("minWords") ? node.get("minWords").asInt() : null,
                    node.hasNonNull("maxWords") ? node.get("maxWords").asInt() : null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new EssayConfig(null, null);
        }
    }
}
