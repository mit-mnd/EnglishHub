package com.englishhub.modules.catalog.service;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.common.util.SlugUtils;
import com.englishhub.modules.assessment.entity.Assessment;
import com.englishhub.modules.assessment.repository.AssessmentRepository;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminLessonDetail;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminLessonRow;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.LessonRequest;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.QuizRef;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.SegmentInput;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.TranscriptSegment;
import com.englishhub.modules.catalog.entity.Course;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.entity.Lesson.MediaType;
import com.englishhub.modules.catalog.entity.LessonTranscriptSegment;
import com.englishhub.modules.catalog.entity.Level;
import com.englishhub.modules.catalog.repository.CourseRepository;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.cms.ContentUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.englishhub.modules.catalog.service.AdminCourseService.blankToNull;

/** Quản trị bài học: nội dung, media, phụ đề có mốc thời gian, xuất bản, xóa. */
@Service
@RequiredArgsConstructor
public class AdminLessonService {

    private static final Set<String> SORTS = Set.of("title", "sortOrder", "createdAt", "updatedAt");
    /** Cho phép phụ đề kết thúc trễ hơn độ dài media khai báo một chút (độ dài thường làm tròn). */
    private static final int TRANSCRIPT_TOLERANCE_MS = 2000;

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final LevelRepository levelRepository;
    private final AssessmentRepository assessmentRepository;
    private final ContentUsageRepository usage;

    @Transactional(readOnly = true)
    public PageResponse<AdminLessonRow> search(Long courseId, Skill skill, Integer levelId, Boolean published,
                                               String keyword, Pageable pageable) {
        return PageResponse.of(lessonRepository.adminSearch(courseId, skill, levelId, published,
                PageableUtils.likePattern(keyword), PageableUtils.requireSortable(pageable, SORTS)), AdminLessonService::toRow);
    }

