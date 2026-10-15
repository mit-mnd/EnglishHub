package com.englishhub.modules.catalog.service;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.modules.catalog.dto.CatalogDtos.CourseDetail;
import com.englishhub.modules.catalog.dto.CatalogDtos.CourseSummary;
import com.englishhub.modules.catalog.dto.CatalogDtos.LessonCard;
import com.englishhub.modules.catalog.dto.CatalogDtos.LessonDetail;
import com.englishhub.modules.catalog.dto.CatalogDtos.LessonNav;
import com.englishhub.modules.catalog.dto.CatalogDtos.ProgressInfo;
import com.englishhub.modules.catalog.dto.CatalogDtos.TranscriptSegment;
import com.englishhub.modules.catalog.dto.LevelResponse;
import com.englishhub.modules.catalog.entity.Course;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.repository.CourseRepository;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.progress.entity.LessonProgress;
import com.englishhub.modules.progress.repository.LessonProgressRepository;
import com.englishhub.modules.user.entity.LearningGoal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Khóa học & bài học phía học viên (chỉ nội dung đã publish), kèm tiến độ của người đang xem. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final Set<String> LESSON_SORTS = Set.of("title", "estimatedMinutes", "sortOrder", "createdAt");

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;

    @Transactional(readOnly = true)
    public List<CourseSummary> listCourses(Long userId, Integer levelId, LearningGoal goal) {
        List<Course> courses = courseRepository.findPublished(levelId, goal);
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Lesson>> lessonsByCourse = lessonRepository
                .findPublishedByCourseIds(courses.stream().map(Course::getId).toList()).stream()
                .collect(Collectors.groupingBy(l -> l.getCourse().getId()));
        Map<Long, LessonProgress> progress = progressByLesson(userId, lessonsByCourse.values().stream()
                .flatMap(List::stream).map(Lesson::getId).toList());
        return courses.stream()
                .map(c -> toSummary(c, lessonsByCourse.getOrDefault(c.getId(), List.of()), progress))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDetail getCourse(Long userId, Long courseId) {
        Course course = courseRepository.findByIdAndPublishedTrue(courseId)
                .orElseThrow(() -> BusinessException.notFound("khóa học", courseId));
        List<Lesson> lessons = lessonRepository.findPublishedByCourseIds(List.of(courseId));
        Map<Long, LessonProgress> progress = progressByLesson(userId, lessons.stream().map(Lesson::getId).toList());
        return new CourseDetail(toSummary(course, lessons, progress),
                lessons.stream().map(l -> LessonCard.of(l, progress.get(l.getId()))).toList());
    }

    @Transactional(readOnly = true)
    public LessonDetail getLesson(Long userId, Long lessonId) {
        Lesson lesson = lessonRepository.findPublishedById(lessonId)
                .orElseThrow(() -> BusinessException.notFound("bài học", lessonId));
        List<Lesson> siblings = lessonRepository.findPublishedByCourseIds(List.of(lesson.getCourse().getId()));
        Map<Long, LessonProgress> progress = progressByLesson(userId, siblings.stream().map(Lesson::getId).toList());

        int index = indexOf(siblings, lessonId);
        Lesson previous = index > 0 ? siblings.get(index - 1) : null;
        Lesson next = index >= 0 && index < siblings.size() - 1 ? siblings.get(index + 1) : null;

        return new LessonDetail(
                lesson.getId(), lesson.getTitle(), lesson.getSummary(), lesson.getSkill(), lesson.getContent(),
                lesson.getMediaType(), lesson.getMediaUrl(), lesson.getDurationSeconds(), lesson.getEstimatedMinutes(),
                LevelResponse.from(lesson.getLevel()), lesson.getCourse().getId(), lesson.getCourse().getTitle(),
                lesson.getTranscript().stream()
                        .map(s -> new TranscriptSegment(s.getId(), s.getStartMs(), s.getEndMs(), s.getText(), s.getTranslation()))
                        .toList(),
                ProgressInfo.of(progress.get(lessonId)),
                LessonNav.of(previous), LessonNav.of(next),
                siblings.stream().map(l -> LessonCard.of(l, progress.get(l.getId()))).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonCard> searchLessons(Long userId, Skill skill, Integer levelId, String keyword,
                                                  Pageable pageable) {
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Lesson> page = lessonRepository.search(skill, levelId, pattern,
                PageableUtils.requireSortable(pageable, LESSON_SORTS));
        Map<Long, LessonProgress> progress = progressByLesson(userId,
                page.getContent().stream().map(Lesson::getId).toList());
        return PageResponse.of(page, l -> LessonCard.of(l, progress.get(l.getId())));
    }

    private CourseSummary toSummary(Course course, List<Lesson> lessons, Map<Long, LessonProgress> progress) {
        int completed = (int) lessons.stream()
                .map(l -> progress.get(l.getId()))
                .filter(p -> p != null && p.isCompleted())
                .count();
        Set<Skill> skills = lessons.stream().map(Lesson::getSkill)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Skill.class)));
        return new CourseSummary(course.getId(), course.getTitle(), course.getSlug(), course.getDescription(),
                LevelResponse.from(course.getLevel()), course.getGoalType(), course.getThumbnailUrl(),
                lessons.size(), lessons.stream().mapToInt(Lesson::getEstimatedMinutes).sum(), completed, skills);
    }

    private Map<Long, LessonProgress> progressByLesson(Long userId, Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return progressRepository.findByUserAndLessons(userId, lessonIds).stream()
                .collect(Collectors.toMap(p -> p.getLesson().getId(), Function.identity()));
    }

    private static int indexOf(List<Lesson> lessons, Long id) {
        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
