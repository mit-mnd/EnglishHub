package com.englishhub.modules.auth.service;

import com.englishhub.modules.auth.repository.PasswordResetTokenRepository;
import com.englishhub.modules.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Dọn token đã hết hạn quá 7 ngày (giữ lại một thời gian ngắn để phục vụ truy vết). */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "0 30 3 * * *")   // 03:30 mỗi ngày (giờ server)
    @Transactional
    public void purgeExpiredTokens() {
        Instant before = clock.instant().minus(RETENTION);
        int refresh = refreshTokenRepository.deleteExpiredBefore(before);
        int reset = passwordResetTokenRepository.deleteExpiredBefore(before);
        log.info("Token cleanup: removed {} refresh tokens, {} password reset tokens", refresh, reset);
    }
}