    @Transactional(readOnly = true)
    public AdminLessonDetail get(Long id) {
        Lesson l = find(id);
        List<Assessment> quizzes = assessmentRepository.findByLessonIdOrderByIdAsc(id);
        Map<Long, Integer> questionCounts = new HashMap<>();
        if (!quizzes.isEmpty()) {
            for (Object[] row : assessmentRepository.countQuestions(quizzes.stream().map(Assessment::getId).toList())) {
                questionCounts.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }
        return new AdminLessonDetail(l.getId(), l.getCourse().getId(), l.getCourse().getTitle(), l.getTitle(), l.getSlug(),
                l.getSkill(), l.getLevel().getId(), l.getSummary(), l.getContent(), l.getMediaType(), l.getMediaUrl(),
                l.getDurationSeconds(), l.getEstimatedMinutes(), l.getSortOrder(), l.isPublished(), l.getUpdatedAt(),
                l.getTranscript().stream()
                        .map(s -> new TranscriptSegment(s.getId(), s.getStartMs(), s.getEndMs(), s.getText(), s.getTranslation()))
                        .toList(),
                quizzes.stream()
                        .map(q -> new QuizRef(q.getId(), q.getTitle(), questionCounts.getOrDefault(q.getId(), 0), q.isPublished()))
                        .toList());
    }

    @Transactional
    public AdminLessonDetail create(LessonRequest request) {
        Lesson lesson = new Lesson();
        apply(lesson, request);
        if (request.sortOrder() == null) {
            // Bài mới mặc định nằm cuối khóa học
            lesson.setSortOrder(lessonRepository.maxSortOrder(request.courseId()) + 10);
        }
        lessonRepository.save(lesson);
        return get(lesson.getId());
    }

    @Transactional
    public AdminLessonDetail update(Long id, LessonRequest request) {
        Lesson lesson = find(id);
        apply(lesson, request);
        lessonRepository.flush();
        return get(id);
    }

    @Transactional
    public AdminLessonDetail setPublished(Long id, boolean published) {
        find(id).setPublished(published);
        lessonRepository.flush();
        return get(id);
    }

    /**
     * Thay toàn bộ phụ đề của bài. Các câu phải theo thứ tự thời gian, EndMs > StartMs và không vượt độ dài media.
     */
    @Transactional
    public AdminLessonDetail replaceTranscript(Long id, List<SegmentInput> segments) {
        Lesson lesson = find(id);
        if (!segments.isEmpty() && lesson.getMediaType() == MediaType.NONE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bài học không có audio/video nên không thể thêm phụ đề");
        }
        validateTranscript(segments, lesson.getDurationSeconds());

        // Xóa trước rồi mới thêm: Hibernate chèn bản ghi mới trước khi xóa bản ghi mồ côi,
        // sẽ vi phạm UNIQUE (LessonId, SortOrder) nếu không flush ở giữa.
        lesson.getTranscript().clear();
        lessonRepository.flush();
        for (int i = 0; i < segments.size(); i++) {
            SegmentInput in = segments.get(i);
            LessonTranscriptSegment s = new LessonTranscriptSegment();
            s.setLesson(lesson);
            s.setSortOrder(i + 1);
            s.setStartMs(in.startMs());
            s.setEndMs(in.endMs());
            s.setText(in.text().trim());
            s.setTranslation(blankToNull(in.translation()));
            lesson.getTranscript().add(s);
        }
        lessonRepository.flush();
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        Lesson lesson = find(id);
        if (usage.lessonUsage(List.of(id)) > 0) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE);
        }
        assessmentRepository.deleteByLessonIds(List.of(id));
        lessonRepository.delete(lesson);
    }

    static void validateTranscript(List<SegmentInput> segments, Integer durationSeconds) {
        int previousStart = -1;
        for (int i = 0; i < segments.size(); i++) {
            SegmentInput s = segments.get(i);
            String row = "Câu phụ đề #" + (i + 1) + ": ";
            if (s.endMs() <= s.startMs()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, row + "thời điểm kết thúc phải sau thời điểm bắt đầu");
            }
            if (s.startMs() < previousStart) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, row + "các câu phải sắp theo thứ tự thời gian");
            }
            if (durationSeconds != null && s.endMs() > durationSeconds * 1000 + TRANSCRIPT_TOLERANCE_MS) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, row + "vượt quá độ dài media (" + durationSeconds + " giây)");
            }
            previousStart = s.startMs();
        }
    }

    private void apply(Lesson lesson, LessonRequest r) {
        Course course = courseRepository.findWithLevelById(r.courseId())
                .orElseThrow(() -> BusinessException.notFound("khóa học", r.courseId()));
        Level level = r.levelId() == null ? course.getLevel()
                : levelRepository.findById(r.levelId()).orElseThrow(() -> BusinessException.notFound("trình độ", r.levelId()));
        String mediaUrl = blankToNull(r.mediaUrl());
        if (r.mediaType() != MediaType.NONE && mediaUrl == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bài có audio/video cần đường dẫn media");
        }
        if (r.mediaType() == MediaType.NONE && !lesson.getTranscript().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hãy xóa phụ đề trước khi bỏ audio/video của bài");
        }

        boolean sameCourse = lesson.getCourse() != null && lesson.getCourse().getId().equals(course.getId());
        String slug = r.slug() == null || r.slug().isBlank()
                ? (sameCourse && r.title().equals(lesson.getTitle()) ? lesson.getSlug()
                    : SlugUtils.unique(r.title(), s -> slugTaken(course.getId(), s, lesson.getId())))
                : r.slug();
        if (slugTaken(course.getId(), slug, lesson.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Slug '" + slug + "' đã có trong khóa học này");
        }

        lesson.setCourse(course);
        lesson.setLevel(level);
        lesson.setTitle(r.title().trim());
        lesson.setSlug(slug);
        lesson.setSkill(r.skill());
        lesson.setSummary(blankToNull(r.summary()));
        lesson.setContent(blankToNull(r.content()));
        lesson.setMediaType(r.mediaType());
        lesson.setMediaUrl(r.mediaType() == MediaType.NONE ? null : mediaUrl);
        lesson.setDurationSeconds(r.mediaType() == MediaType.NONE ? null : r.durationSeconds());
        lesson.setEstimatedMinutes(r.estimatedMinutes());
        if (r.sortOrder() != null) {
            lesson.setSortOrder(r.sortOrder());
        }
        lesson.setPublished(r.published());
    }

    private boolean slugTaken(Long courseId, String slug, Long selfId) {
        return selfId == null ? lessonRepository.existsByCourseIdAndSlug(courseId, slug)
                : lessonRepository.existsByCourseIdAndSlugAndIdNot(courseId, slug, selfId);
    }

    private Lesson find(Long id) {
        return lessonRepository.findWithCourseById(id).orElseThrow(() -> BusinessException.notFound("bài học", id));
    }

    static AdminLessonRow toRow(Lesson l) {
        return new AdminLessonRow(l.getId(), l.getCourse().getId(), l.getCourse().getTitle(), l.getTitle(), l.getSlug(),
                l.getSkill(), l.getLevel().getCode(), l.getLevel().getName(), l.getMediaType(), l.getEstimatedMinutes(),
                l.getSortOrder(), l.isPublished(), l.getCourse().isPublished(), l.getUpdatedAt());
    }
}
