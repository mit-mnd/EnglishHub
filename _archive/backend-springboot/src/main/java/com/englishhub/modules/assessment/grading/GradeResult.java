package com.englishhub.modules.assessment.grading;

import java.math.BigDecimal;

/**
 * Kết quả chấm một câu.
 *
 * @param correct null nếu câu cần chấm thủ công/AI (ESSAY, SPEAKING)
 * @param score   điểm đạt được (có thể là điểm một phần), null nếu chưa chấm
 */
public record GradeResult(Boolean correct, BigDecimal score) {

    public static GradeResult pending() {
        return new GradeResult(null, null);
    }

    public boolean isGraded() {
        return score != null;
    }
}
