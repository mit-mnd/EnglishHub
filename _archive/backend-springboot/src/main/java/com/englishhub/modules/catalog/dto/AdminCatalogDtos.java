package com.englishhub.modules.catalog.dto;

import com.englishhub.common.domain.Skill;
import com.englishhub.modules.catalog.entity.Lesson.MediaType;
import com.englishhub.modules.user.entity.LearningGoal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** DTO quản trị khóa học / bài học (thấy cả nội dung chưa xuất bản). */
public final class AdminCatalogDtos {

    /** Slug do admin tự nhập (để trống = sinh từ tiêu đề). */
    public static final String SLUG_PATTERN = "^$|^[a-z0-9]+(-[a-z0-9]+)*$";
    /** Media: file tải lên (/media/...) hoặc link https. */
    public static final String URL_PATTERN = "^$|^(/media/|https://)\\S+$";

    private AdminCatalogDtos() {
    }

    public record CourseRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 160) @Pattern(regexp = SLUG_PATTERN, message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
            @Size(max = 4000) String description,
            @NotNull Integer levelId,
            @NotNull LearningGoal goalType,
            @Size(max = 500) @Pattern(regexp = URL_PATTERN, message = "Ảnh phải là file đã tải lên hoặc link https") String thumbnailUrl,
            boolean published,
            @Min(0) @Max(100000) Integer sortOrder) {
    }

    public record AdminCourseRow(
            Long id, String title, String slug, Integer levelId, String levelCode, String levelName,
            LearningGoal goalType, boolean published, int sortOrder, long lessonCount, long publishedLessons,
            Instant updatedAt) {
    }

    public record AdminCourseDetail(
            Long id, String title, String slug, String description, Integer levelId, String levelCode, String levelName,
            LearningGoal goalType, String thumbnailUrl, boolean published, int sortOrder, Instant updatedAt,
            List<AdminLessonRow> lessons) {
    }

    public record LessonRequest(
            @NotNull Long courseId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 160) @Pattern(regexp = SLUG_PATTERN, message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
            @NotNull Skill skill,
            Integer levelId,
            @Size(max = 500) String summary,
            @Size(max = 200000) String content,
            @NotNull MediaType mediaType,
            @Size(max = 500) @Pattern(regexp = URL_PATTERN, message = "Media phải là file đã tải lên hoặc link https") String mediaUrl,
            @Min(1) @Max(36000) Integer durationSeconds,
            @NotNull @Min(1) @Max(600) Integer estimatedMinutes,
            @Min(0) @Max(100000) Integer sortOrder,
            boolean published) {
    }

    public record AdminLessonRow(
            Long id, Long courseId, String courseTitle, String title, String slug, Skill skill, String levelCode,
            String levelName, MediaType mediaType, int estimatedMinutes, int sortOrder, boolean published,
            boolean coursePublished, Instant updatedAt) {
    }

    public record QuizRef(Long id, String title, int questionCount, boolean published) {
    }

    public record AdminLessonDetail(
            Long id, Long courseId, String courseTitle, String title, String slug, Skill skill, Integer levelId,
            String summary, String content, MediaType mediaType, String mediaUrl, Integer durationSeconds,
            int estimatedMinutes, int sortOrder, boolean published, Instant updatedAt,
            List<TranscriptSegment> transcript, List<QuizRef> quizzes) {
    }

    public record SegmentInput(
            @NotNull @Min(0) Integer startMs,
            @NotNull @Min(1) Integer endMs,
            @NotBlank @Size(max = 1000) String text,
            @Size(max = 1000) String translation) {
    }

    public record TranscriptRequest(@NotNull @Size(max = 500) List<@Valid SegmentInput> segments) {
    }

    public record ReorderRequest(@NotEmpty @Size(max = 500) List<@NotNull Long> ids) {
    }

    public record PublishRequest(boolean published) {
    }

    public record TranscriptSegment(Long id, int startMs, int endMs, String text, String translation) {
    }
}
