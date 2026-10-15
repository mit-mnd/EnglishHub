package com.englishhub.modules.catalog.service;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.common.util.SlugUtils;
import com.englishhub.modules.assessment.repository.AssessmentRepository;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminCourseDetail;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminCourseRow;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.CourseRequest;
import com.englishhub.modules.catalog.entity.Course;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.entity.Level;
import com.englishhub.modules.catalog.repository.CourseRepository;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.cms.ContentUsageRepository;
import com.englishhub.modules.user.entity.LearningGoal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Quản trị khóa học: tìm kiếm, tạo/sửa, xuất bản, sắp xếp bài, xóa (khi chưa có học viên). */
@Service
@RequiredArgsConstructor
public class AdminCourseService {

    private static final Set<String> SORTS = Set.of("title", "sortOrder", "createdAt", "updatedAt");

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LevelRepository levelRepository;
    private final AssessmentRepository assessmentRepository;
    private final ContentUsageRepository usage;

    @Transactional(readOnly = true)
    public PageResponse<AdminCourseRow> search(String keyword, Integer levelId, LearningGoal goal, Boolean published,
                                               Pageable pageable) {
        Page<Course> page = courseRepository.adminSearch(PageableUtils.likePattern(keyword), levelId, goal, published,
                PageableUtils.requireSortable(pageable, SORTS));
        Map<Long, long[]> counts = lessonCounts(page.getContent().stream().map(Course::getId).toList());
        return PageResponse.of(page, c -> toRow(c, counts.getOrDefault(c.getId(), new long[2])));
    }

    /** Danh sách gọn mọi khóa học (cho ô chọn khóa học trong form bài học / bộ lọc). */
    @Transactional(readOnly = true)
    public List<AdminCourseRow> options() {
        List<Course> courses = courseRepository.findAllByOrderByLevelSortOrderAscSortOrderAscIdAsc();
        Map<Long, long[]> counts = lessonCounts(courses.stream().map(Course::getId).toList());
        return courses.stream().map(c -> toRow(c, counts.getOrDefault(c.getId(), new long[2]))).toList();
    }

    @Transactional(readOnly = true)
    public AdminCourseDetail get(Long id) {
        Course c = find(id);
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAscIdAsc(id);
        return new AdminCourseDetail(c.getId(), c.getTitle(), c.getSlug(), c.getDescription(), c.getLevel().getId(),
                c.getLevel().getCode(), c.getLevel().getName(), c.getGoalType(), c.getThumbnailUrl(), c.isPublished(),
                c.getSortOrder(), c.getUpdatedAt(), lessons.stream().map(AdminLessonService::toRow).toList());
    }

    @Transactional
    public AdminCourseDetail create(CourseRequest request, Long adminId) {
        Course course = new Course();
        course.setCreatedBy(adminId);
        apply(course, request);
        courseRepository.save(course);
        return get(course.getId());
    }

    @Transactional
    public AdminCourseDetail update(Long id, CourseRequest request) {
        Course course = find(id);
        apply(course, request);
        courseRepository.flush();   // cập nhật UpdatedAt trước khi trả về
        return get(id);
    }

    @Transactional
    public AdminCourseDetail setPublished(Long id, boolean published) {
        find(id).setPublished(published);
        courseRepository.flush();
        return get(id);
    }

    /** Sắp xếp lại bài trong khóa: ids là toàn bộ bài của khóa theo thứ tự mới. */
    @Transactional
    public AdminCourseDetail reorderLessons(Long id, List<Long> ids) {
        find(id);
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAscIdAsc(id);
        Map<Long, Lesson> byId = new HashMap<>();
        lessons.forEach(l -> byId.put(l.getId(), l));
        if (ids.size() != lessons.size() || !byId.keySet().containsAll(ids)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh sách bài học phải gồm đủ và đúng các bài của khóa học");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setSortOrder((i + 1) * 10);
        }
        lessonRepository.flush();
        return get(id);
    }

    /**
     * Xóa khóa học kèm toàn bộ bài học + quiz của nó. Chỉ cho phép khi chưa bài nào có dữ liệu học viên;
     * ngược lại trả CONTENT_IN_USE để admin ẩn khóa học thay vì xóa.
     */
    @Transactional
    public void delete(Long id) {
        Course course = find(id);
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAscIdAsc(id);
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        if (usage.lessonUsage(lessonIds) > 0) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE);
        }
        if (!lessonIds.isEmpty()) {
            assessmentRepository.deleteByLessonIds(lessonIds);
        }
        // Xóa bài học qua JPA trước (kèm phụ đề): các bài đang nằm trong persistence context sẽ chặn xóa khóa học
        lessonRepository.deleteAll(lessons);
        courseRepository.delete(course);
    }

    private void apply(Course course, CourseRequest r) {
        Level level = levelRepository.findById(r.levelId()).orElseThrow(() -> BusinessException.notFound("trình độ", r.levelId()));
        String slug = r.slug() == null || r.slug().isBlank()
                ? (course.getSlug() != null && r.title().equals(course.getTitle()) ? course.getSlug()
                    : SlugUtils.unique(r.title(), s -> slugTaken(s, course.getId())))
                : r.slug();
        if (slugTaken(slug, course.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Slug '" + slug + "' đã được dùng cho khóa học khác");
        }
        course.setTitle(r.title().trim());
        course.setSlug(slug);
        course.setDescription(blankToNull(r.description()));
        course.setLevel(level);
        course.setGoalType(r.goalType());
        course.setThumbnailUrl(blankToNull(r.thumbnailUrl()));
        course.setPublished(r.published());
        course.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
    }

    private boolean slugTaken(String slug, Long selfId) {
        return selfId == null ? courseRepository.existsBySlug(slug) : courseRepository.existsBySlugAndIdNot(slug, selfId);
    }

    private Course find(Long id) {
        return courseRepository.findWithLevelById(id).orElseThrow(() -> BusinessException.notFound("khóa học", id));
    }

    private Map<Long, long[]> lessonCounts(List<Long> courseIds) {
        Map<Long, long[]> result = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (Object[] row : lessonRepository.countByCourse(courseIds)) {
                result.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
            }
        }
        return result;
    }

    private static AdminCourseRow toRow(Course c, long[] counts) {
        return new AdminCourseRow(c.getId(), c.getTitle(), c.getSlug(), c.getLevel().getId(), c.getLevel().getCode(),
                c.getLevel().getName(), c.getGoalType(), c.isPublished(), c.getSortOrder(), counts[0], counts[1],
                c.getUpdatedAt());
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
