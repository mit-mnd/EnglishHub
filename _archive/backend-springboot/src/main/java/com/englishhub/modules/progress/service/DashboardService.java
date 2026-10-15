package com.englishhub.modules.progress.service;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.modules.progress.dto.DashboardDtos.ActivityPoint;
import com.englishhub.modules.progress.dto.DashboardDtos.DashboardRange;
import com.englishhub.modules.progress.dto.DashboardDtos.DashboardResponse;
import com.englishhub.modules.progress.dto.DashboardDtos.Granularity;
import com.englishhub.modules.progress.dto.DashboardDtos.Streak;
import com.englishhub.modules.progress.dto.DashboardDtos.TodayProgress;
import com.englishhub.modules.progress.dto.DashboardDtos.Totals;
import com.englishhub.modules.progress.repository.DashboardQueryRepository;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.DailyRow;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.LifetimeRow;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.StreakRow;
import com.englishhub.modules.progress.repository.DashboardQueryRepository.WordCounts;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tổng hợp số liệu dashboard học viên. Mọi mốc "hôm nay" tính theo múi giờ của học viên. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardQueryRepository queries;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, DashboardRange range) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("người dùng", userId));
        ZoneId zone = ZoneId.of(user.getTimeZone());
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);

        // Lấy dư về đầu tuần/tháng để nhóm đầu tiên của biểu đồ có đủ dữ liệu
        LocalDate from = periodStart(today.minusDays(range.days() - 1L), range.granularity());
        List<DailyRow> daily = queries.findDailyActivities(userId, from, today);

        DailyRow todayRow = daily.stream().filter(d -> d.date().equals(today)).findFirst().orElse(null);
        int todayMinutes = todayRow == null ? 0 : toMinutes(todayRow.studySeconds());
        int goal = user.getDailyGoalMinutes();

        WordCounts words = queries.countWords(userId, now);
        LifetimeRow lifetime = queries.findLifetimeTotals(userId);
        Totals totals = new Totals(
                toMinutes(lifetime.studySeconds()),
                queries.countCompletedLessons(userId),
                words.mastered(), words.learning(), words.fresh(), words.due(),
                lifetime.quizzesTaken(),
                average(lifetime.totalQuizScore(), lifetime.quizzesTaken()));

        return new DashboardResponse(
                range, range.granularity(), today, zone.getId(),
                toStreak(queries.findStreak(userId).orElse(null), today),
                new TodayProgress(todayMinutes, goal, todayMinutes >= goal),
                totals,
                buildSeries(daily, from, today, range.granularity()),
                queries.findActiveLearningPath(userId).orElse(null),
                queries.findLatestPlacement(userId).orElse(null),
                queries.countUnreadNotifications(userId));
    }

    /**
     * Streak hiển thị: nếu lần học cuối là hôm nay hoặc hôm qua thì chuỗi còn hiệu lực,
     * còn lại chuỗi đã đứt (hiển thị 0) dù bảng UserStreaks chưa được job cập nhật.
     */
    static Streak toStreak(StreakRow row, LocalDate today) {
        if (row == null || row.lastActivityDate() == null) {
            return new Streak(0, row == null ? 0 : row.longest(), null, false);
        }
        boolean activeToday = row.lastActivityDate().equals(today);
        boolean alive = activeToday || row.lastActivityDate().equals(today.minusDays(1));
        return new Streak(alive ? row.current() : 0, row.longest(), row.lastActivityDate(), activeToday);
    }

    /** Gộp dữ liệu ngày thành chuỗi liên tục (ngày/tuần/tháng); khoảng không có hoạt động = 0. */
    static List<ActivityPoint> buildSeries(List<DailyRow> daily, LocalDate from, LocalDate to, Granularity granularity) {
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();   // [giây, bài, từ mới, từ ôn, quiz]
        Map<LocalDate, BigDecimal> scores = new LinkedHashMap<>();
        for (LocalDate d = periodStart(from, granularity); !d.isAfter(to); d = next(d, granularity)) {
            buckets.put(d, new long[5]);
            scores.put(d, BigDecimal.ZERO);
        }
        for (DailyRow row : daily) {
            LocalDate key = periodStart(row.date(), granularity);
            long[] b = buckets.get(key);
            if (b == null) {
                continue;
            }
            b[0] += row.studySeconds();
            b[1] += row.lessonsCompleted();
            b[2] += row.wordsLearned();
            b[3] += row.wordsReviewed();
            b[4] += row.quizzesTaken();
            scores.merge(key, row.totalQuizScore(), BigDecimal::add);
        }
        List<ActivityPoint> series = new ArrayList<>();
        buckets.forEach((start, b) -> series.add(new ActivityPoint(start, toMinutes(b[0]), (int) b[1], (int) b[2],
                (int) b[3], (int) b[4], average(scores.get(start), (int) b[4]))));
        return series;
    }

    static LocalDate periodStart(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate next(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case DAY -> date.plusDays(1);
            case WEEK -> date.plusWeeks(1);
            case MONTH -> date.plusMonths(1);
        };
    }

    private static int toMinutes(long seconds) {
        return (int) Math.round(seconds / 60.0);
    }

    private static BigDecimal average(BigDecimal total, int count) {
        return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }
}
