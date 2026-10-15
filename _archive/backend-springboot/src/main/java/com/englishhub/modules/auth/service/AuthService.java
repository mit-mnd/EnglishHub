package com.englishhub.modules.auth.service;

import com.englishhub.common.config.AppProperties;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.EmailUtils;
import com.englishhub.common.util.TokenUtils;
import com.englishhub.integration.mail.MailService;
import com.englishhub.modules.auth.dto.AuthDtos.AuthResponse;
import com.englishhub.modules.auth.dto.AuthDtos.LoginRequest;
import com.englishhub.modules.auth.dto.AuthDtos.RegisterRequest;
import com.englishhub.modules.auth.dto.AuthDtos.ResetPasswordRequest;
import com.englishhub.modules.auth.entity.PasswordResetToken;
import com.englishhub.modules.auth.entity.RefreshToken;
import com.englishhub.modules.auth.entity.RevokeReason;
import com.englishhub.modules.auth.repository.PasswordResetTokenRepository;
import com.englishhub.modules.auth.repository.RefreshTokenRepository;
import com.englishhub.modules.user.dto.UserResponse;
import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.modules.user.entity.RoleCode;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.entity.UserStatus;
import com.englishhub.modules.user.repository.RoleRepository;
import com.englishhub.modules.user.repository.UserRepository;
import com.englishhub.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Xác thực: đăng ký, đăng nhập, refresh (xoay vòng token), đăng xuất, quên/đặt lại mật khẩu.
 *
 * <p>Refresh token được xoay vòng (rotation): mỗi lần refresh, token cũ bị thu hồi và cấp token mới.
 * Nếu một token ĐÃ thu hồi bị dùng lại, coi như token bị đánh cắp và thu hồi toàn bộ phiên của user.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final AppProperties appProperties;
    private final Clock clock;

    /** Hash giả dùng khi email không tồn tại, để thời gian phản hồi tương đương (chống dò email). */
    private String dummyPasswordHash;

    @Transactional
    public AuthResponse register(RegisterRequest request, ClientInfo client) {
        String email = EmailUtils.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setLearningGoal(request.learningGoal() != null ? request.learningGoal() : LearningGoal.GENERAL);
        user.setRoles(new HashSet<>(Set.of(roleRepository.findByCode(RoleCode.LEARNER)
                .orElseThrow(() -> new IllegalStateException("Thiếu role LEARNER trong database")))));
        user.setLastLoginAt(clock.instant());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Hai request đăng ký cùng email đồng thời: UNIQUE constraint chặn request thứ hai
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return issueTokens(user, client);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        String email = EmailUtils.normalize(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        ensureActive(user);
        user.setLastLoginAt(clock.instant());
        return issueTokens(user, client);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse refresh(String rawRefreshToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (token.isRevoked()) {
            if (token.getRevokedReason() == RevokeReason.ROTATED) {
                // Token đã được thay bằng token mới mà vẫn bị dùng lại -> có khả năng bị đánh cắp
                log.warn("Refresh token reuse detected for userId={}", token.getUser().getId());
                refreshTokenRepository.revokeAllActiveByUserId(token.getUser().getId(), now, RevokeReason.REUSE_DETECTED);
            }
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (token.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = token.getUser();
        ensureActive(user);
        token.revoke(now, RevokeReason.ROTATED);
        return issueTokens(user, client);
    }

    /** Đăng xuất: thu hồi refresh token. Luôn thành công (idempotent). */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawRefreshToken))
                .filter(t -> !t.isRevoked())
                .ifPresent(t -> t.revoke(clock.instant(), RevokeReason.LOGOUT));
    }

    /** Luôn trả cùng một thông báo dù email có tồn tại hay không (tránh dò email). */
    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = EmailUtils.normalize(rawEmail);
        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    Instant now = clock.instant();
                    passwordResetTokenRepository.invalidateAllByUserId(user.getId(), now);

                    String rawToken = TokenUtils.generateToken();
                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setUser(user);
                    resetToken.setTokenHash(TokenUtils.sha256Hex(rawToken));
                    resetToken.setExpiresAt(now.plus(appProperties.security().passwordResetTtl()));
                    passwordResetTokenRepository.save(resetToken);

                    String link = UriComponentsBuilder.fromUriString(appProperties.frontendUrl())
                            .path("/reset-password").queryParam("token", rawToken).build().toUriString();
                    mailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), link);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = clock.instant();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(TokenUtils.sha256Hex(request.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));
        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        token.setUsedAt(now);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now, RevokeReason.PASSWORD_CHANGED);
    }

    private AuthResponse issueTokens(User user, ClientInfo client) {
        String rawRefreshToken = TokenUtils.generateToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(TokenUtils.sha256Hex(rawRefreshToken));
        refreshToken.setExpiresAt(clock.instant().plus(appProperties.jwt().refreshTokenTtl()));
        refreshToken.setUserAgent(truncate(client.userAgent(), 300));
        refreshToken.setIpAddress(truncate(client.ipAddress(), 45));
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.roleCodes());
        return new AuthResponse(accessToken, rawRefreshToken, "Bearer", jwtService.accessTokenTtlSeconds(),
                UserResponse.from(user));
    }

    private static void ensureActive(User user) {
        switch (user.getStatus()) {
            case LOCKED -> throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            case DISABLED -> throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            case ACTIVE -> { }
        }
    }

    private String dummyHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode(TokenUtils.generateToken());
        }
        return dummyPasswordHash;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** Thông tin thiết bị gửi request, lưu kèm refresh token để người dùng/admin truy vết phiên. */
    public record ClientInfo(String userAgent, String ipAddress) {
    }
}
