package com.englishhub.integration.ai.writing;

import java.util.List;

/**
 * Cổng chấm bài viết. Bản mặc định chấm theo quy tắc ({@link RuleBasedWritingEvaluator});
 * có thể thay bằng bộ chấm dùng mô hình ngôn ngữ lớn mà không sửa nghiệp vụ.
 */
public interface WritingEvaluator {

    WritingFeedback evaluate(WritingInput input);

    /** minWords/maxWords: yêu cầu độ dài của đề (có thể null). */
    record WritingInput(String text, String prompt, Integer minWords, Integer maxWords) {
    }

    /**
     * Một lỗi/gợi ý, vị trí tính theo ký tự trong văn bản gốc [start, end).
     * type: SPELLING | GRAMMAR | CAPITALIZATION | PUNCTUATION | STYLE. suggestion null = chỉ nhắc nhở.
     */
    record Issue(int start, int end, String type, String original, String suggestion, String message) {
    }

    /** Điểm thành phần 0-100: hoàn thành yêu cầu, ngữ pháp & chính tả, từ vựng, mạch lạc. */
    record Criteria(int taskAchievement, int grammar, int vocabulary, int coherence) {
    }

    record WritingFeedback(
            String provider,
            int score,
            Criteria criteria,
            int wordCount,
            int sentenceCount,
            List<Issue> issues,
            String correctedText,
            List<String> strengths,
            List<String> improvements) {
    }
}
