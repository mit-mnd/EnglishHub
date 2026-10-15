package com.englishhub.modules.vocabulary.repository;

import com.englishhub.modules.vocabulary.entity.UserWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserWordRepository extends JpaRepository<UserWord, Long> {

    @EntityGraph(attributePaths = "word")
    Optional<UserWord> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "word")
    Optional<UserWord> findByUserIdAndWordId(Long userId, Long wordId);

    @Query("select uw from UserWord uw where uw.userId = :userId and uw.word.id in :wordIds")
    List<UserWord> findByUserAndWords(@Param("userId") Long userId, @Param("wordIds") Collection<Long> wordIds);

    @EntityGraph(attributePaths = "word")
    @Query("""
            select uw from UserWord uw
            where uw.userId = :userId
              and (:status is null or uw.status = :status)
              and (:keyword is null or lower(uw.word.headword) like :keyword or lower(uw.word.meaningVi) like :keyword
                   or lower(uw.note) like :keyword)""")
    Page<UserWord> search(@Param("userId") Long userId, @Param("status") UserWord.Status status,
                          @Param("keyword") String keyword, Pageable pageable);

    // ------------------------------------------------------------------ Flashcard SRS

    /** Khóa dòng khi chấm thẻ: 2 tab cùng ôn một thẻ không ghi đè lịch của nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uw from UserWord uw where uw.id = :id and uw.userId = :userId")
    Optional<UserWord> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /** Thẻ đã học và đến hạn ôn (không gồm thẻ mới / tạm ẩn), hạn sớm nhất trước. */
    @EntityGraph(attributePaths = "word")
    @Query("""
            select uw from UserWord uw
            where uw.userId = :userId and uw.status not in :excluded and uw.dueAt <= :now
            order by uw.dueAt, uw.id""")
    List<UserWord> findDue(@Param("userId") Long userId, @Param("now") Instant now,
                           @Param("excluded") Collection<UserWord.Status> excluded, Pageable pageable);

    @EntityGraph(attributePaths = "word")
    List<UserWord> findByUserIdAndStatusOrderByCreatedAtAscIdAsc(Long userId, UserWord.Status status, Pageable pageable);

    @Query("select uw.status, count(uw) from UserWord uw where uw.userId = :userId group by uw.status")
    List<Object[]> countByStatus(@Param("userId") Long userId);

    @Query("""
            select count(uw) from UserWord uw
            where uw.userId = :userId and uw.status not in :excluded and uw.dueAt <= :now""")
    long countDue(@Param("userId") Long userId, @Param("now") Instant now,
                  @Param("excluded") Collection<UserWord.Status> excluded);

    Optional<UserWord> findFirstByUserIdAndStatusNotInAndDueAtAfterOrderByDueAtAsc(
            Long userId, Collection<UserWord.Status> excluded, Instant after);

    /** Thời điểm đến hạn của các thẻ trong khoảng (để dự báo số thẻ cần ôn những ngày tới). */
    @Query("""
            select uw.dueAt from UserWord uw
            where uw.userId = :userId and uw.status not in :excluded and uw.dueAt > :from and uw.dueAt < :to""")
    List<Instant> findDueTimesBetween(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to,
                                      @Param("excluded") Collection<UserWord.Status> excluded);

    /** Từ trong sổ (trừ từ tạm ẩn), mới thêm trước - nguồn từ cho trò chơi. */
    @EntityGraph(attributePaths = "word")
    @Query("""
            select uw from UserWord uw
            where uw.userId = :userId and uw.status <> com.englishhub.modules.vocabulary.entity.UserWord.Status.SUSPENDED
            order by uw.createdAt desc""")
    List<UserWord> findActiveForGames(@Param("userId") Long userId, Pageable pageable);

    /** Headword (chữ thường) các từ học viên đã lưu - để tô sáng trong bài đọc. */
    @Query("select distinct lower(uw.word.headword) from UserWord uw where uw.userId = :userId")
    List<String> findSavedHeadwords(@Param("userId") Long userId);
}
