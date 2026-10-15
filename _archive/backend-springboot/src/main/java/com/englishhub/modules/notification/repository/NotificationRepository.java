package com.englishhub.modules.notification.repository;

import com.englishhub.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Đã gửi loại thông báo này kể từ đầu ngày (theo múi giờ học viên) chưa - để mỗi loại tối đa 1 lần/ngày. */
    boolean existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(Long userId, Notification.Type type, Instant since);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Notification n set n.read = true, n.readAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.read = false""")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
