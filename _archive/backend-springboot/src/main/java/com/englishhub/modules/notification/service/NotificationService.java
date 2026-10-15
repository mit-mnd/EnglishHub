package com.englishhub.modules.notification.service;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.modules.assessment.event.PlacementCompletedEvent;
import com.englishhub.modules.notification.entity.Notification;
import com.englishhub.modules.notification.entity.Notification.Type;
import com.englishhub.modules.notification.repository.NotificationRepository;
import com.englishhub.modules.progress.event.LessonProgressEvents.StreakMilestone;
import com.englishhub.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Thông báo trong ứng dụng: tạo (từ job nhắc học hoặc sự kiện), đọc, đánh dấu đã đọc. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public record NotificationResponse(Long id, Type type, String title, String message, String link, boolean read,
                                       Instant readAt, Instant createdAt) {

        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.isRead(),
                    n.getReadAt(), n.getCreatedAt());
        }
    }

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public Notification create(Long userId, Type type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(truncate(title, 200));
        n.setMessage(truncate(message, 1000));
        n.setLink(link);
        return repository.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? repository.findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return PageResponse.of(page, NotificationResponse::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long id) {
        Notification n = require(userId, id);
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(clock.instant());
        }
        return NotificationResponse.of(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, clock.instant());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(require(userId, id));
    }

    // ------------------------------------------------------------------ thông báo theo sự kiện

    @EventListener
    public void onStreakMilestone(StreakMilestone event) {
        create(event.userId(), Type.ACHIEVEMENT, "Chuỗi " + event.days() + " ngày học liên tục! 🔥",
                "Tuyệt vời! Bạn đã học " + event.days() + " ngày liên tiếp. Giữ vững phong độ nhé!", "/app/dashboard");
    }

    @EventListener
    public void onPlacementCompleted(PlacementCompletedEvent event) {
        String level = userRepository.findWithDetailsById(event.userId())
                .map(u -> u.getCurrentLevel() == null ? null : u.getCurrentLevel().getName())
                .orElse(null);
        create(event.userId(), Type.PATH_UPDATED, "Lộ trình học đã sẵn sàng",
                (level != null ? "Bạn được xếp trình độ " + level + ". " : "")
                        + "Xem lộ trình được gợi ý theo kết quả bài kiểm tra.", "/app/path");
    }

    private Notification require(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> BusinessException.notFound("thông báo", id));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
