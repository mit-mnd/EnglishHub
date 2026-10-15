package com.englishhub.modules.progress.service;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.modules.catalog.dto.CatalogDtos.LessonNav;
import com.englishhub.modules.catalog.dto.CatalogDtos.ProgressInfo;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.progress.dto.LessonProgressDtos.CompleteRequest;
import com.englishhub.modules.progress.dto.LessonProgressDtos.CompleteResponse;
import com.englishhub.modules.progress.dto.LessonProgressDtos.HeartbeatRequest;
import com.englishhub.modules.progress.entity.LessonProgress;
import com.englishhub.modules.progress.event.LessonProgressEvents.LessonCompleted;
import com.englishhub.modules.progress.event.LessonProgressEvents.LessonStarted;
import com.englishhub.modules.progress.repository.LessonProgressRepository;
import com.englishhub.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Ghi nhận tiến độ bài học:
 * <ul>
 *   <li>heartbeat: cộng thời gian học, lưu vị trí media, % đã học -> cập nhật thời gian học trong ngày & streak.</li>
 *   <li>complete: đánh dấu hoàn thành (chỉ lần đầu mới tính vào "bài hoàn thành" trong ngày).</li>
 * </ul>
 * Các thao tác khóa dòng user để 2 tab cùng mở 1 bài không tạo 2 bản ghi tiến độ.
 */
@Service
@RequiredArgsConstructor
public class LessonProgressService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public ProgressInfo heartbeat(Long userId, Long lessonId, HeartbeatRequest request) {
        LessonProgress progress = loadOrStart(userId, lessonId);
        progress.setTimeSpentSeconds(progress.getTimeSpentSeconds() + request.seconds());
        if (request.positionMs() != null) {
            progress.setLastPositionMs(request.positionMs());
        }
        if (!progress.isCompleted() && request.progressPercent() != null
                && request.progressPercent().compareTo(progress.getProgressPercent()) > 0) {
            progress.setProgressPercent(request.progressPercent());
        }
        if (request.seconds() > 0) {
            activityService.recordStudyTime(userId, request.seconds());
        }
        return ProgressInfo.of(progress);
    }

    @Transactional
    public CompleteResponse complete(Long userId, Long lessonId, CompleteRequest request) {
        LessonProgress progress = loadOrStart(userId, lessonId);
        boolean firstCompletion = !progress.isCompleted();
        if (firstCompletion) {
            progress.setStatus(LessonProgress.Status.COMPLETED);
            progress.setCompletedAt(clock.instant());
            progress.setProgressPercent(HUNDRED);
            activityService.recordLessonCompleted(userId);
            events.publishEvent(new LessonCompleted(userId, lessonId));
        }
        BigDecimal score = request == null ? null : request.score();
        if (score != null && (progress.getBestScore() == null || score.compareTo(progress.getBestScore()) > 0)) {
            progress.setBestScore(score);
        }
        return new CompleteResponse(ProgressInfo.of(progress), firstCompletion, nextInCourse(progress.getLesson()));
    }

    private LessonProgress loadOrStart(Long userId, Long lessonId) {
        Lesson lesson = lessonRepository.findPublishedById(lessonId)
                .orElseThrow(() -> BusinessException.notFound("bài học", lessonId));
        userRepository.findForUpdate(userId).orElseThrow(() -> BusinessException.notFound("người dùng", userId));
        return progressRepository.findByUserIdAndLessonId(userId, lessonId).orElseGet(() -> {
            LessonProgress created = new LessonProgress();
            created.setUserId(userId);
            created.setLesson(lesson);
            created.setStartedAt(Instant.now(clock));
            progressRepository.save(created);
            events.publishEvent(new LessonStarted(userId, lessonId));
            return created;
        });
    }

    private LessonNav nextInCourse(Lesson lesson) {
        List<Lesson> siblings = lessonRepository.findPublishedByCourseIds(List.of(lesson.getCourse().getId()));
        for (int i = 0; i < siblings.size() - 1; i++) {
            if (siblings.get(i).getId().equals(lesson.getId())) {
                return LessonNav.of(siblings.get(i + 1));
            }
        }
        return null;
    }
}
