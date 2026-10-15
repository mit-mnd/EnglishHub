package com.englishhub.modules.user.dto;

import com.englishhub.modules.user.entity.LearningGoal;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullName,

        @Size(max = 500, message = "URL ảnh đại diện tối đa 500 ký tự")
        String avatarUrl,

        @NotNull(message = "Vui lòng chọn mục tiêu học")
        LearningGoal learningGoal,

        @NotNull @Min(value = 5, message = "Mục tiêu tối thiểu 5 phút/ngày")
        @Max(value = 480, message = "Mục tiêu tối đa 480 phút/ngày")
        Integer dailyGoalMinutes,

        @NotBlank @Size(max = 50)
        String timeZone,

        @NotNull
        Boolean reminderEnabled,

        @JsonFormat(pattern = "HH:mm")
        LocalTime reminderTime,

        @NotNull
        Boolean emailNotificationEnabled) {
}
