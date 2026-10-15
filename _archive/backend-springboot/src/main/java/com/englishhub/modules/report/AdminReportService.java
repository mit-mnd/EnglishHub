package com.englishhub.modules.report;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Số liệu tổng quan cho Admin dashboard (truy vấn tổng hợp, chỉ đọc). */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    /** Điều kiện "là học viên" (có role LEARNER, không phải ADMIN) cho bảng Users alias u. */
    static final String LEARNER_FILTER = """
                EXISTS (SELECT 1 FROM UserRoles ur JOIN Roles r ON r.Id = ur.RoleId
                        WHERE ur.UserId = u.Id AND r.Code = 'LEARNER')
                AND NOT EXISTS (SELECT 1 FROM UserRoles ur JOIN Roles r ON r.Id = ur.RoleId
                        WHERE ur.UserId = u.Id AND r.Code = 'ADMIN')""";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public record LevelCount(String levelCode, String levelName, int learners) {
    }

    public record OverviewResponse(
            int totalLearners,
            int newLearnersLast7Days,
            int activeLearnersLast7Days,
            int placementAttempts,
            BigDecimal averagePlacementPercent,
            int completedLessons,
            List<LevelCount> levelDistribution) {
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var params = new MapSqlParameterSource()
                .addValue("since", nowUtc.minusDays(7))
                .addValue("sinceDate", LocalDate.from(nowUtc).minusDays(6));

        OverviewResponse base = jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM Users u WHERE %1$s),
                  (SELECT COUNT(*) FROM Users u WHERE %1$s AND u.CreatedAt >= :since),
                  (SELECT COUNT(DISTINCT d.UserId) FROM DailyActivities d WHERE d.ActivityDate >= :sinceDate),
                  (SELECT COUNT(*) FROM TestAttempts a JOIN Tests t ON t.Id = a.TestId
                     WHERE t.TestType = 'PLACEMENT' AND a.Status <> 'IN_PROGRESS'),
                  (SELECT CAST(AVG(a.Percentage) AS DECIMAL(5,2)) FROM TestAttempts a JOIN Tests t ON t.Id = a.TestId
                     WHERE t.TestType = 'PLACEMENT' AND a.Status <> 'IN_PROGRESS'),
                  (SELECT COUNT(*) FROM LessonProgresses WHERE Status = 'COMPLETED')
                """.formatted(LEARNER_FILTER), params,
                (rs, i) -> new OverviewResponse(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getBigDecimal(5), rs.getInt(6), List.of()));

        List<LevelCount> distribution = jdbc.query("""
                SELECT ISNULL(l.Code, 'UNASSIGNED'), ISNULL(l.Name, N'Chưa xếp lớp'), COUNT(*)
                FROM Users u LEFT JOIN Levels l ON l.Id = u.CurrentLevelId
                WHERE %s
                GROUP BY l.Code, l.Name, l.SortOrder
                ORDER BY ISNULL(l.SortOrder, 0)""".formatted(LEARNER_FILTER),
                (rs, i) -> new LevelCount(rs.getString(1), rs.getString(2), rs.getInt(3)));

        return new OverviewResponse(base.totalLearners(), base.newLearnersLast7Days(), base.activeLearnersLast7Days(),
                base.placementAttempts(), base.averagePlacementPercent(), base.completedLessons(), distribution);
    }
}
