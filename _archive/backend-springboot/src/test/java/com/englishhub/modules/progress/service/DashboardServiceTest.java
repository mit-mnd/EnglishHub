package com.englishhub.modules.progress.service;

import com.englishhub.modules.progress.dto.DashboardDtos.ActivityPoint;
import com.englishhub.modules.progress.dto.DashboardDtos.Granularity;
import com.englishhub.modules.progress.dto.DashboardDtos.Streak;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.DailyRow;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.StreakRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);   // thứ Tư

    @Test
    void dailySeriesFillsGapsWithZero() {
        List<DailyRow> rows = List.of(
                row(TODAY.minusDays(6), 1500, 1, new BigDecimal("80")),
                row(TODAY, 90, 2, new BigDecimal("150")));

        List<ActivityPoint> series = DashboardService.buildSeries(rows, TODAY.minusDays(6), TODAY, Granularity.DAY);

        assertThat(series).hasSize(7);
        assertThat(series.get(0).studyMinutes()).isEqualTo(25);
        assertThat(series.get(0).averageScore()).isEqualByComparingTo("80");
        assertThat(series.get(3).studyMinutes()).isZero();
        assertThat(series.get(3).averageScore()).isNull();
        assertThat(series.get(6).averageScore()).isEqualByComparingTo("75");
    }

    @Test
    void weeklySeriesStartsOnMondayAndSumsDays() {
        List<DailyRow> rows = List.of(
                row(LocalDate.of(2026, 10, 12), 600, 0, BigDecimal.ZERO),    // thứ Hai
                row(LocalDate.of(2026, 10, 14), 1200, 0, BigDecimal.ZERO),   // thứ Tư cùng tuần
                row(LocalDate.of(2026, 10, 5), 300, 0, BigDecimal.ZERO));    // tuần trước

        List<ActivityPoint> series = DashboardService.buildSeries(rows, LocalDate.of(2026, 10, 5), TODAY, Granularity.WEEK);

        assertThat(series).extracting(ActivityPoint::periodStart)
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
        assertThat(series).extracting(ActivityPoint::studyMinutes).containsExactly(5, 30);
    }

    @Test
    void streakIsAliveOnlyIfLastActivityWasTodayOrYesterday() {
        assertThat(DashboardService.toStreak(new StreakRow(5, 9, TODAY), TODAY))
                .isEqualTo(new Streak(5, 9, TODAY, true));
        assertThat(DashboardService.toStreak(new StreakRow(5, 9, TODAY.minusDays(1)), TODAY).current()).isEqualTo(5);
        assertThat(DashboardService.toStreak(new StreakRow(5, 9, TODAY.minusDays(2)), TODAY).current()).isZero();
        assertThat(DashboardService.toStreak(null, TODAY)).isEqualTo(new Streak(0, 0, null, false));
    }

    private static DailyRow row(LocalDate date, int seconds, int quizzes, BigDecimal totalScore) {
        return new DailyRow(date, seconds, 0, 0, 0, quizzes, totalScore);
    }
}
