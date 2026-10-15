package com.englishhub.modules.learningpath.repository;

import com.englishhub.modules.learningpath.entity.LearningPath;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    /** Lộ trình đang chạy (tối đa 1 nhờ filtered unique index) kèm toàn bộ item và bài học. */
    @EntityGraph(attributePaths = {"level", "items", "items.lesson"})
    Optional<LearningPath> findFirstByUserIdAndStatus(Long userId, LearningPath.Status status);

    boolean existsByUserIdAndStatus(Long userId, LearningPath.Status status);
}
