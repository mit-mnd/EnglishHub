package com.englishhub.modules.user.controller;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.user.dto.AdminCreateUserRequest;
import com.englishhub.modules.user.dto.AdminUpdateUserRequest;
import com.englishhub.modules.user.dto.UpdateUserStatusRequest;
import com.englishhub.modules.user.dto.UserResponse;
import com.englishhub.modules.user.entity.RoleCode;
import com.englishhub.modules.user.entity.UserStatus;
import com.englishhub.modules.user.service.AdminUserService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - người dùng")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Tìm kiếm người dùng (phân trang). sort: createdAt | email | fullName | lastLoginAt")
    @GetMapping
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) Integer levelId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminUserService.search(keyword, status, role, levelId, pageable);
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return adminUserService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody AdminCreateUserRequest request) {
        return adminUserService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest request,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return adminUserService.update(id, request, principal.id());
    }

    @Operation(summary = "Khóa / mở khóa / vô hiệu hóa tài khoản")
    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return adminUserService.updateStatus(id, request.status(), principal.id());
    }
}
