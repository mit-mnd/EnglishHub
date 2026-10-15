package com.englishhub.modules.assessment.repository;

import com.englishhub.modules.assessment.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, AssessmentQuestion.Key> {

    /**
     * Xóa toàn bộ câu hỏi của bài test. clearAutomatically: bỏ các AssessmentQuestion cũ khỏi persistence context,
     * nếu không lần lưu lại cùng khóa (TestId, QuestionId) sẽ bị Hibernate coi là UPDATE bản ghi đã mất.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AssessmentQuestion aq where aq.assessment.id = :testId")
    int deleteByTestId(@Param("testId") Long testId);
}
