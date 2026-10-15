package com.englishhub.modules.vocabulary.repository;

import com.englishhub.modules.vocabulary.entity.FlashcardReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, Long> {

    long countByUserIdAndReviewedAtGreaterThanEqual(Long userId, Instant from);

    /** Số thẻ MỚI đã đưa vào học trong ngày: thẻ có lần ôn hôm nay và chưa từng ôn trước đó. */
    @Query("""
            select count(distinct r.userWordId) from FlashcardReview r
            where r.userId = :userId and r.reviewedAt >= :dayStart
              and not exists (select 1 from FlashcardReview p
                              where p.userWordId = r.userWordId and p.reviewedAt < :dayStart)""")
    long countNewIntroducedSince(@Param("userId") Long userId, @Param("dayStart") Instant dayStart);

    boolean existsByUserWordId(Long userWordId);
}
