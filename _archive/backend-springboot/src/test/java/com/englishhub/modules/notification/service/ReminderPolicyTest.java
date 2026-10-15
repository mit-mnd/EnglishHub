package com.englishhub.modules.notification.service;

import com.englishhub.modules.notification.entity.Notification.Type;
import com.englishhub.modules.notification.service.ReminderPolicy.Context;
import com.englishhub.modules.notification.service.ReminderPolicy.Reminder;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderPolicyTest {

    private static Context ctx(String now, boolean sent, int studied, int streak, boolean studiedToday, int due) {
        return new Context(LocalTime.parse(now), true, LocalTime.of(19, 0), sent, false, studied, 20, streak, studiedToday, due);
    }

    @Test
    void remindsAfterReminderTimeWhenGoalNotReached() {
        List<Reminder> r = ReminderPolicy.decide(ctx("19:05", false, 5, 0, true, 3));
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.type()).isEqualTo(Type.STUDY_REMINDER);
            assertThat(x.message()).contains("3 thẻ").contains("15 phút");
            assertThat(x.link()).isEqualTo("/app/vocabulary/review");
        });
    }

    @Test
    void noReminderBeforeTimeOrWhenAlreadySentOrGoalReached() {
        assertThat(ReminderPolicy.decide(ctx("18:59", false, 0, 0, false, 0))).isEmpty();
        assertThat(ReminderPolicy.decide(ctx("21:00", true, 0, 0, true, 0))).isEmpty();
        assertThat(ReminderPolicy.decide(ctx("21:00", false, 20, 0, true, 0))).isEmpty();
    }

    @Test
    void lateRunStillReminds() {
        // Job dừng vài giờ rồi chạy lại: vẫn nhắc vì chưa nhắc trong ngày
        assertThat(ReminderPolicy.decide(ctx("22:40", false, 0, 0, true, 0)))
                .extracting(Reminder::type).containsExactly(Type.STUDY_REMINDER);
    }

    @Test
    void warnsAboutStreakInTheEveningIfNotStudiedToday() {
        List<Reminder> r = ReminderPolicy.decide(ctx("20:15", true, 0, 5, false, 0));
        assertThat(r).extracting(Reminder::type).containsExactly(Type.STREAK);
        assertThat(r.get(0).title()).contains("5 ngày");

        assertThat(ReminderPolicy.decide(ctx("19:30", true, 0, 5, false, 0))).isEmpty();   // chưa tới 20:00
        assertThat(ReminderPolicy.decide(ctx("20:30", true, 0, 1, false, 0))).isEmpty();   // chuỗi 1 ngày: chưa đáng tiếc
    }

    @Test
    void disabledRemindersSendNothing() {
        Context off = new Context(LocalTime.of(21, 0), false, LocalTime.of(19, 0), false, false, 0, 20, 9, false, 4);
        assertThat(ReminderPolicy.decide(off)).isEmpty();
    }
}
