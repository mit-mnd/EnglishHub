package com.englishhub.modules.notification.service;

import com.englishhub.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Chạy mỗi 15 phút: nhắc học theo giờ học viên chọn và cảnh báo sắp mất chuỗi ngày học.
 * (Quy mô lớn nên chia lô theo giờ nhắc / múi giờ; hiện duyệt toàn bộ học viên bật nhắc học.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyReminderJob {

    public record RunResult(int usersChecked, int notificationsCreated) {
    }

    private final UserRepository userRepository;
    private final StudyReminderService reminderService;
    private final Clock clock;

    @Scheduled(cron = "0 */15 * * * *")
    public void scheduledRun() {
        RunResult result = run();
        if (result.notificationsCreated() > 0) {
            log.info("Study reminders: checked {} users, created {} notifications",
                    result.usersChecked(), result.notificationsCreated());
        }
    }

    public RunResult run() {
        Instant now = clock.instant();
        List<Long> userIds = userRepository.findReminderCandidateIds();
        int created = 0;
        for (Long id : userIds) {
            try {
                created += reminderService.processUser(id, now);
            } catch (RuntimeException e) {
                log.warn("Không tạo được nhắc học cho userId={}: {}", id, e.getMessage());
            }
        }
        return new RunResult(userIds.size(), created);
    }
}
