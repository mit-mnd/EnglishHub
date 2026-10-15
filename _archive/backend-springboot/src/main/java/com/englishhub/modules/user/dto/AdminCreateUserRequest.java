package com.englishhub.modules.user.dto;

import com.englishhub.common.validation.StrongPassword;
import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.modules.user.entity.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminCreateUserRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255)
        String email,

        @StrongPassword
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150)
        String fullName,

        @NotEmpty(message = "Phải có ít nhất một vai trò")
        Set<RoleCode> roles,

        Integer levelId,

        LearningGoal learningGoal) {
}
