package com.englishhub.modules.catalog.repository;

import com.englishhub.common.domain.Skill;
import com.englishhub.modules.catalog.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    /** Bài học hiển thị cho học viên: bản thân bài và khóa học chứa nó đều đã publish. */
    @EntityGraph(attributePaths = {"course", "level"})
    @Query("select l from Lesson l where l.id = :id and l.published = true and l.course.published = true")
    Optional<Lesson> findPublishedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"course", "level"})
    @Query("""
            select l from Lesson l
            where l.published = true and l.course.published = true and l.course.id in :courseIds
            order by l.course.sortOrder, l.course.id, l.sortOrder, l.id""")
    List<Lesson> findPublishedByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    @EntityGraph(attributePaths = {"course", "level"})
    @Query("""
            select l from Lesson l
            where l.published = true and l.course.published = true
              and (:skill is null or l.skill = :skill)
              and (:levelId is null or l.level.id = :levelId)
              and (:keyword is null or lower(l.title) like :keyword or lower(l.summary) like :keyword)""")
    Page<Lesson> search(@Param("skill") Skill skill, @Param("levelId") Integer levelId,
                        @Param("keyword") String keyword, Pageable pageable);

    // ------------------------------------------------------------------ Quản trị

    @EntityGraph(attributePaths = {"course", "level"})
    @Query(value = """
            select l from Lesson l
            where (:courseId is null or l.course.id = :courseId)
              and (:skill is null or l.skill = :skill)
              and (:levelId is null or l.level.id = :levelId)
              and (:published is null or l.published = :published)
              and (:keyword is null or lower(l.title) like :keyword or l.slug like :keyword)""",
            countQuery = """
            select count(l) from Lesson l
            where (:courseId is null or l.course.id = :courseId)
              and (:skill is null or l.skill = :skill)
              and (:levelId is null or l.level.id = :levelId)
              and (:published is null or l.published = :published)
              and (:keyword is null or lower(l.title) like :keyword or l.slug like :keyword)""")
    Page<Lesson> adminSearch(@Param("courseId") Long courseId, @Param("skill") Skill skill,
                             @Param("levelId") Integer levelId, @Param("published") Boolean published,
                             @Param("keyword") String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"course", "level"})
    @Query("select l from Lesson l where l.id = :id")
    Optional<Lesson> findWithCourseById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"course", "level"})
    List<Lesson> findByCourseIdOrderBySortOrderAscIdAsc(Long courseId);

    /** [courseId, số bài, số bài đã publish] */
    @Query("""
            select l.course.id, count(l), sum(case when l.published = true then 1 else 0 end)
            from Lesson l where l.course.id in :courseIds group by l.course.id""")
    List<Object[]> countByCourse(@Param("courseIds") Collection<Long> courseIds);

    @Query("select coalesce(max(l.sortOrder), 0) from Lesson l where l.course.id = :courseId")
    int maxSortOrder(@Param("courseId") Long courseId);

    boolean existsByCourseIdAndSlug(Long courseId, String slug);

    boolean existsByCourseIdAndSlugAndIdNot(Long courseId, String slug, Long id);
}
