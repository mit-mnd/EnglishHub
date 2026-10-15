package com.englishhub.modules.learningpath.service;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.modules.assessment.service.PlacementService;
import com.englishhub.modules.assessment.service.PlacementService.PlacementSnapshot;
import com.englishhub.modules.catalog.dto.CatalogDtos.LessonCard;
import com.englishhub.modules.catalog.dto.LevelResponse;
import com.englishhub.modules.catalog.entity.Course;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.entity.Level;
import com.englishhub.modules.catalog.repository.CourseRepository;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.learningpath.dto.LearningPathDtos.GeneratePathRequest;
import com.englishhub.modules.learningpath.dto.LearningPathDtos.LearningPathResponse;
import com.englishhub.modules.learningpath.dto.LearningPathDtos.PathItemResponse;
import com.englishhub.modules.learningpath.entity.LearningPath;
import com.englishhub.modules.learningpath.entity.LearningPathItem;
import com.englishhub.modules.learningpath.entity.LearningPathItem.Status;
import com.englishhub.modules.learningpath.repository.LearningPathRepository;
import com.englishhub.modules.progress.entity.LessonProgress;
import com.englishhub.modules.progress.repository.LessonProgressRepository;
import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lộ trình học cá nhân hóa.
 *
 * <p><b>Thuật toán sinh lộ trình</b> (level L, mục tiêu G, kỹ năng yếu W từ bài xếp lớp):</p>
 * <ol>
 *   <li>Lấy khóa học đã publish ở level L và level kế tiếp (để có bài "vươn lên"),
 *       khóa đúng mục tiêu G xếp trước khóa GENERAL.</li>
 *   <li>Lấy bài học theo thứ tự khóa học -> thứ tự bài; trong mỗi nhóm 4 bài, bài thuộc kỹ năng yếu W được đẩy lên trước.</li>
 *   <li>Không có bài ở L -> lùi dần xuống các level thấp hơn. Tối đa {@value #MAX_ITEMS} bài.</li>
 *   <li>Bài đã học/đã xong trước đó giữ nguyên trạng thái; xếp lịch ngày học theo số phút mỗi ngày.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathService {

    static final int MAX_ITEMS = 30;

    private static final Map<LearningGoal, String> GOAL_NAMES = Map.of(
            LearningGoal.GENERAL, "Tổng quát",
            LearningGoal.COMMUNICATION, "Giao tiếp",
            LearningGoal.TOEIC, "TOEIC",
            LearningGoal.IELTS, "IELTS");

    private final LearningPathRepository pathRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LevelRepository levelRepository;
    private final LessonProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final PlacementService placementService;
    private final Clock clock;

    // ------------------------------------------------------------------ đọc

    @Transactional(readOnly = true)
    public Optional<LearningPathResponse> getActive(Long userId) {
        return activePath(userId).map(this::toResponse);
    }

    // ------------------------------------------------------------------ sinh lộ trình

    @Transactional
    public LearningPathResponse generate(Long userId, GeneratePathRequest request) {
        LearningPath path = build(userId, request == null ? GeneratePathRequest.empty() : request)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_LESSONS_FOR_PATH));
        return toResponse(path);
    }

    /**
     * Gọi khi học viên vừa làm xong bài xếp lớp: tạo lộ trình nếu chưa có.
     * Không ném lỗi nghiệp vụ, vì exception ở đây sẽ làm rollback cả transaction nộp bài.
     */
    @Transactional
    public void generateIfAbsent(Long userId) {
        if (pathRepository.existsByUserIdAndStatus(userId, LearningPath.Status.ACTIVE)) {
            return;
        }
        if (build(userId, GeneratePathRequest.empty()).isEmpty()) {
            log.info("Chưa có bài học phù hợp để tạo lộ trình cho userId={}", userId);
        }
    }

    private Optional<LearningPath> build(Long userId, GeneratePathRequest request) {
        User user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("người dùng", userId));
        List<Level> levels = levelRepository.findAllByOrderBySortOrderAsc();
        Optional<PlacementSnapshot> snapshot = placementService.latestSnapshot(userId);

        LearningGoal goal = request.goalType() != null ? request.goalType() : user.getLearningGoal();
        Level level = request.levelId() != null
                ? levels.stream().filter(l -> l.getId().equals(request.levelId())).findFirst()
                        .orElseThrow(() -> BusinessException.notFound("level", request.levelId()))
                : (user.getCurrentLevel() != null ? user.getCurrentLevel() : levels.get(0));
        int dailyMinutes = request.dailyMinutes() != null ? request.dailyMinutes() : user.getDailyGoalMinutes();
        // Tự chọn level khác => lộ trình theo mục tiêu, không dựa vào kết quả xếp lớp
        boolean fromPlacement = request.levelId() == null && snapshot.isPresent();
        Set<Skill> weakSkills = fromPlacement ? snapshot.get().weakSkills() : Set.of();

        List<Lesson> lessons = selectLessons(level, goal, levels, weakSkills);
        if (lessons.isEmpty()) {
            return Optional.empty();
        }

        activePath(userId).ifPresent(old -> {
            old.setStatus(LearningPath.Status.ARCHIVED);
            // Hibernate chạy INSERT trước UPDATE trong cùng một lần flush -> phải flush việc lưu trữ lộ trình cũ
            // trước, nếu không filtered unique index "1 lộ trình ACTIVE / học viên" sẽ báo trùng.
            pathRepository.flush();
        });

        LearningPath path = new LearningPath();
        path.setUserId(userId);
        path.setName("Lộ trình " + GOAL_NAMES.get(goal) + " · " + level.getName());
        path.setGoalType(goal);
        path.setLevel(level);
        path.setSource(fromPlacement ? LearningPath.Source.PLACEMENT : LearningPath.Source.GOAL);
        path.setDailyMinutes(dailyMinutes);
        path.setPlacementAttemptId(fromPlacement ? snapshot.get().attemptId() : null);

        Map<Long, LessonProgress> progress = progressByLesson(userId, lessons.stream().map(Lesson::getId).toList());
        int order = 1;
        for (Lesson lesson : lessons) {
            LearningPathItem item = new LearningPathItem();
            item.setLesson(lesson);
            item.setSortOrder(order++);
            applyExistingProgress(item, progress.get(lesson.getId()));
            path.addItem(item);
        }
        refresh(path, today(user));

        // Đồng bộ hồ sơ với lựa chọn mới của học viên
        user.setLearningGoal(goal);
        user.setDailyGoalMinutes(dailyMinutes);
        return Optional.of(pathRepository.save(path));
    }

    private List<Lesson> selectLessons(Level level, LearningGoal goal, List<Level> levels, Set<Skill> weakSkills) {
        List<LearningGoal> goals = goal == LearningGoal.GENERAL
                ? List.of(LearningGoal.GENERAL) : List.of(goal, LearningGoal.GENERAL);
        int index = indexOfLevel(levels, level);

        List<Lesson> result = new ArrayList<>(lessonsOfLevel(level, goals, weakSkills));
        if (index + 1 < levels.size()) {
            result.addAll(lessonsOfLevel(levels.get(index + 1), goals, weakSkills));
        }
        // Không có nội dung ở level hiện tại -> lùi dần xuống level thấp hơn
        for (int i = index - 1; result.isEmpty() && i >= 0; i--) {
            result.addAll(lessonsOfLevel(levels.get(i), goals, weakSkills));
        }
        return result.size() > MAX_ITEMS ? result.subList(0, MAX_ITEMS) : result;
    }

    private List<Lesson> lessonsOfLevel(Level level, List<LearningGoal> goals, Set<Skill> weakSkills) {
        List<Course> courses = new ArrayList<>(courseRepository.findForPath(level.getId(), goals));
        if (courses.isEmpty()) {
            return List.of();
        }
        courses.sort(Comparator.comparingInt(c -> goals.indexOf(c.getGoalType())));   // đúng mục tiêu trước
        Map<Long, Integer> courseRank = new HashMap<>();
        for (int i = 0; i < courses.size(); i++) {
            courseRank.put(courses.get(i).getId(), i);
        }
        List<Lesson> lessons = new ArrayList<>(lessonRepository.findPublishedByCourseIds(courseRank.keySet()));
        lessons.sort(Comparator.<Lesson>comparingInt(l -> courseRank.get(l.getCourse().getId()))
                .thenComparing(Lesson::getSortOrder)
                .thenComparing(Lesson::getId));
        return PathPlanner.prioritizeWeakSkills(lessons, Lesson::getSkill, weakSkills);
    }

    // ------------------------------------------------------------------ điều chỉnh lộ trình

    @Transactional
    public LearningPathResponse addLesson(Long userId, Long lessonId) {
        LearningPath path = requireActive(userId);
        Lesson lesson = lessonRepository.findPublishedById(lessonId)
                .orElseThrow(() -> BusinessException.notFound("bài học", lessonId));
        if (path.findItemByLesson(lessonId).isPresent()) {
            throw new BusinessException(ErrorCode.LESSON_ALREADY_IN_PATH);
        }
        LearningPathItem item = new LearningPathItem();
        item.setLesson(lesson);
        item.setCustom(true);
        item.setSortOrder(path.getItems().stream().mapToInt(LearningPathItem::getSortOrder).max().orElse(0) + 1);
        applyExistingProgress(item, progressRepository.findByUserIdAndLessonId(userId, lessonId).orElse(null));
        path.addItem(item);
        refresh(path, today(userId));
        return toResponse(path);
    }

    @Transactional
    public LearningPathResponse removeItem(Long userId, Long itemId) {
        LearningPath path = requireActive(userId);
        LearningPathItem item = requireItem(path, itemId);
        path.getItems().remove(item);   // orphanRemoval -> DELETE
        renumber(path.getItems());
        refresh(path, today(userId));
        return toResponse(path);
    }

    @Transactional
    public LearningPathResponse skipItem(Long userId, Long itemId) {
        LearningPath path = requireActive(userId);
        LearningPathItem item = requireItem(path, itemId);
        if (item.getStatus() == Status.COMPLETED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bài học đã hoàn thành, không thể bỏ qua");
        }
        item.setStatus(Status.SKIPPED);
        refresh(path, today(userId));
        return toResponse(path);
    }

    @Transactional
    public LearningPathResponse unskipItem(Long userId, Long itemId) {
        LearningPath path = requireActive(userId);
        LearningPathItem item = requireItem(path, itemId);
        if (item.getStatus() == Status.SKIPPED) {
            applyExistingProgress(item, progressRepository.findByUserIdAndLessonId(userId, item.getLesson().getId()).orElse(null));
            refresh(path, today(userId));
        }
        return toResponse(path);
    }

    /** Sắp xếp lại: itemIds phải là hoán vị đầy đủ các item của lộ trình. */
    @Transactional
    public LearningPathResponse reorder(Long userId, List<Long> itemIds) {
        LearningPath path = requireActive(userId);
        Set<Long> current = path.getItems().stream().map(LearningPathItem::getId).collect(Collectors.toSet());
        if (itemIds.size() != current.size() || !current.equals(new HashSet<>(itemIds))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh sách sắp xếp không khớp với các bài trong lộ trình");
        }
        Map<Long, LearningPathItem> byId = path.getItems().stream()
                .collect(Collectors.toMap(LearningPathItem::getId, Function.identity()));
        for (int i = 0; i < itemIds.size(); i++) {
            byId.get(itemIds.get(i)).setSortOrder(i + 1);
        }
        refresh(path, today(userId));
        return toResponse(path);
    }

    // ------------------------------------------------------------------ phản ứng với tiến độ bài học

    @Transactional
    public void markLessonStarted(Long userId, Long lessonId) {
        activePath(userId).ifPresent(path -> path.findItemByLesson(lessonId)
                .filter(item -> item.getStatus() == Status.LOCKED || item.getStatus() == Status.AVAILABLE)
                .ifPresent(item -> {
                    item.setStatus(Status.IN_PROGRESS);
                    refresh(path, today(userId));
                }));
    }

    /** Hoàn thành mọi bài thì lộ trình vẫn ACTIVE (100%) để học viên thấy kết quả và chủ động tạo lộ trình mới. */
    @Transactional
    public void markLessonCompleted(Long userId, Long lessonId) {
        activePath(userId).ifPresent(path -> path.findItemByLesson(lessonId)
                .filter(item -> item.getStatus() != Status.COMPLETED)
                .ifPresent(item -> {
                    item.setStatus(Status.COMPLETED);
                    item.setCompletedAt(clock.instant());
                    refresh(path, today(userId));
                }));
    }

    // ------------------------------------------------------------------ helpers

    /** Sắp lại theo SortOrder, tính lại trạng thái "học tiếp/sắp tới" và lịch ngày học của các bài chưa xong. */
    private static void refresh(LearningPath path, LocalDate today) {
        path.getItems().sort(Comparator.comparing(LearningPathItem::getSortOrder));
        PathPlanner.refreshStatuses(path.getItems());
        List<LearningPathItem> pending = path.getItems().stream().filter(i -> !i.isDone()).toList();
        List<LocalDate> dates = PathPlanner.planDates(pending, i -> i.getLesson().getEstimatedMinutes(),
                path.getDailyMinutes(), today);
        for (int i = 0; i < pending.size(); i++) {
            pending.get(i).setPlannedDate(dates.get(i));
        }
    }

    private static void applyExistingProgress(LearningPathItem item, LessonProgress progress) {
        if (progress == null) {
            item.setStatus(Status.LOCKED);
        } else if (progress.isCompleted()) {
            item.setStatus(Status.COMPLETED);
            item.setCompletedAt(progress.getCompletedAt());
        } else {
            item.setStatus(Status.IN_PROGRESS);
        }
    }

    private static void renumber(List<LearningPathItem> items) {
        items.sort(Comparator.comparing(LearningPathItem::getSortOrder));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setSortOrder(i + 1);
        }
    }

    private Optional<LearningPath> activePath(Long userId) {
        return pathRepository.findFirstByUserIdAndStatus(userId, LearningPath.Status.ACTIVE);
    }

    private LearningPath requireActive(Long userId) {
        return activePath(userId).orElseThrow(() -> new BusinessException(ErrorCode.LEARNING_PATH_NOT_FOUND));
    }

    private static LearningPathItem requireItem(LearningPath path, Long itemId) {
        return path.findItem(itemId).orElseThrow(() -> BusinessException.notFound("mục lộ trình", itemId));
    }

    private LocalDate today(Long userId) {
        return today(userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("người dùng", userId)));
    }

    private LocalDate today(User user) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimeZone()));
    }

    private static int indexOfLevel(List<Level> levels, Level level) {
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).getId().equals(level.getId())) {
                return i;
            }
        }
        return 0;
    }

    private Map<Long, LessonProgress> progressByLesson(Long userId, List<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return progressRepository.findByUserAndLessons(userId, lessonIds).stream()
                .collect(Collectors.toMap(p -> p.getLesson().getId(), Function.identity()));
    }

    private LearningPathResponse toResponse(LearningPath path) {
        List<LearningPathItem> items = path.getItems().stream()
                .sorted(Comparator.comparing(LearningPathItem::getSortOrder)).toList();
        Map<Long, LessonProgress> progress = progressByLesson(path.getUserId(),
                items.stream().map(i -> i.getLesson().getId()).toList());

        int completed = (int) items.stream().filter(i -> i.getStatus() == Status.COMPLETED).count();
        int skipped = (int) items.stream().filter(i -> i.getStatus() == Status.SKIPPED).count();
        int total = items.size();
        int totalMinutes = items.stream().mapToInt(i -> i.getLesson().getEstimatedMinutes()).sum();
        int remainingMinutes = items.stream().filter(i -> !i.isDone()).mapToInt(i -> i.getLesson().getEstimatedMinutes()).sum();
        BigDecimal percent = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((completed + skipped) * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        LocalDate finish = items.stream().filter(i -> !i.isDone()).map(LearningPathItem::getPlannedDate)
                .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        Long currentItemId = items.stream()
                .filter(i -> i.getStatus() == Status.IN_PROGRESS || i.getStatus() == Status.AVAILABLE)
                .map(LearningPathItem::getId).findFirst().orElse(null);

        return new LearningPathResponse(path.getId(), path.getName(), path.getGoalType(),
                LevelResponse.from(path.getLevel()), path.getSource(), path.getStatus(), path.getDailyMinutes(),
                path.getCreatedAt(), total, completed, skipped, percent, totalMinutes, remainingMinutes, finish,
                currentItemId,
                items.stream().map(i -> new PathItemResponse(i.getId(), i.getSortOrder(), i.getStatus(), i.isCustom(),
                        i.getPlannedDate(), i.getCompletedAt(),
                        LessonCard.of(i.getLesson(), progress.get(i.getLesson().getId())))).toList());
    }
}
