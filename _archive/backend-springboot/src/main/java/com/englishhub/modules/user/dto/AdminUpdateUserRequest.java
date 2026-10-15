package com.englishhub.modules.user.dto;

import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.modules.user.entity.RoleCode;
import com.englishhub.modules.user.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminUpdateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150)
        String fullName,

        @NotEmpty(message = "Phải có ít nhất một vai trò")
        Set<RoleCode> roles,

        @NotNull
        UserStatus status,

        /** null = chưa xếp level. */
        Integer levelId,

        @NotNull
        LearningGoal learningGoal) {
}
