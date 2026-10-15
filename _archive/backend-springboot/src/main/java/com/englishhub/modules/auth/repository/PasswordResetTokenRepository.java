package com.englishhub.modules.auth.repository;

import com.englishhub.modules.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Vô hiệu các link cũ chưa dùng khi user yêu cầu link mới. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetToken t set t.usedAt = :now, t.updatedAt = :now
            where t.user.id = :userId and t.usedAt is null""")
    int invalidateAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
