package com.englishhub.modules.progress.dto;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Dữ liệu cho dashboard học viên. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public enum Granularity {
        DAY, WEEK, MONTH
    }

    /** Khoảng thời gian của biểu đồ; khoảng càng dài thì gộp nhóm càng thô. */
    public enum DashboardRange {
        LAST_7_DAYS("7d", 7, Granularity.DAY),
        LAST_30_DAYS("30d", 30, Granularity.DAY),
        LAST_90_DAYS("90d", 90, Granularity.WEEK),
        LAST_365_DAYS("365d", 365, Granularity.MONTH);

        private final String code;
        private final int days;
        private final Granularity granularity;

        DashboardRange(String code, int days, Granularity granularity) {
            this.code = code;
            this.days = days;
            this.granularity = granularity;
        }

        @JsonValue
        public String code() {
            return code;
        }

        public int days() {
            return days;
        }

        public Granularity granularity() {
            return granularity;
        }

        public static DashboardRange fromCode(String code) {
            return Arrays.stream(values()).filter(r -> r.code.equalsIgnoreCase(code)).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "range phải là một trong: 7d, 30d, 90d, 365d"));
        }
    }

    public record Streak(int current, int longest, LocalDate lastActivityDate, boolean activeToday) {
    }

    public record TodayProgress(int studyMinutes, int goalMinutes, boolean goalReached) {
    }

    public record Totals(
            int studyMinutes,
            int lessonsCompleted,
            int wordsMastered,
            int wordsLearning,
            int wordsNew,
            int wordsDue,
            int quizzesTaken,
            BigDecimal averageQuizScore) {
    }

    /** Một điểm trên biểu đồ (ngày / tuần bắt đầu thứ Hai / tháng). */
    public record ActivityPoint(
            LocalDate periodStart,
            int studyMinutes,
            int lessonsCompleted,
            int wordsLearned,
            int wordsReviewed,
            int quizzesTaken,
            BigDecimal averageScore) {
    }

    public record NextLesson(Long id, String title, String skill, int estimatedMinutes, String status) {
    }

    public record LearningPathSummary(
            Long id,
            String name,
            String goalType,
            int totalItems,
            int completedItems,
            BigDecimal percent,
            NextLesson nextLesson) {
    }

    public record PlacementSummary(Long attemptId, BigDecimal percentage, Instant submittedAt, String levelCode,
                                   String levelName) {
    }

    public record DashboardResponse(
            DashboardRange range,
            Granularity granularity,
            LocalDate today,
            String timeZone,
            Streak streak,
            TodayProgress todayProgress,
            Totals totals,
            List<ActivityPoint> series,
            LearningPathSummary learningPath,
            PlacementSummary latestPlacement,
            int unreadNotifications) {
    }
}
