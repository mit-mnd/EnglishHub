package com.englishhub.modules.user.controller;

import com.englishhub.modules.user.dto.ChangePasswordRequest;
import com.englishhub.modules.user.dto.UpdateProfileRequest;
import com.englishhub.modules.user.dto.UserResponse;
import com.englishhub.modules.user.service.UserService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users - hồ sơ cá nhân")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Thông tin người dùng đang đăng nhập")
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getProfile(principal.id());
    }

    @Operation(summary = "Cập nhật hồ sơ, mục tiêu học và nhắc học")
    @PutMapping
    public UserResponse updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.id(), request);
    }

    @Operation(summary = "Đổi mật khẩu (đăng xuất mọi thiết bị)")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.id(), request);
        return ResponseEntity.noContent().build();
    }
}
