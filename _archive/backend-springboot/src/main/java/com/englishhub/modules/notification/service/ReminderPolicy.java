package com.englishhub.modules.notification.service;

import com.englishhub.modules.notification.entity.Notification.Type;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Quyết định nhắc học (hàm thuần - không truy cập DB, để unit test).
 *
 * <ul>
 *   <li><b>STUDY_REMINDER</b>: đã tới giờ nhắc học viên chọn, hôm nay chưa đạt mục tiêu thời gian học,
 *       chưa nhắc lần nào trong ngày. Nếu job tạm dừng và chạy lại muộn vẫn nhắc (không cần trúng đúng khung 15 phút).</li>
 *   <li><b>STREAK</b>: đang có chuỗi >= 2 ngày, hôm nay chưa học, đã từ 20:00 trở đi, chưa cảnh báo trong ngày.</li>
 * </ul>
 * Học viên tắt "nhắc học hằng ngày" thì không gửi loại nào.
 */
public final class ReminderPolicy {

    static final LocalTime STREAK_WARNING_FROM = LocalTime.of(20, 0);

    private ReminderPolicy() {
    }

    public record Context(
            LocalTime localNow,
            boolean reminderEnabled,
            LocalTime reminderTime,
            boolean studyReminderSentToday,
            boolean streakWarningSentToday,
            int studyMinutesToday,
            int dailyGoalMinutes,
            int currentStreak,
            boolean studiedToday,
            int dueCards) {
    }

    public record Reminder(Type type, String title, String message, String link) {
    }

    public static List<Reminder> decide(Context c) {
        List<Reminder> result = new ArrayList<>();
        if (!c.reminderEnabled()) {
            return result;
        }
        if (c.reminderTime() != null && !c.localNow().isBefore(c.reminderTime()) && !c.studyReminderSentToday()
                && c.studyMinutesToday() < c.dailyGoalMinutes()) {
            int remaining = c.dailyGoalMinutes() - c.studyMinutesToday();
            result.add(c.dueCards() > 0
                    ? new Reminder(Type.STUDY_REMINDER, "Đến giờ học rồi! ⏰",
                            "Bạn có " + c.dueCards() + " thẻ từ vựng cần ôn và còn " + remaining
                                    + " phút để đạt mục tiêu hôm nay.", "/app/vocabulary/review")
                    : new Reminder(Type.STUDY_REMINDER, "Đến giờ học rồi! ⏰",
                            "Còn " + remaining + " phút nữa là đạt mục tiêu hôm nay. Học tiếp lộ trình nhé!", "/app/path"));
        }
        if (c.currentStreak() >= 2 && !c.studiedToday() && !c.localNow().isBefore(STREAK_WARNING_FROM)
                && !c.streakWarningSentToday()) {
            result.add(new Reminder(Type.STREAK, "Đừng để mất chuỗi " + c.currentStreak() + " ngày! 🔥",
                    "Hôm nay bạn chưa học. Chỉ cần ôn vài thẻ từ vựng để giữ chuỗi ngày học.",
                    c.dueCards() > 0 ? "/app/vocabulary/review" : "/app/vocabulary"));
        }
        return result;
    }
}
