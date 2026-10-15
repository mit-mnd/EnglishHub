package com.englishhub.modules.user.dto;

import com.englishhub.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
        @StrongPassword String newPassword) {
}
