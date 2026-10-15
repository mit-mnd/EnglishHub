package com.englishhub.modules.assessment.grading;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Câu trả lời của học viên cho một câu hỏi. Chỉ dùng field phù hợp với loại câu hỏi:
 * <ul>
 *   <li>SINGLE_CHOICE / MULTIPLE_CHOICE: {@code selectedOptionIds}</li>
 *   <li>FILL_BLANK: {@code textAnswers} (theo thứ tự blank {{1}}, {{2}}...)</li>
 *   <li>MATCHING: {@code matches} (optionId vế trái -> chuỗi vế phải đã chọn)</li>
 *   <li>ORDERING: {@code orderedOptionIds} (thứ tự học viên sắp xếp)</li>
 *   <li>ESSAY / SPEAKING: {@code answerText}</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerPayload(
        List<Long> selectedOptionIds,
        List<String> textAnswers,
        Map<Long, String> matches,
        List<Long> orderedOptionIds,
        String answerText) {

    public static AnswerPayload empty() {
        return new AnswerPayload(null, null, null, null, null);
    }
}
