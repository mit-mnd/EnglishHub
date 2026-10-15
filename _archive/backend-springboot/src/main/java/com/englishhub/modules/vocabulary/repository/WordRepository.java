package com.englishhub.modules.vocabulary.repository;

import com.englishhub.modules.vocabulary.entity.Word;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {

    /** Collation mặc định của SQL Server không phân biệt hoa thường nên so sánh bằng là đủ. */
    List<Word> findByHeadwordInOrderByIdAsc(Collection<String> headwords);

    Optional<Word> findByHeadwordAndPartOfSpeech(String headword, String partOfSpeech);

    /** Từ ngẫu nhiên có nghĩa tiếng Việt, tới level chỉ định - bổ sung khi sổ từ học viên còn ít (trò chơi). */
    @Query(value = """
            SELECT TOP (:limit) * FROM Words
            WHERE MeaningVi <> N'' AND (LevelId IS NULL OR LevelId <= :maxLevelId)
            ORDER BY NEWID()""", nativeQuery = true)
    List<Word> findRandomForGames(@Param("maxLevelId") int maxLevelId, @Param("limit") int limit);

    // ------------------------------------------------------------------ Quản trị

    /** excludeSetId: ẩn từ đã có trong bộ từ đó (khi chọn từ cho bộ). */
    @Query(value = """
            select w from Word w
            where (:keyword is null or lower(w.headword) like :keyword or lower(w.meaningVi) like :keyword)
              and (:levelId is null or w.levelId = :levelId)
              and (:pos is null or w.partOfSpeech = :pos)
              and (:source is null or w.source = :source)
              and (:excludeSetId is null or not exists (
                    select 1 from VocabularySetWord sw where sw.set.id = :excludeSetId and sw.word = w))""",
            countQuery = """
            select count(w) from Word w
            where (:keyword is null or lower(w.headword) like :keyword or lower(w.meaningVi) like :keyword)
              and (:levelId is null or w.levelId = :levelId)
              and (:pos is null or w.partOfSpeech = :pos)
              and (:source is null or w.source = :source)
              and (:excludeSetId is null or not exists (
                    select 1 from VocabularySetWord sw where sw.set.id = :excludeSetId and sw.word = w))""")
    Page<Word> adminSearch(@Param("keyword") String keyword, @Param("levelId") Integer levelId,
                           @Param("pos") String partOfSpeech, @Param("source") Word.Source source,
                           @Param("excludeSetId") Long excludeSetId, Pageable pageable);

    boolean existsByHeadwordAndPartOfSpeechAndIdNot(String headword, String partOfSpeech, Long id);

    boolean existsByHeadwordAndPartOfSpeech(String headword, String partOfSpeech);
}
