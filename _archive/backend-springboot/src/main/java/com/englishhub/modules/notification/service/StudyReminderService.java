package com.englishhub.modules.notification.service;

import com.englishhub.modules.notification.entity.Notification.Type;
import com.englishhub.modules.notification.repository.NotificationRepository;
import com.englishhub.modules.notification.service.ReminderPolicy.Context;
import com.englishhub.modules.notification.service.ReminderPolicy.Reminder;
import com.englishhub.modules.progress.repository.DashboardQueryRepository;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.DailyRow;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.StreakRow;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import com.englishhub.modules.vocabulary.entity.UserWord.Status;
import com.englishhub.modules.vocabulary.repository.UserWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;

/** Thu thập dữ liệu của một học viên, hỏi {@link ReminderPolicy} và tạo thông báo nhắc học (mỗi loại 1 lần/ngày). */
@Service
@RequiredArgsConstructor
public class StudyReminderService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final DashboardQueryRepository dashboardQueries;
    private final UserWordRepository userWordRepository;

    /** Mỗi học viên xử lý trong transaction riêng: lỗi ở một người không chặn những người khác. */
    @Transactional
    public int processUser(Long userId, Instant now) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return 0;
        }
        ZoneId zone = ZoneId.of(user.getTimeZone());
        ZonedDateTime local = now.atZone(zone);
        LocalDate today = local.toLocalDate();
        Instant dayStart = today.atStartOfDay(zone).toInstant();

        int studyMinutes = dashboardQueries.findDailyActivities(userId, today, today).stream()
                .mapToInt(DailyRow::studySeconds).sum() / 60;
        StreakRow streak = dashboardQueries.findStreak(userId).orElse(null);
        boolean studiedToday = streak != null && today.equals(streak.lastActivityDate());
        boolean streakAlive = streak != null && streak.lastActivityDate() != null
                && !streak.lastActivityDate().isBefore(today.minusDays(1));
        int dueCards = (int) userWordRepository.countDue(userId, now, EnumSet.of(Status.NEW, Status.SUSPENDED));

        Context context = new Context(local.toLocalTime(), user.isReminderEnabled(), user.getReminderTime(),
                notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(userId, Type.STUDY_REMINDER, dayStart),
                notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(userId, Type.STREAK, dayStart),
                studyMinutes, user.getDailyGoalMinutes(), streakAlive ? streak.current() : 0, studiedToday, dueCards);

        List<Reminder> reminders = ReminderPolicy.decide(context);
        reminders.forEach(r -> notificationService.create(userId, r.type(), r.title(), r.message(), r.link()));
        return reminders.size();
    }
}
