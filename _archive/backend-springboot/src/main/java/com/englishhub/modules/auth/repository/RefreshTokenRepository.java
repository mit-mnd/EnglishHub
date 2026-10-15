package com.englishhub.modules.auth.repository;

import com.englishhub.modules.auth.entity.RefreshToken;
import com.englishhub.modules.auth.entity.RevokeReason;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi mọi phiên còn hiệu lực của user (đổi/đặt lại mật khẩu, khóa tài khoản, phát hiện token bị đánh cắp).
     * flushAutomatically: ghi các thay đổi đang chờ trước khi chạy UPDATE. Không dùng clearAutomatically
     * vì nó detach mọi entity đang quản lý -> các thay đổi sau đó trong cùng transaction sẽ bị mất.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revokedReason = :reason, t.updatedAt = :now
            where t.user.id = :userId and t.revokedAt is null and t.expiresAt > :now""")
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now,
                                @Param("reason") RevokeReason reason);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
