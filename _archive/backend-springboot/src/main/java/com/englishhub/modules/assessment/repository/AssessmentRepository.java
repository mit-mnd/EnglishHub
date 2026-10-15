package com.englishhub.modules.assessment.repository;

import com.englishhub.modules.assessment.entity.Assessment;
import com.englishhub.modules.assessment.entity.AssessmentQuestion;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    /** Bài test đang dùng = bài đã publish mới nhất của loại đó. */
    Optional<Assessment> findFirstByTestTypeAndPublishedTrueOrderByIdDesc(TestType testType);

    /** Bài tập / quiz đã publish gắn với một bài học. */
    @EntityGraph(attributePaths = {"questions", "questions.question"})
    List<Assessment> findByLessonIdAndPublishedTrueOrderByIdAsc(Long lessonId);

    /** Nạp toàn bộ câu hỏi + phương án của bài test trong 1 query. */
    @Query("""
            select distinct aq from AssessmentQuestion aq
            join fetch aq.question q
            left join fetch q.options
            where aq.assessment.id = :testId
            order by aq.sortOrder""")
    List<AssessmentQuestion> findQuestionsWithOptions(@Param("testId") Long testId);

    // ------------------------------------------------------------------ Quản trị

    List<Assessment> findByLessonIdOrderByIdAsc(Long lessonId);

    /** [testId, số câu hỏi] */
    @Query("select aq.assessment.id, count(aq) from AssessmentQuestion aq where aq.assessment.id in :ids group by aq.assessment.id")
    List<Object[]> countQuestions(@Param("ids") Collection<Long> testIds);

    /** Xóa quiz của bài học (TestQuestions xóa theo ON DELETE CASCADE). Chỉ gọi khi chưa có lượt làm. */
    @Modifying
    @Query("delete from Assessment a where a.lessonId in :lessonIds")
    int deleteByLessonIds(@Param("lessonIds") Collection<Long> lessonIds);

    @Query(value = """
            select a from Assessment a
            where (:type is null or a.testType = :type)
              and (:lessonId is null or a.lessonId = :lessonId)
              and (:published is null or a.published = :published)
              and (:keyword is null or lower(a.title) like :keyword)""",
            countQuery = """
            select count(a) from Assessment a
            where (:type is null or a.testType = :type)
              and (:lessonId is null or a.lessonId = :lessonId)
              and (:published is null or a.published = :published)
              and (:keyword is null or lower(a.title) like :keyword)""")
    Page<Assessment> adminSearch(
            @Param("type") TestType type, @Param("lessonId") Long lessonId, @Param("published") Boolean published,
            @Param("keyword") String keyword, Pageable pageable);
}
