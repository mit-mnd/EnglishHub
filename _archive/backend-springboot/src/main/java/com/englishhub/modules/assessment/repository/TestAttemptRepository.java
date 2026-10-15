package com.englishhub.modules.assessment.repository;

import com.englishhub.modules.assessment.entity.Enums.AttemptStatus;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import com.englishhub.modules.assessment.entity.TestAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long> {

    Optional<TestAttempt> findFirstByUserIdAndAssessmentIdAndStatusOrderByStartedAtDesc(
            Long userId, Long testId, AttemptStatus status);

    @EntityGraph(attributePaths = {"resultLevel"})
    Optional<TestAttempt> findFirstByUserIdAndAssessmentTestTypeAndStatusNotOrderBySubmittedAtDesc(
            Long userId, TestType testType, AttemptStatus status);

    @EntityGraph(attributePaths = {"assessment", "resultLevel"})
    Optional<TestAttempt> findByIdAndUserId(Long id, Long userId);

    /** Khóa dòng attempt khi nộp bài để 2 request nộp đồng thời không chấm 2 lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TestAttempt a where a.id = :id and a.user.id = :userId")
    Optional<TestAttempt> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select a from TestAttempt a where a.user.id = :userId and a.assessment.id in :testIds")
    List<TestAttempt> findByUserAndTests(@Param("userId") Long userId, @Param("testIds") Collection<Long> testIds);

    @EntityGraph(attributePaths = {"assessment", "resultLevel"})
    Page<TestAttempt> findByUserIdAndAssessmentTestTypeAndStatusNot(
            Long userId, TestType testType, AttemptStatus status, Pageable pageable);
}
