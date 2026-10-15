package com.englishhub.modules.learningpath.dto;

import com.englishhub.modules.catalog.dto.CatalogDtos.LessonCard;
import com.englishhub.modules.catalog.dto.LevelResponse;
import com.englishhub.modules.learningpath.entity.LearningPath;
import com.englishhub.modules.learningpath.entity.LearningPathItem;
import com.englishhub.modules.user.entity.LearningGoal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LearningPathDtos {

    private LearningPathDtos() {
    }

    /** Mọi field đều tùy chọn: bỏ trống = dùng level/mục tiêu/quỹ thời gian hiện tại của học viên. */
    public record GeneratePathRequest(
            LearningGoal goalType,
            Integer levelId,
            @Min(value = 5, message = "Tối thiểu 5 phút/ngày") @Max(value = 480, message = "Tối đa 480 phút/ngày")
            Integer dailyMinutes) {

        public static GeneratePathRequest empty() {
            return new GeneratePathRequest(null, null, null);
        }
    }

    public record AddLessonRequest(@NotNull(message = "Thiếu lessonId") Long lessonId) {
    }

    public record ReorderRequest(@NotEmpty(message = "Danh sách bài trống") List<Long> itemIds) {
    }

    public record PathItemResponse(
            Long id,
            int sortOrder,
            LearningPathItem.Status status,
            boolean custom,
            LocalDate plannedDate,
            Instant completedAt,
            LessonCard lesson) {
    }

    public record LearningPathResponse(
            Long id,
            String name,
            LearningGoal goalType,
            LevelResponse level,
            LearningPath.Source source,
            LearningPath.Status status,
            int dailyMinutes,
            Instant createdAt,
            int totalItems,
            int completedItems,
            int skippedItems,
            BigDecimal percent,
            int totalMinutes,
            int remainingMinutes,
            LocalDate estimatedFinishDate,
            Long currentItemId,
            List<PathItemResponse> items) {
    }
}
