package com.englishhub.modules.catalog.dto;

import com.englishhub.common.domain.Skill;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.entity.Lesson.MediaType;
import com.englishhub.modules.progress.entity.LessonProgress;
import com.englishhub.modules.user.entity.LearningGoal;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** DTO của khóa học / bài học. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CourseSummary(
            Long id,
            String title,
            String slug,
            String description,
            LevelResponse level,
            LearningGoal goalType,
            String thumbnailUrl,
            int lessonCount,
            int totalMinutes,
            int completedLessons,
            Set<Skill> skills) {
    }

    public record CourseDetail(CourseSummary course, List<LessonCard> lessons) {
    }

    /** Thẻ bài học dùng trong danh sách (khóa học, lộ trình, tìm kiếm). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LessonCard(
            Long id,
            String title,
            String summary,
            Skill skill,
            int estimatedMinutes,
            MediaType mediaType,
            Long courseId,
            String courseTitle,
            String levelCode,
            String levelName,
            LessonProgress.Status progressStatus,
            BigDecimal progressPercent) {

        public static LessonCard of(Lesson lesson, LessonProgress progress) {
            return new LessonCard(lesson.getId(), lesson.getTitle(), lesson.getSummary(), lesson.getSkill(),
                    lesson.getEstimatedMinutes(), lesson.getMediaType(),
                    lesson.getCourse().getId(), lesson.getCourse().getTitle(),
                    lesson.getLevel().getCode(), lesson.getLevel().getName(),
                    progress == null ? null : progress.getStatus(),
                    progress == null ? null : progress.getProgressPercent());
        }
    }

    public record TranscriptSegment(Long id, int startMs, int endMs, String text, String translation) {
    }

    public record ProgressInfo(
            LessonProgress.Status status,
            BigDecimal progressPercent,
            Integer lastPositionMs,
            int timeSpentSeconds,
            BigDecimal bestScore,
            Instant completedAt) {

        public static ProgressInfo of(LessonProgress p) {
            return p == null ? null : new ProgressInfo(p.getStatus(), p.getProgressPercent(), p.getLastPositionMs(),
                    p.getTimeSpentSeconds(), p.getBestScore(), p.getCompletedAt());
        }
    }

    public record LessonNav(Long id, String title, Skill skill) {

        public static LessonNav of(Lesson lesson) {
            return lesson == null ? null : new LessonNav(lesson.getId(), lesson.getTitle(), lesson.getSkill());
        }
    }

    public record LessonDetail(
            Long id,
            String title,
            String summary,
            Skill skill,
            String content,
            MediaType mediaType,
            String mediaUrl,
            Integer durationSeconds,
            int estimatedMinutes,
            LevelResponse level,
            Long courseId,
            String courseTitle,
            List<TranscriptSegment> transcript,
            ProgressInfo progress,
            LessonNav previous,
            LessonNav next,
            List<LessonCard> courseLessons) {
    }
}
