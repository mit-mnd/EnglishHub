package com.englishhub.modules.vocabulary.repository;

import com.englishhub.modules.vocabulary.entity.VocabularySet;
import com.englishhub.modules.vocabulary.entity.VocabularySetWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VocabularySetRepository extends JpaRepository<VocabularySet, Long> {

    /** Bộ từ hệ thống đã publish, sắp theo level rồi tên. */
    @Query("select s from VocabularySet s where s.published = true and s.ownerUserId is null order by s.levelId, s.name")
    List<VocabularySet> findPublishedSystemSets();

    @Query("select s from VocabularySet s where s.id = :id and s.published = true and s.ownerUserId is null")
    Optional<VocabularySet> findPublishedSystemSet(@Param("id") Long id);

    @Query("""
            select sw from VocabularySetWord sw join fetch sw.word
            where sw.set.id in :setIds order by sw.set.id, sw.sortOrder""")
    List<VocabularySetWord> findWordsOfSets(@Param("setIds") Collection<Long> setIds);

    // ------------------------------------------------------------------ Quản trị (chỉ bộ từ hệ thống)

    @Query(value = """
            select s from VocabularySet s
            where s.ownerUserId is null
              and (:keyword is null or lower(s.name) like :keyword or s.slug like :keyword)
              and (:levelId is null or s.levelId = :levelId)
              and (:published is null or s.published = :published)""",
            countQuery = """
            select count(s) from VocabularySet s
            where s.ownerUserId is null
              and (:keyword is null or lower(s.name) like :keyword or s.slug like :keyword)
              and (:levelId is null or s.levelId = :levelId)
              and (:published is null or s.published = :published)""")
    Page<VocabularySet> adminSearch(@Param("keyword") String keyword, @Param("levelId") Integer levelId,
                                    @Param("published") Boolean published, Pageable pageable);

    @Query("select s from VocabularySet s where s.id = :id and s.ownerUserId is null")
    Optional<VocabularySet> findSystemSet(@Param("id") Long id);

    /** [setId, số từ] */
    @Query("select sw.set.id, count(sw) from VocabularySetWord sw where sw.set.id in :ids group by sw.set.id")
    List<Object[]> countWords(@Param("ids") Collection<Long> setIds);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Xóa từ của bộ; clearAutomatically để lưu lại cùng khóa (SetId, WordId) không bị coi là UPDATE. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VocabularySetWord sw where sw.set.id = :setId")
    int deleteWordsOfSet(@Param("setId") Long setId);
}
