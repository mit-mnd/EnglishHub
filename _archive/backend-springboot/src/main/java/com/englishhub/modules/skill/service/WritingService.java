package com.englishhub.modules.skill.service;

import com.englishhub.integration.ai.writing.WritingEvaluator;
import com.englishhub.integration.ai.writing.WritingEvaluator.WritingFeedback;
import com.englishhub.integration.ai.writing.WritingEvaluator.WritingInput;
import com.englishhub.modules.skill.dto.SkillDtos.WritingResultResponse;
import com.englishhub.modules.skill.entity.WritingSubmission;
import com.englishhub.modules.skill.repository.WritingSubmissionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Chấm bài viết qua {@link WritingEvaluator} và lưu lịch sử (bài gốc, bản sửa, nhận xét). */
@Service
@RequiredArgsConstructor
public class WritingService {

    private final WritingEvaluator evaluator;
    private final WritingSubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public WritingResultResponse evaluate(Long userId, String text, String prompt, Integer minWords, Integer maxWords,
                                          Long lessonId, Long questionId) {
        WritingFeedback feedback = evaluator.evaluate(new WritingInput(text, prompt, minWords, maxWords));

        WritingSubmission submission = new WritingSubmission();
        submission.setUserId(userId);
        submission.setLessonId(lessonId);
        submission.setQuestionId(questionId);
        submission.setPrompt(prompt == null ? null : prompt.substring(0, Math.min(prompt.length(), 2000)));
        submission.setContent(text);
        submission.setWordCount(feedback.wordCount());
        submission.setScore(BigDecimal.valueOf(feedback.score()));
        submission.setCorrectedContent(feedback.correctedText());
        submission.setFeedbackJson(toJson(feedback));
        submission.setProvider(feedback.provider());
        submission.setStatus(WritingSubmission.Status.SCORED);
        submissionRepository.save(submission);
        return new WritingResultResponse(submission.getId(), feedback);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
