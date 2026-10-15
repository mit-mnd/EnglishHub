package com.englishhub.modules.notification.controller;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.notification.service.NotificationService;
import com.englishhub.modules.notification.service.NotificationService.NotificationResponse;
import com.englishhub.modules.notification.service.StudyReminderJob;
import com.englishhub.modules.notification.service.StudyReminderJob.RunResult;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Thông báo")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final StudyReminderJob reminderJob;

    @Operation(summary = "Danh sách thông báo (mới nhất trước)")
    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @PageableDefault(size = 10) Pageable pageable) {
        return notificationService.list(principal.id(), unreadOnly, pageable);
    }

    @Operation(summary = "Số thông báo chưa đọc (frontend gọi định kỳ)")
    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("count", notificationService.unreadCount(principal.id()));
    }

    @PostMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return notificationService.markRead(principal.id(), id);
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("updated", notificationService.markAllRead(principal.id()));
    }

    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        notificationService.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "[Admin] Chạy ngay job nhắc học (vận hành / kiểm thử)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/jobs/study-reminders")
    public RunResult runReminders() {
        return reminderJob.run();
    }
}
