package com.englishhub.modules.skill.repository;

import com.englishhub.modules.skill.entity.SpeakingSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpeakingSubmissionRepository extends JpaRepository<SpeakingSubmission, Long> {

    Optional<SpeakingSubmission> findByIdAndUserId(Long id, Long userId);

    Page<SpeakingSubmission> findByUserIdAndLessonIdOrderByCreatedAtDesc(Long userId, Long lessonId, Pageable pageable);

    Page<SpeakingSubmission> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
