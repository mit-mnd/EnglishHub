package com.englishhub.common.util;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

public final class PageableUtils {

    private PageableUtils() {
    }

    /** Chỉ cho sắp xếp theo các field được phép (tránh lỗi 500 và tránh sort theo cột nhạy cảm). */
    public static Pageable requireSortable(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Không hỗ trợ sắp xếp theo '" + order.getProperty() + "'. Cho phép: " + allowed);
            }
        }
        return pageable;
    }

    /** Từ khóa tìm kiếm -> mẫu LIKE chữ thường "%abc%"; trống -> null (= không lọc). */
    public static String likePattern(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
