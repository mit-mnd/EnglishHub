package com.englishhub.modules.vocabulary.srs;

import com.englishhub.modules.vocabulary.entity.UserWord.Status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lặp lại ngắt quãng theo SM-2 (SuperMemo 2), điều chỉnh cho 4 nút tự đánh giá.
 *
 * <pre>
 * Nút    q   Kết quả
 * AGAIN  1   Quên: học lại sau 10 phút, repetitions = 0 (đang ở REVIEW/MASTERED thì tính 1 lần quên)
 * HARD   3   Khó:  lần 1 -> 1 ngày, lần 2 -> 3 ngày, sau đó I × 1,2
 * GOOD   4   Được: lần 1 -> 1 ngày, lần 2 -> 6 ngày, sau đó I × EF
 * EASY   5   Dễ:   lần 1 -> 4 ngày, lần 2 -> 8 ngày, sau đó I × EF × 1,3
 *
 * EF' = max(1,3; EF + 0,1 - (5 - q) × (0,08 + (5 - q) × 0,02))
 * Trạng thái: I < 6 ngày -> LEARNING, 6..20 -> REVIEW, >= 21 -> MASTERED ("đã thuộc")
 * </pre>
 * Khoảng cách mới luôn dài hơn khoảng cách cũ ít nhất 1 ngày (trừ AGAIN) để từ không bị "kẹt".
 */
public final class Sm2Scheduler {

    public enum Grade {
        AGAIN(1), HARD(3), GOOD(4), EASY(5);

        final int quality;

        Grade(int quality) {
            this.quality = quality;
        }

        public int quality() {
            return quality;
        }
    }

    public record CardState(BigDecimal easeFactor, int intervalDays, int repetitions, int lapseCount, Status status) {
    }

    /** @param delay thời gian tới lần ôn kế tiếp */
    public record Schedule(CardState state, Duration delay) {
    }

    public static final Duration RELEARN_DELAY = Duration.ofMinutes(10);
    static final double MIN_EASE = 1.3;
    static final int MAX_INTERVAL_DAYS = 3650;
    static final int REVIEW_THRESHOLD_DAYS = 6;
    static final int MASTERED_THRESHOLD_DAYS = 21;

    private Sm2Scheduler() {
    }

    public static Schedule schedule(CardState current, Grade grade) {
        double ef = current.easeFactor().doubleValue();
        int q = grade.quality;
        double newEf = Math.max(MIN_EASE, ef + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));

        if (grade == Grade.AGAIN) {
            boolean lapse = current.status() == Status.REVIEW || current.status() == Status.MASTERED;
            CardState next = new CardState(ease(newEf), 0, 0, current.lapseCount() + (lapse ? 1 : 0), Status.LEARNING);
            return new Schedule(next, RELEARN_DELAY);
        }

        int interval;
        if (current.repetitions() == 0) {
            interval = grade == Grade.EASY ? 4 : 1;
        } else if (current.repetitions() == 1) {
            interval = switch (grade) {
                case HARD -> 3;
                case EASY -> 8;
                default -> 6;
            };
        } else {
            double factor = switch (grade) {
                case HARD -> 1.2;
                case EASY -> newEf * 1.3;
                default -> newEf;
            };
            interval = Math.max(current.intervalDays() + 1, (int) Math.round(current.intervalDays() * factor));
        }
        interval = Math.min(interval, MAX_INTERVAL_DAYS);

        Status status = interval >= MASTERED_THRESHOLD_DAYS ? Status.MASTERED
                : interval >= REVIEW_THRESHOLD_DAYS ? Status.REVIEW : Status.LEARNING;
        CardState next = new CardState(ease(newEf), interval, current.repetitions() + 1, current.lapseCount(), status);
        return new Schedule(next, Duration.ofDays(interval));
    }

    /** Trạng thái theo tiến độ (dùng khi bỏ tạm ẩn một từ). */
    public static Status statusFor(int intervalDays, int repetitions) {
        if (repetitions == 0) {
            return intervalDays == 0 ? Status.NEW : Status.LEARNING;
        }
        return intervalDays >= MASTERED_THRESHOLD_DAYS ? Status.MASTERED
                : intervalDays >= REVIEW_THRESHOLD_DAYS ? Status.REVIEW : Status.LEARNING;
    }

    /** Khoảng thời gian tương ứng mỗi nút - hiển thị trên nút để học viên chọn. */
    public static Map<Grade, Duration> preview(CardState current) {
        Map<Grade, Duration> result = new EnumMap<>(Grade.class);
        for (Grade g : Grade.values()) {
            result.put(g, schedule(current, g).delay());
        }
        return result;
    }

    private static BigDecimal ease(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
