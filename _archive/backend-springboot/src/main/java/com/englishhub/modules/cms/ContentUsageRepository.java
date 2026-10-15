package com.englishhub.modules.cms;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kiểm tra nội dung đã gắn với dữ liệu học viên hay chưa, trước khi admin xóa.
 * Đã có dữ liệu thì không xóa (giữ lịch sử học, tránh lỗi khóa ngoại) mà chỉ cho ẩn.
 */
@Repository
@RequiredArgsConstructor
public class ContentUsageRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Số bản ghi học viên gắn với các bài học: tiến độ, lộ trình, bài nói/viết, lượt làm quiz của bài. */
    public long lessonUsage(Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return 0;
        }
        return count("""
                SELECT (SELECT COUNT(*) FROM LessonProgresses WHERE LessonId IN (:ids))
                     + (SELECT COUNT(*) FROM LearningPathItems WHERE LessonId IN (:ids))
                     + (SELECT COUNT(*) FROM SpeakingSubmissions WHERE LessonId IN (:ids))
                     + (SELECT COUNT(*) FROM WritingSubmissions WHERE LessonId IN (:ids))
                     + (SELECT COUNT(*) FROM TestAttempts a JOIN Tests t ON t.Id = a.TestId WHERE t.LessonId IN (:ids))""",
                new MapSqlParameterSource("ids", lessonIds));
    }

    /** Số câu trả lời / bài nộp của học viên dùng câu hỏi này. */
    public long questionAnswers(Long questionId) {
        return count("""
                SELECT (SELECT COUNT(*) FROM AttemptAnswers WHERE QuestionId = :id)
                     + (SELECT COUNT(*) FROM SpeakingSubmissions WHERE QuestionId = :id)
                     + (SELECT COUNT(*) FROM WritingSubmissions WHERE QuestionId = :id)""",
                new MapSqlParameterSource("id", questionId));
    }

    /** Tên các bài test đang chứa câu hỏi. */
    public List<String> testsContainingQuestion(Long questionId) {
        return jdbc.queryForList("""
                SELECT t.Title FROM TestQuestions tq JOIN Tests t ON t.Id = tq.TestId
                WHERE tq.QuestionId = :id ORDER BY t.Id""", new MapSqlParameterSource("id", questionId), String.class);
    }

    public long testAttempts(Long testId) {
        return count("SELECT COUNT(*) FROM TestAttempts WHERE TestId = :id", new MapSqlParameterSource("id", testId));
    }

    /** Số học viên đã lưu từ này vào sổ từ. */
    public long wordLearners(Long wordId) {
        return count("SELECT COUNT(*) FROM UserWords WHERE WordId = :id", new MapSqlParameterSource("id", wordId));
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    /** testId -> số lượt làm. */
    public Map<Long, Long> attemptCounts(Collection<Long> testIds) {
        Map<Long, Long> result = new HashMap<>();
        if (!testIds.isEmpty()) {
            jdbc.query("SELECT TestId, COUNT(*) FROM TestAttempts WHERE TestId IN (:ids) GROUP BY TestId",
                    new MapSqlParameterSource("ids", testIds), rs -> {
                        result.put(rs.getLong(1), rs.getLong(2));
                    });
        }
        return result;
    }
}
