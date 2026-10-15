package com.englishhub.modules.auth.dto;

import com.englishhub.common.validation.StrongPassword;
import com.englishhub.modules.user.dto.UserResponse;
import com.englishhub.modules.user.entity.LearningGoal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Các request/response của module xác thực. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không đúng định dạng")
            @Size(max = 255, message = "Email tối đa 255 ký tự")
            String email,

            @StrongPassword
            String password,

            @NotBlank(message = "Họ tên không được để trống")
            @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
            String fullName,

            /** Không bắt buộc - mặc định GENERAL. */
            LearningGoal learningGoal) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email không được để trống") String email,
            @NotBlank(message = "Mật khẩu không được để trống") String password) {
    }

    public record RefreshTokenRequest(@NotBlank(message = "Thiếu refresh token") String refreshToken) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không đúng định dạng")
            String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Thiếu token") String token,
            @StrongPassword String newPassword) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserResponse user) {
    }

    public record MessageResponse(String message) {
    }
}
