package com.englishhub.common.util;

import java.util.Locale;

public final class EmailUtils {

    private EmailUtils() {
    }

    /** Email lưu dạng chữ thường, bỏ khoảng trắng để so sánh/unique nhất quán. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
