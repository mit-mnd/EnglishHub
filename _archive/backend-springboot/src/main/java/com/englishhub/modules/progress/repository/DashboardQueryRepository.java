package com.englishhub.modules.progress.repository;

import com.englishhub.modules.progress.dto.DashboardDtos.LearningPathSummary;
import com.englishhub.modules.progress.dto.DashboardDtos.NextLesson;
import com.englishhub.modules.progress.dto.DashboardDtos.PlacementSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn chỉ-đọc cho dashboard. Dùng SQL tổng hợp trực tiếp (read model) thay vì nạp entity,
 * vì dashboard chỉ cần các con số COUNT/SUM và được gọi rất thường xuyên.
 */
@Repository
@RequiredArgsConstructor
public class DashboardQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record StreakRow(int current, int longest, LocalDate lastActivityDate) {
    }

    public record DailyRow(LocalDate date, int studySeconds, int lessonsCompleted, int wordsLearned,
                           int wordsReviewed, int quizzesTaken, BigDecimal totalQuizScore) {
    }

    public record LifetimeRow(long studySeconds, int quizzesTaken, BigDecimal totalQuizScore) {
    }

    public record WordCounts(int mastered, int learning, int fresh, int due) {
    }

    public Optional<StreakRow> findStreak(Long userId) {
        return jdbc.query("""
                        SELECT CurrentStreak, LongestStreak, LastActivityDate
                        FROM UserStreaks WHERE UserId = :userId""",
                params(userId),
                (rs, i) -> new StreakRow(rs.getInt(1), rs.getInt(2), toLocalDate(rs.getDate(3)))).stream().findFirst();
    }

    public List<DailyRow> findDailyActivities(Long userId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                        SELECT ActivityDate, StudySeconds, LessonsCompleted, WordsLearned, WordsReviewed,
                               QuizzesTaken, TotalQuizScore
                        FROM DailyActivities
                        WHERE UserId = :userId AND ActivityDate BETWEEN :from AND :to
                        ORDER BY ActivityDate""",
                params(userId).addValue("from", from).addValue("to", to),
                (rs, i) -> new DailyRow(toLocalDate(rs.getDate(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getBigDecimal(7)));
    }

    public LifetimeRow findLifetimeTotals(Long userId) {
        return jdbc.queryForObject("""
                        SELECT ISNULL(SUM(CAST(StudySeconds AS BIGINT)), 0), ISNULL(SUM(QuizzesTaken), 0),
                               ISNULL(SUM(TotalQuizScore), 0)
                        FROM DailyActivities WHERE UserId = :userId""",
                params(userId),
                (rs, i) -> new LifetimeRow(rs.getLong(1), rs.getInt(2), rs.getBigDecimal(3)));
    }

    public WordCounts countWords(Long userId, Instant now) {
        return jdbc.queryForObject("""
                        SELECT ISNULL(SUM(CASE WHEN Status = 'MASTERED' THEN 1 ELSE 0 END), 0),
                               ISNULL(SUM(CASE WHEN Status IN ('LEARNING', 'REVIEW') THEN 1 ELSE 0 END), 0),
                               ISNULL(SUM(CASE WHEN Status = 'NEW' THEN 1 ELSE 0 END), 0),
                               ISNULL(SUM(CASE WHEN Status <> 'SUSPENDED' AND DueAt <= :now THEN 1 ELSE 0 END), 0)
                        FROM UserWords WHERE UserId = :userId""",
                // DATETIME2 lưu giờ UTC "trần" -> bind LocalDateTime UTC (Timestamp sẽ bị đổi theo múi giờ JVM)
                params(userId).addValue("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)),
                (rs, i) -> new WordCounts(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
    }

    public int countCompletedLessons(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM LessonProgresses WHERE UserId = :userId AND Status = 'COMPLETED'",
                params(userId), Integer.class);
        return count == null ? 0 : count;
    }

    public int countUnreadNotifications(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Notifications WHERE UserId = :userId AND IsRead = 0",
                params(userId), Integer.class);
        return count == null ? 0 : count;
    }

    /** Lộ trình ACTIVE + bài học kế tiếp (ưu tiên bài đang học dở, rồi bài đã mở khóa, rồi bài khóa đầu tiên). */
    public Optional<LearningPathSummary> findActiveLearningPath(Long userId) {
        Optional<LearningPathSummary> path = jdbc.query("""
                        SELECT p.Id, p.Name, p.GoalType, COUNT(i.Id) AS Total,
                               ISNULL(SUM(CASE WHEN i.Status IN ('COMPLETED', 'SKIPPED') THEN 1 ELSE 0 END), 0) AS Done
                        FROM LearningPaths p
                        LEFT JOIN LearningPathItems i ON i.PathId = p.Id
                        WHERE p.UserId = :userId AND p.Status = 'ACTIVE'
                        GROUP BY p.Id, p.Name, p.GoalType""",
                params(userId), (rs, i) -> mapPath(rs)).stream().findFirst();
        return path.map(p -> new LearningPathSummary(p.id(), p.name(), p.goalType(), p.totalItems(),
                p.completedItems(), p.percent(), findNextLesson(p.id()).orElse(null)));
    }

    public Optional<PlacementSummary> findLatestPlacement(Long userId) {
        return jdbc.query("""
                        SELECT TOP 1 a.Id, a.Percentage, a.SubmittedAt, l.Code, l.Name
                        FROM TestAttempts a
                        JOIN Tests t ON t.Id = a.TestId
                        LEFT JOIN Levels l ON l.Id = a.ResultLevelId
                        WHERE a.UserId = :userId AND t.TestType = 'PLACEMENT' AND a.Status <> 'IN_PROGRESS'
                        ORDER BY a.SubmittedAt DESC""",
                params(userId),
                (rs, i) -> new PlacementSummary(rs.getLong(1), rs.getBigDecimal(2), toInstant(rs.getTimestamp(3)),
                        rs.getString(4), rs.getString(5))).stream().findFirst();
    }

    private Optional<NextLesson> findNextLesson(Long pathId) {
        return jdbc.query("""
                        SELECT TOP 1 l.Id, l.Title, l.Skill, l.EstimatedMinutes, i.Status
                        FROM LearningPathItems i
                        JOIN Lessons l ON l.Id = i.LessonId
                        WHERE i.PathId = :pathId AND i.Status IN ('IN_PROGRESS', 'AVAILABLE', 'LOCKED')
                        ORDER BY CASE i.Status WHEN 'IN_PROGRESS' THEN 0 WHEN 'AVAILABLE' THEN 1 ELSE 2 END,
                                 i.SortOrder""",
                new MapSqlParameterSource("pathId", pathId),
                (rs, i) -> new NextLesson(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5))).stream().findFirst();
    }

    private static LearningPathSummary mapPath(ResultSet rs) throws SQLException {
        int total = rs.getInt("Total");
        int done = rs.getInt("Done");
        BigDecimal percent = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(done * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        return new LearningPathSummary(rs.getLong("Id"), rs.getString("Name"), rs.getString("GoalType"),
                total, done, percent, null);
    }

    private static MapSqlParameterSource params(Long userId) {
        return new MapSqlParameterSource("userId", userId);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    /** DATETIME2 lưu theo UTC -> đọc như LocalDateTime rồi gắn UTC. */
    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
