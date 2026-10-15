package com.englishhub.modules.catalog.repository;

import com.englishhub.modules.catalog.entity.Course;
import com.englishhub.modules.user.entity.LearningGoal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /** Khóa học đã publish, lọc tùy chọn theo level / mục tiêu (null = không lọc). */
    @EntityGraph(attributePaths = "level")
    @Query("""
            select c from Course c
            where c.published = true
              and (:levelId is null or c.level.id = :levelId)
              and (:goal is null or c.goalType = :goal)
            order by c.level.sortOrder, c.sortOrder, c.id""")
    List<Course> findPublished(@Param("levelId") Integer levelId, @Param("goal") LearningGoal goal);

    @EntityGraph(attributePaths = "level")
    Optional<Course> findByIdAndPublishedTrue(Long id);

    /** Ứng viên cho lộ trình: khóa học của 1 level, khớp mục tiêu hoặc GENERAL. */
    @EntityGraph(attributePaths = "level")
    @Query("""
            select c from Course c
            where c.published = true and c.level.id = :levelId and c.goalType in :goals
            order by c.sortOrder, c.id""")
    List<Course> findForPath(@Param("levelId") Integer levelId, @Param("goals") Collection<LearningGoal> goals);

    // ------------------------------------------------------------------ Quản trị

    @EntityGraph(attributePaths = "level")
    @Query(value = """
            select c from Course c
            where (:keyword is null or lower(c.title) like :keyword or c.slug like :keyword)
              and (:levelId is null or c.level.id = :levelId)
              and (:goal is null or c.goalType = :goal)
              and (:published is null or c.published = :published)""",
            countQuery = """
            select count(c) from Course c
            where (:keyword is null or lower(c.title) like :keyword or c.slug like :keyword)
              and (:levelId is null or c.level.id = :levelId)
              and (:goal is null or c.goalType = :goal)
              and (:published is null or c.published = :published)""")
    Page<Course> adminSearch(@Param("keyword") String keyword, @Param("levelId") Integer levelId,
                             @Param("goal") LearningGoal goal, @Param("published") Boolean published, Pageable pageable);

    @EntityGraph(attributePaths = "level")
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findWithLevelById(@Param("id") Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @EntityGraph(attributePaths = "level")
    List<Course> findAllByOrderByLevelSortOrderAscSortOrderAscIdAsc();
}
