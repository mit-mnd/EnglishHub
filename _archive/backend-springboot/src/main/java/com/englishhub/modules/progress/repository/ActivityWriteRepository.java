package com.englishhub.modules.progress.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

/**
 * Ghi số liệu hoạt động theo ngày và chuỗi ngày học.
 *
 * <p>Dùng MERGE ... WITH (HOLDLOCK) để "upsert" nguyên tử: hai request cùng lúc (2 tab trình duyệt)
 * không tạo 2 dòng cho cùng (UserId, ActivityDate) và không ghi đè số liệu của nhau.</p>
 */
@Repository
@RequiredArgsConstructor
public class ActivityWriteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record DailyDelta(int studySeconds, int lessonsCompleted, int wordsLearned, int wordsReviewed,
                             int quizzesTaken, BigDecimal quizScore) {
    }

    /** Cộng dồn vào DailyActivities; GoalReached = 1 khi tổng thời gian học đạt mục tiêu ngày. */
    public void addDailyActivity(Long userId, LocalDate date, DailyDelta d, int goalSeconds) {
        jdbc.update("""
                MERGE DailyActivities WITH (HOLDLOCK) AS t
                USING (SELECT :userId AS UserId, :date AS ActivityDate) AS s
                   ON t.UserId = s.UserId AND t.ActivityDate = s.ActivityDate
                WHEN MATCHED THEN UPDATE SET
                    StudySeconds     = t.StudySeconds + :sec,
                    LessonsCompleted = t.LessonsCompleted + :lessons,
                    WordsLearned     = t.WordsLearned + :learned,
                    WordsReviewed    = t.WordsReviewed + :reviewed,
                    QuizzesTaken     = t.QuizzesTaken + :quizzes,
                    TotalQuizScore   = t.TotalQuizScore + :score,
                    GoalReached      = CASE WHEN t.StudySeconds + :sec >= :goalSec THEN 1 ELSE t.GoalReached END,
                    UpdatedAt        = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN INSERT
                    (UserId, ActivityDate, StudySeconds, LessonsCompleted, WordsLearned, WordsReviewed,
                     QuizzesTaken, TotalQuizScore, GoalReached)
                    VALUES (:userId, :date, :sec, :lessons, :learned, :reviewed, :quizzes, :score,
                            CASE WHEN :sec >= :goalSec THEN 1 ELSE 0 END);""",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("date", date)
                        .addValue("sec", d.studySeconds())
                        .addValue("lessons", d.lessonsCompleted())
                        .addValue("learned", d.wordsLearned())
                        .addValue("reviewed", d.wordsReviewed())
                        .addValue("quizzes", d.quizzesTaken())
                        .addValue("score", d.quizScore() == null ? BigDecimal.ZERO : d.quizScore())
                        .addValue("goalSec", goalSeconds));
    }

    /**
     * Cập nhật chuỗi ngày học khi có hoạt động trong ngày {@code today} (theo múi giờ học viên):
     * <ul>
     *   <li>Đã ghi nhận hôm nay -> giữ nguyên.</li>
     *   <li>Lần cuối là hôm qua -> chuỗi + 1.</li>
     *   <li>Còn lại (nghỉ >= 1 ngày hoặc lần đầu) -> chuỗi = 1.</li>
     * </ul>
     * Trong UPDATE SET, mọi biểu thức t.* đều đọc giá trị CŨ nên phải lặp lại công thức chuỗi mới.
     *
     * @return chuỗi ngày mới nếu chuỗi vừa được cập nhật (lần học đầu tiên trong ngày); rỗng nếu hôm nay đã tính rồi.
     *         Dùng OUTPUT của MERGE nên không cần truy vấn thêm.
     */
    public OptionalInt touchStreak(Long userId, LocalDate today) {
        List<Integer> changed = jdbc.query("""
                MERGE UserStreaks WITH (HOLDLOCK) AS t
                USING (SELECT :userId AS UserId) AS s ON t.UserId = s.UserId
                WHEN MATCHED AND (t.LastActivityDate IS NULL OR t.LastActivityDate < :today) THEN UPDATE SET
                    CurrentStreak = CASE WHEN t.LastActivityDate = DATEADD(DAY, -1, :today)
                                         THEN t.CurrentStreak + 1 ELSE 1 END,
                    LongestStreak = CASE WHEN (CASE WHEN t.LastActivityDate = DATEADD(DAY, -1, :today)
                                                    THEN t.CurrentStreak + 1 ELSE 1 END) > t.LongestStreak
                                         THEN (CASE WHEN t.LastActivityDate = DATEADD(DAY, -1, :today)
                                                    THEN t.CurrentStreak + 1 ELSE 1 END)
                                         ELSE t.LongestStreak END,
                    LastActivityDate = :today,
                    UpdatedAt = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN INSERT (UserId, CurrentStreak, LongestStreak, LastActivityDate)
                    VALUES (:userId, 1, 1, :today)
                OUTPUT inserted.CurrentStreak;""",
                new MapSqlParameterSource().addValue("userId", userId).addValue("today", today),
                (rs, i) -> rs.getInt(1));
        return changed.isEmpty() ? OptionalInt.empty() : OptionalInt.of(changed.get(0));
    }
}
