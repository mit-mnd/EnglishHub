package com.englishhub.modules.vocabulary.srs;

import com.englishhub.modules.vocabulary.entity.UserWord.Status;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler.CardState;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler.Grade;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler.Schedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Sm2SchedulerTest {

    private static final CardState NEW = new CardState(new BigDecimal("2.50"), 0, 0, 0, Status.NEW);

    @Test
    void goodAnswersFollowClassicSm2Intervals() {
        List<Integer> intervals = new ArrayList<>();
        CardState s = NEW;
        for (int i = 0; i < 5; i++) {
            s = Sm2Scheduler.schedule(s, Grade.GOOD).state();
            intervals.add(s.intervalDays());
        }
        // 1, 6, 6×2.5=15, 15×2.5=38 (≈37.5), 38×2.5=95 - EF giữ nguyên 2,5 khi chọn GOOD (q = 4)
        assertThat(intervals).containsExactly(1, 6, 15, 38, 95);
        assertThat(s.easeFactor()).isEqualByComparingTo("2.50");
        assertThat(s.status()).isEqualTo(Status.MASTERED);
    }

    @Test
    void statusProgressesLearningReviewMastered() {
        Schedule first = Sm2Scheduler.schedule(NEW, Grade.GOOD);
        assertThat(first.state().status()).isEqualTo(Status.LEARNING);
        Schedule second = Sm2Scheduler.schedule(first.state(), Grade.GOOD);
        assertThat(second.state().status()).isEqualTo(Status.REVIEW);
        Schedule third = Sm2Scheduler.schedule(second.state(), Grade.EASY);   // 6 × 2.6 × 1.3 ≈ 20
        Schedule fourth = Sm2Scheduler.schedule(third.state(), Grade.GOOD);
        assertThat(fourth.state().status()).isEqualTo(Status.MASTERED);
    }

    @Test
    void againResetsRepetitionsCountsLapseAndRelearnsInTenMinutes() {
        CardState review = new CardState(new BigDecimal("2.50"), 15, 3, 0, Status.REVIEW);
        Schedule s = Sm2Scheduler.schedule(review, Grade.AGAIN);

        assertThat(s.delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(s.state().repetitions()).isZero();
        assertThat(s.state().lapseCount()).isEqualTo(1);
        assertThat(s.state().status()).isEqualTo(Status.LEARNING);
        assertThat(s.state().easeFactor()).isEqualByComparingTo("1.96");   // 2.5 + 0.1 - 4 × (0.08 + 0.08)

        // Quên một từ đang học (chưa REVIEW) không tính là lapse
        assertThat(Sm2Scheduler.schedule(NEW, Grade.AGAIN).state().lapseCount()).isZero();
    }

    @Test
    void easeFactorNeverDropsBelowMinimum() {
        CardState s = new CardState(new BigDecimal("1.40"), 10, 4, 2, Status.REVIEW);
        for (int i = 0; i < 5; i++) {
            s = Sm2Scheduler.schedule(s, Grade.AGAIN).state();
        }
        assertThat(s.easeFactor()).isEqualByComparingTo("1.30");
    }

    @Test
    void hardGrowsSlowlyButAlwaysAtLeastOneDay() {
        CardState s = new CardState(new BigDecimal("1.30"), 2, 3, 0, Status.LEARNING);
        Schedule hard = Sm2Scheduler.schedule(s, Grade.HARD);
        assertThat(hard.state().intervalDays()).isEqualTo(3);   // round(2 × 1.2) = 2 -> tối thiểu 2 + 1
    }

    @Test
    void previewOrdersIntervalsByDifficulty() {
        CardState s = new CardState(new BigDecimal("2.50"), 6, 2, 0, Status.REVIEW);
        var preview = Sm2Scheduler.preview(s);
        assertThat(preview.get(Grade.AGAIN)).isLessThan(preview.get(Grade.HARD));
        assertThat(preview.get(Grade.HARD)).isLessThan(preview.get(Grade.GOOD));
        assertThat(preview.get(Grade.GOOD)).isLessThan(preview.get(Grade.EASY));
    }
}
