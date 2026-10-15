package com.englishhub.modules.auth.entity;

import com.englishhub.common.entity.AuditableEntity;
import com.englishhub.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "RefreshTokens")
public class RefreshToken extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    /** SHA-256 của token gốc. */
    @Column(nullable = false, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RevokeReason revokedReason;

    @Nationalized
    @Column(length = 300)
    private String userAgent;

    @Column(length = 45)
    private String ipAddress;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now, RevokeReason reason) {
        this.revokedAt = now;
        this.revokedReason = reason;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
