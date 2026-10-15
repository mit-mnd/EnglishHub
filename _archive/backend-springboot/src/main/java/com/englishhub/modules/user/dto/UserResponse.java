package com.englishhub.modules.user.dto;

import com.englishhub.modules.catalog.dto.LevelResponse;
import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String avatarUrl,
        UserStatus status,
        Set<String> roles,
        LevelResponse currentLevel,
        LearningGoal learningGoal,
        Integer dailyGoalMinutes,
        String timeZone,
        boolean reminderEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime reminderTime,
        boolean emailNotificationEnabled,
        Instant placementCompletedAt,
        Instant lastLoginAt,
        Instant createdAt) {

    /** Gọi trong transaction (roles và currentLevel là lazy). */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus(),
                new TreeSet<>(user.roleCodes()),
                LevelResponse.from(user.getCurrentLevel()),
                user.getLearningGoal(),
                user.getDailyGoalMinutes(),
                user.getTimeZone(),
                user.isReminderEnabled(),
                user.getReminderTime(),
                user.isEmailNotificationEnabled(),
                user.getPlacementCompletedAt(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
