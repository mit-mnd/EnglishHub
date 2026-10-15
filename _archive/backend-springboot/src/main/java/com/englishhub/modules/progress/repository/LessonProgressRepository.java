package com.englishhub.modules.progress.repository;

import com.englishhub.modules.progress.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, Long lessonId);

    @Query("select p from LessonProgress p where p.userId = :userId and p.lesson.id in :lessonIds")
    List<LessonProgress> findByUserAndLessons(@Param("userId") Long userId, @Param("lessonIds") Collection<Long> lessonIds);
}
