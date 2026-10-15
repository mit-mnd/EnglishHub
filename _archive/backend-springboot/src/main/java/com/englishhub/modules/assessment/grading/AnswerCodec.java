package com.englishhub.modules.assessment.grading;

import com.englishhub.modules.assessment.entity.AttemptAnswer;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Chuyển {@link AnswerPayload} sang/ra 2 cột của AttemptAnswers:
 * SelectedOptionIds (JSON mảng id) và AnswerText (JSON hoặc văn bản thuần).
 */
@Component
@RequiredArgsConstructor
public class AnswerCodec {

    private static final TypeReference<List<Long>> IDS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> TEXTS = new TypeReference<>() {
    };
    private static final TypeReference<Map<Long, String>> MATCHES = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public void write(AttemptAnswer target, QuestionType type, AnswerPayload payload) {
        target.setSelectedOptionIds(null);
        target.setAnswerText(null);
        switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> target.setSelectedOptionIds(toJson(payload.selectedOptionIds()));
            case ORDERING -> target.setSelectedOptionIds(toJson(payload.orderedOptionIds()));
            case FILL_BLANK -> target.setAnswerText(toJson(payload.textAnswers()));
            case MATCHING -> target.setAnswerText(toJson(payload.matches()));
            case ESSAY, SPEAKING -> target.setAnswerText(payload.answerText());
        }
    }

    public AnswerPayload read(AttemptAnswer source, QuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE ->
                    new AnswerPayload(fromJson(source.getSelectedOptionIds(), IDS), null, null, null, null);
            case ORDERING -> new AnswerPayload(null, null, null, fromJson(source.getSelectedOptionIds(), IDS), null);
            case FILL_BLANK -> new AnswerPayload(null, fromJson(source.getAnswerText(), TEXTS), null, null, null);
            case MATCHING -> new AnswerPayload(null, null, fromJson(source.getAnswerText(), MATCHES), null, null);
            case ESSAY, SPEAKING -> new AnswerPayload(null, null, null, null, source.getAnswerText());
        };
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể ghi câu trả lời dạng JSON", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dữ liệu câu trả lời trong DB không hợp lệ", e);
        }
    }
}
