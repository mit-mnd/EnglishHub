package com.englishhub.modules.progress.event;

/**
 * Sự kiện tiến độ bài học, phát đồng bộ trong cùng transaction.
 * Module learningpath lắng nghe để cập nhật trạng thái lộ trình (không phụ thuộc ngược vào progress).
 */
public final class LessonProgressEvents {

    private LessonProgressEvents() {
    }

    public record LessonStarted(Long userId, Long lessonId) {
    }

    public record LessonCompleted(Long userId, Long lessonId) {
    }

    /** Chuỗi ngày học vừa đạt một mốc (3, 7, 14, 30...). */
    public record StreakMilestone(Long userId, int days) {
    }
}
