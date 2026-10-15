package com.englishhub.modules.user.service;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.EmailUtils;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.modules.auth.entity.RevokeReason;
import com.englishhub.modules.auth.repository.RefreshTokenRepository;
import com.englishhub.modules.catalog.entity.Level;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.user.dto.AdminCreateUserRequest;
import com.englishhub.modules.user.dto.AdminUpdateUserRequest;
import com.englishhub.modules.user.dto.UserResponse;
import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.modules.user.entity.Role;
import com.englishhub.modules.user.entity.RoleCode;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.entity.UserStatus;
import com.englishhub.modules.user.repository.RoleRepository;
import com.englishhub.modules.user.repository.UserRepository;
import com.englishhub.modules.user.repository.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Nghiệp vụ quản lý người dùng dành cho ADMIN. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "email", "fullName", "lastLoginAt", "status");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LevelRepository levelRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String keyword, UserStatus status, RoleCode role, Integer levelId,
                                             Pageable pageable) {
        Specification<User> spec = Specification.allOf(Stream.of(
                        UserSpecifications.keyword(keyword),
                        UserSpecifications.status(status),
                        UserSpecifications.hasRole(role),
                        UserSpecifications.levelId(levelId))
                .filter(s -> s != null)
                .toList());
        Pageable safePageable = PageableUtils.requireSortable(pageable, SORTABLE_FIELDS);
        return PageResponse.of(userRepository.findAll(spec, safePageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(getUser(id));
    }

    @Transactional
    public UserResponse create(AdminCreateUserRequest request) {
        String email = EmailUtils.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setLearningGoal(request.learningGoal() != null ? request.learningGoal() : LearningGoal.GENERAL);
        user.setCurrentLevel(resolveLevel(request.levelId()));
        user.setRoles(resolveRoles(request.roles()));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, AdminUpdateUserRequest request, Long currentAdminId) {
        User user = getUser(id);
        guardSelfLockout(id, currentAdminId, request.status(), request.roles());
        user.setFullName(request.fullName().trim());
        user.setRoles(resolveRoles(request.roles()));
        user.setCurrentLevel(resolveLevel(request.levelId()));
        user.setLearningGoal(request.learningGoal());
        applyStatus(user, request.status());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status, Long currentAdminId) {
        User user = getUser(id);
        guardSelfLockout(id, currentAdminId, status, null);
        applyStatus(user, status);
        return UserResponse.from(user);
    }

    /** Khi khóa/vô hiệu hóa: thu hồi refresh token để user bị đăng xuất khi access token hết hạn. */
    private void applyStatus(User user, UserStatus status) {
        boolean deactivating = user.getStatus() != status && status != UserStatus.ACTIVE;
        user.setStatus(status);
        if (deactivating) {
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), clock.instant(), RevokeReason.ACCOUNT_DEACTIVATED);
        }
    }

    /** Ngăn admin tự khóa mình hoặc tự gỡ quyền ADMIN (tránh mất quyền quản trị). */
    private static void guardSelfLockout(Long targetId, Long currentAdminId, UserStatus newStatus, Set<RoleCode> newRoles) {
        if (!targetId.equals(currentAdminId)) {
            return;
        }
        boolean deactivating = newStatus != null && newStatus != UserStatus.ACTIVE;
        boolean droppingAdmin = newRoles != null && !newRoles.contains(RoleCode.ADMIN);
        if (deactivating || droppingAdmin) {
            throw new BusinessException(ErrorCode.SELF_MODIFICATION_NOT_ALLOWED);
        }
    }

    private Set<Role> resolveRoles(Set<RoleCode> codes) {
        List<Role> roles = roleRepository.findByCodeIn(codes);
        if (roles.size() != codes.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vai trò không hợp lệ");
        }
        return new HashSet<>(roles);
    }

    private Level resolveLevel(Integer levelId) {
        if (levelId == null) {
            return null;
        }
        return levelRepository.findById(levelId).orElseThrow(() -> BusinessException.notFound("level", levelId));
    }

    private User getUser(Long id) {
        return userRepository.findWithDetailsById(id).orElseThrow(() -> BusinessException.notFound("người dùng", id));
    }
}
