package com.englishhub.modules.auth.controller;

import com.englishhub.modules.auth.dto.AuthDtos.AuthResponse;
import com.englishhub.modules.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.englishhub.modules.auth.dto.AuthDtos.LoginRequest;
import com.englishhub.modules.auth.dto.AuthDtos.MessageResponse;
import com.englishhub.modules.auth.dto.AuthDtos.RefreshTokenRequest;
import com.englishhub.modules.auth.dto.AuthDtos.RegisterRequest;
import com.englishhub.modules.auth.dto.AuthDtos.ResetPasswordRequest;
import com.englishhub.modules.auth.service.AuthService;
import com.englishhub.modules.auth.service.AuthService.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Đăng ký tài khoản học viên")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, clientInfo(http));
    }

    @Operation(summary = "Đăng nhập")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, clientInfo(http));
    }

    @Operation(summary = "Cấp access token mới bằng refresh token (token cũ bị thu hồi)")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), clientInfo(http));
    }

    @Operation(summary = "Đăng xuất (thu hồi refresh token)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Gửi link đặt lại mật khẩu (dev: link in ra log server)")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return new MessageResponse("Nếu email tồn tại trong hệ thống, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu.");
    }

    @Operation(summary = "Đặt lại mật khẩu bằng token trong email")
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.");
    }

    private static ClientInfo clientInfo(HttpServletRequest http) {
        return new ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }
}
