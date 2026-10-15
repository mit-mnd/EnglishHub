package com.englishhub.modules.user.service;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.modules.auth.entity.RevokeReason;
import com.englishhub.modules.auth.repository.RefreshTokenRepository;
import com.englishhub.modules.user.dto.ChangePasswordRequest;
import com.englishhub.modules.user.dto.UpdateProfileRequest;
import com.englishhub.modules.user.dto.UserResponse;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;

/** Nghiệp vụ hồ sơ cá nhân của người dùng đang đăng nhập. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.setFullName(request.fullName().trim());
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setLearningGoal(request.learningGoal());
        user.setDailyGoalMinutes(request.dailyGoalMinutes());
        user.setTimeZone(validateTimeZone(request.timeZone()));
        user.setReminderEnabled(request.reminderEnabled());
        user.setReminderTime(request.reminderTime());
        user.setEmailNotificationEnabled(request.emailNotificationEnabled());
        return UserResponse.from(user);
    }

    /** Đổi mật khẩu và đăng xuất mọi thiết bị khác (thu hồi toàn bộ refresh token). */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant(), RevokeReason.PASSWORD_CHANGED);
    }

    private User getUser(Long userId) {
        return userRepository.findWithDetailsById(userId)
                .orElseThrow(() -> BusinessException.notFound("người dùng", userId));
    }

    private static String validateTimeZone(String timeZone) {
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Múi giờ không hợp lệ: " + timeZone);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
