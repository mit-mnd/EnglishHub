package com.englishhub.modules.assessment.repository;

import com.englishhub.common.domain.Skill;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Tìm trong ngân hàng câu hỏi; excludeTestId: ẩn các câu đã có trong bài test đó (khi chọn câu cho bài test). */
    @EntityGraph(attributePaths = "level")
    @Query(value = """
            select q from Question q
            where (:type is null or q.questionType = :type)
              and (:skill is null or q.skill = :skill)
              and (:levelId is null or q.level.id = :levelId)
              and (:active is null or q.active = :active)
              and (:keyword is null or lower(q.content) like :keyword or lower(q.passage) like :keyword)
              and (:excludeTestId is null or not exists (
                    select 1 from AssessmentQuestion aq where aq.assessment.id = :excludeTestId and aq.question = q))""",
            countQuery = """
            select count(q) from Question q
            where (:type is null or q.questionType = :type)
              and (:skill is null or q.skill = :skill)
              and (:levelId is null or q.level.id = :levelId)
              and (:active is null or q.active = :active)
              and (:keyword is null or lower(q.content) like :keyword or lower(q.passage) like :keyword)
              and (:excludeTestId is null or not exists (
                    select 1 from AssessmentQuestion aq where aq.assessment.id = :excludeTestId and aq.question = q))""")
    Page<Question> adminSearch(@Param("type") QuestionType type, @Param("skill") Skill skill,
                               @Param("levelId") Integer levelId, @Param("active") Boolean active,
                               @Param("keyword") String keyword, @Param("excludeTestId") Long excludeTestId,
                               Pageable pageable);

    @EntityGraph(attributePaths = {"level", "options"})
    @Query("select q from Question q where q.id = :id")
    Optional<Question> findWithOptionsById(@Param("id") Long id);

    /** [questionId, số bài test chứa câu hỏi] */
    @Query("select aq.question.id, count(aq) from AssessmentQuestion aq where aq.question.id in :ids group by aq.question.id")
    List<Object[]> countTests(@Param("ids") Collection<Long> questionIds);
}
