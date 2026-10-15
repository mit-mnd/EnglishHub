package com.englishhub.modules.report;

import com.englishhub.common.util.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.englishhub.modules.report.AdminReportService.LEARNER_FILTER;

/**
 * Báo cáo chi tiết cho admin (chỉ đọc, truy vấn tổng hợp bằng SQL):
 * hoạt động theo ngày, hiệu quả nội dung (bài học, bài test, câu hỏi khó), kỹ năng nói/viết, từ vựng,
 * và file CSV danh sách học viên.
 * <p>
 * Ngày hoạt động (DailyActivities) tính theo múi giờ của từng học viên; ngày đăng ký tính theo UTC.
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    /** Câu hỏi cần tối thiểu bấy nhiêu lượt trả lời đã chấm mới đưa vào danh sách "khó nhất" (tránh nhiễu). */
    static final int MIN_ANSWERS_FOR_DIFFICULTY = 5;
    private static final int TOP = 10;
    private static final BigDecimal DEFAULT_PASS_PERCENT = BigDecimal.valueOf(60);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    // ------------------------------------------------------------------ Hoạt động theo ngày

    public record DailyPoint(LocalDate date, int activeLearners, int newLearners, int studyMinutes, int lessonsCompleted,
                             int wordsReviewed) {
    }

    public record ActivityReport(int days, List<DailyPoint> points, int totalStudyMinutes, BigDecimal avgDailyActive) {
    }

    @Transactional(readOnly = true)
    public ActivityReport activity(int requestedDays) {
        int days = Math.max(7, Math.min(requestedDays, 365));
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate from = today.minusDays(days - 1L);
        var params = new MapSqlParameterSource().addValue("from", from).addValue("fromTs", from.atStartOfDay());

        Map<LocalDate, int[]> byDate = new HashMap<>();   // [active, new, minutes, lessons, words]
        jdbc.query("""
                SELECT ActivityDate, COUNT(DISTINCT UserId), SUM(StudySeconds) / 60, SUM(LessonsCompleted), SUM(WordsReviewed)
                FROM DailyActivities WHERE ActivityDate >= :from GROUP BY ActivityDate""", params, rs -> {
            int[] v = byDate.computeIfAbsent(rs.getDate(1).toLocalDate(), d -> new int[5]);
            v[0] = rs.getInt(2);
            v[2] = rs.getInt(3);
            v[3] = rs.getInt(4);
            v[4] = rs.getInt(5);
        });
        jdbc.query("""
                SELECT CAST(u.CreatedAt AS DATE), COUNT(*) FROM Users u
                WHERE u.CreatedAt >= :fromTs AND %s GROUP BY CAST(u.CreatedAt AS DATE)""".formatted(LEARNER_FILTER), params, rs -> {
            byDate.computeIfAbsent(rs.getDate(1).toLocalDate(), d -> new int[5])[1] = rs.getInt(2);
        });

        // Điền đủ mọi ngày (kể cả ngày không có hoạt động) để biểu đồ liền mạch
        List<DailyPoint> points = new ArrayList<>(days);
        int totalMinutes = 0;
        int totalActive = 0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            int[] v = byDate.getOrDefault(d, new int[5]);
            points.add(new DailyPoint(d, v[0], v[1], v[2], v[3], v[4]));
            totalMinutes += v[2];
            totalActive += v[0];
        }
        return new ActivityReport(days, points, totalMinutes,
                BigDecimal.valueOf(totalActive).divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------------ Hiệu quả nội dung

    public record LessonStat(Long id, String title, String skill, String courseTitle, int learners, int completions,
                             BigDecimal completionRate, BigDecimal avgMinutes) {
    }

    public record TestStat(Long id, String title, String testType, int attempts, BigDecimal avgPercent, BigDecimal passRate) {
    }

    public record QuestionStat(Long id, String content, String questionType, String skill, int answers, BigDecimal correctRate) {
    }

    public record SkillStat(String skill, int submissions, BigDecimal avgScore) {
    }

    public record VocabularyStat(int savedWords, int masteredWords, int reviewsLast7Days, int gamePlaysLast7Days) {
    }

    public record ContentReport(List<LessonStat> topLessons, List<LessonStat> lowCompletionLessons, List<TestStat> tests,
                                List<QuestionStat> hardestQuestions, List<SkillStat> productiveSkills,
                                VocabularyStat vocabulary) {
    }

    @Transactional(readOnly = true)
    public ContentReport content() {
        LocalDateTime weekAgo = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(7);
        var params = new MapSqlParameterSource().addValue("top", TOP).addValue("minAnswers", MIN_ANSWERS_FOR_DIFFICULTY)
                .addValue("since", weekAgo).addValue("defaultPass", DEFAULT_PASS_PERCENT);

        String lessonStats = """
                SELECT TOP (:top) l.Id, l.Title, l.Skill, c.Title, COUNT(*) AS Learners,
                       SUM(CASE WHEN p.Status = 'COMPLETED' THEN 1 ELSE 0 END) AS Completions,
                       CAST(100.0 * SUM(CASE WHEN p.Status = 'COMPLETED' THEN 1 ELSE 0 END) / COUNT(*) AS DECIMAL(5,1)) AS Rate,
                       CAST(AVG(p.TimeSpentSeconds) / 60.0 AS DECIMAL(7,1))
                FROM LessonProgresses p JOIN Lessons l ON l.Id = p.LessonId JOIN Courses c ON c.Id = l.CourseId
                GROUP BY l.Id, l.Title, l.Skill, c.Title
                %s""";
        List<LessonStat> top = jdbc.query(lessonStats.formatted("ORDER BY Completions DESC, Learners DESC"), params,
                (rs, i) -> lessonStat(rs));
        // Bài nhiều người bắt đầu nhưng chưa hoàn thành: gợi ý nội dung cần xem lại (tối thiểu 3 học viên, có người bỏ dở)
        List<LessonStat> low = jdbc.query(lessonStats.formatted("""
                HAVING COUNT(*) >= 3 AND SUM(CASE WHEN p.Status = 'COMPLETED' THEN 1 ELSE 0 END) < COUNT(*)
                ORDER BY Rate ASC, Learners DESC"""), params,
                (rs, i) -> lessonStat(rs));

        List<TestStat> tests = jdbc.query("""
                SELECT TOP (:top) t.Id, t.Title, t.TestType, COUNT(*),
                       CAST(AVG(a.Percentage) AS DECIMAL(5,1)),
                       CAST(100.0 * SUM(CASE WHEN a.Percentage >= ISNULL(t.PassingPercent, :defaultPass) THEN 1 ELSE 0 END)
                            / COUNT(*) AS DECIMAL(5,1))
                FROM TestAttempts a JOIN Tests t ON t.Id = a.TestId
                WHERE a.Status <> 'IN_PROGRESS'
                GROUP BY t.Id, t.Title, t.TestType
                ORDER BY COUNT(*) DESC""", params,
                (rs, i) -> new TestStat(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getBigDecimal(5), "PLACEMENT".equals(rs.getString(3)) ? null : rs.getBigDecimal(6)));

        // Chỉ câu chấm tự động: câu Viết/Nói tính "đúng" theo ngưỡng điểm nên tỉ lệ đúng không phản ánh độ khó của đề
        List<QuestionStat> hardest = jdbc.query("""
                SELECT TOP (:top) q.Id, LEFT(q.Content, 200), q.QuestionType, q.Skill, COUNT(*),
                       CAST(100.0 * SUM(CASE WHEN aa.IsCorrect = 1 THEN 1 ELSE 0 END) / COUNT(*) AS DECIMAL(5,1)) AS Rate
                FROM AttemptAnswers aa JOIN Questions q ON q.Id = aa.QuestionId
                WHERE aa.IsCorrect IS NOT NULL AND q.QuestionType NOT IN ('ESSAY', 'SPEAKING')
                GROUP BY q.Id, LEFT(q.Content, 200), q.QuestionType, q.Skill
                HAVING COUNT(*) >= :minAnswers
                ORDER BY Rate ASC, COUNT(*) DESC""", params,
                (rs, i) -> new QuestionStat(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        rs.getBigDecimal(6)));

        List<SkillStat> skills = jdbc.query("""
                SELECT 'SPEAKING', COUNT(*), CAST(AVG(PronunciationScore) AS DECIMAL(5,1)) FROM SpeakingSubmissions WHERE Status = 'SCORED'
                UNION ALL
                SELECT 'WRITING', COUNT(*), CAST(AVG(Score) AS DECIMAL(5,1)) FROM WritingSubmissions WHERE Status = 'SCORED'""",
                params, (rs, i) -> new SkillStat(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)));

        VocabularyStat vocabulary = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM UserWords),
                       (SELECT COUNT(*) FROM UserWords WHERE Status = 'MASTERED'),
                       (SELECT COUNT(*) FROM FlashcardReviews WHERE ReviewedAt >= :since),
                       (SELECT COUNT(*) FROM GameResults WHERE CreatedAt >= :since)""", params,
                (rs, i) -> new VocabularyStat(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));

        return new ContentReport(top, low, tests, hardest, skills, vocabulary);
    }

    private static LessonStat lessonStat(ResultSet rs) throws SQLException {
        return new LessonStat(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6),
                rs.getBigDecimal(7), rs.getBigDecimal(8));
    }

    // ------------------------------------------------------------------ Xuất CSV

    /** Danh sách học viên kèm số liệu học tập, dạng CSV (UTF-8 có BOM để mở bằng Excel). */
    @Transactional(readOnly = true)
    public String learnersCsv() {
        CsvWriter csv = new CsvWriter().row(List.of("Email", "Họ tên", "Trình độ", "Mục tiêu", "Trạng thái",
                "Ngày đăng ký (UTC)", "Đăng nhập gần nhất (UTC)", "Chuỗi hiện tại", "Chuỗi dài nhất", "Tổng phút học",
                "Bài đã hoàn thành", "Từ trong sổ", "Điểm xếp lớp gần nhất (%)"));
        jdbc.query("""
                SELECT u.Email, u.FullName, l.Name, u.LearningGoal, u.Status, u.CreatedAt, u.LastLoginAt,
                       ISNULL(s.CurrentStreak, 0), ISNULL(s.LongestStreak, 0),
                       ISNULL((SELECT SUM(d.StudySeconds) FROM DailyActivities d WHERE d.UserId = u.Id), 0) / 60,
                       (SELECT COUNT(*) FROM LessonProgresses p WHERE p.UserId = u.Id AND p.Status = 'COMPLETED'),
                       (SELECT COUNT(*) FROM UserWords w WHERE w.UserId = u.Id),
                       (SELECT TOP 1 a.Percentage FROM TestAttempts a JOIN Tests t ON t.Id = a.TestId
                         WHERE a.UserId = u.Id AND t.TestType = 'PLACEMENT' AND a.Status <> 'IN_PROGRESS'
                         ORDER BY a.SubmittedAt DESC)
                FROM Users u
                LEFT JOIN Levels l ON l.Id = u.CurrentLevelId
                LEFT JOIN UserStreaks s ON s.UserId = u.Id
                WHERE %s
                ORDER BY u.CreatedAt DESC""".formatted(LEARNER_FILTER), new MapSqlParameterSource(), rs -> {
            csv.row(Arrays.asList(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), utc(rs.getTimestamp(6)), utc(rs.getTimestamp(7)), rs.getInt(8), rs.getInt(9),
                    rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getBigDecimal(13)));
        });
        return csv.build();
    }

    /** DATETIME2 lưu UTC: đọc thành LocalDateTime rồi định dạng "yyyy-MM-dd HH:mm". */
    private static String utc(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().toString().replace('T', ' ').substring(0, 16);
    }
}
