package com.englishhub.modules.user.repository;

import com.englishhub.modules.user.entity.RoleCode;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.entity.UserStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/** Điều kiện lọc động cho màn hình quản lý người dùng. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("fullName")), pattern));
    }

    public static Specification<User> status(UserStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<User> levelId(Integer levelId) {
        return levelId == null ? null : (root, query, cb) -> cb.equal(root.get("currentLevel").get("id"), levelId);
    }

    /** Dùng subquery thay vì join để không nhân bản dòng khi phân trang. */
    public static Specification<User> hasRole(RoleCode role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            var subUser = sub.from(User.class);
            Join<Object, Object> roles = subUser.join("roles");
            sub.select(subUser.get("id"))
                    .where(cb.equal(subUser.get("id"), root.get("id")), cb.equal(roles.get("code"), role));
            return cb.exists(sub);
        };
    }
}
