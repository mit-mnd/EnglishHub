package com.englishhub.modules.assessment.entity;

/** Các enum của module assessment (khớp CHECK constraint trong database). */
public final class Enums {

    private Enums() {
    }

    public enum QuestionType {
        SINGLE_CHOICE, MULTIPLE_CHOICE, FILL_BLANK, MATCHING, ORDERING, SPEAKING, ESSAY;

        /** Dạng câu hỏi được chấm tự động ngay khi nộp bài. */
        public boolean isAutoGradable() {
            return this != SPEAKING && this != ESSAY;
        }
    }

    public enum TestType {
        PLACEMENT, LESSON_QUIZ, PRACTICE, MOCK_EXAM
    }

    public enum AttemptStatus {
        IN_PROGRESS, SUBMITTED, EXPIRED
    }
}
