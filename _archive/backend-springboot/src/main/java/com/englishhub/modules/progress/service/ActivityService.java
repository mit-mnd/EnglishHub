package com.englishhub.modules.progress.service;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.modules.progress.event.LessonProgressEvents.StreakMilestone;
import com.englishhub.modules.progress.repository.ActivityWriteRepository;
import com.englishhub.modules.progress.repository.ActivityWriteRepository.DailyDelta;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * Điểm ghi nhận hoạt động học tập duy nhất (bài học, flashcard, quiz, trò chơi...).
 * Cập nhật DailyActivities (theo ngày ở múi giờ học viên) và chuỗi ngày học; phát sự kiện khi chuỗi đạt mốc.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    static final Set<Integer> STREAK_MILESTONES = Set.of(3, 7, 14, 21, 30, 50, 100, 200, 365);

    private final ActivityWriteRepository writes;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void recordStudyTime(Long userId, int seconds) {
        record(userId, new DailyDelta(seconds, 0, 0, 0, 0, BigDecimal.ZERO));
    }

    @Transactional
    public void recordLessonCompleted(Long userId) {
        record(userId, new DailyDelta(0, 1, 0, 0, 0, BigDecimal.ZERO));
    }

    @Transactional
    public void record(Long userId, DailyDelta delta) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("người dùng", userId));
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimeZone()));
        writes.addDailyActivity(userId, today, delta, user.getDailyGoalMinutes() * 60);
        writes.touchStreak(userId, today)
                .stream()
                .filter(STREAK_MILESTONES::contains)
                .forEach(days -> events.publishEvent(new StreakMilestone(userId, days)));
    }
}
