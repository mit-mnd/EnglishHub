package com.englishhub.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Tạo slug ASCII cho URL từ tiêu đề tiếng Việt: "Chào hỏi & Giới thiệu" -> "chao-hoi-gioi-thieu". */
public final class SlugUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_LENGTH = 160;

    private SlugUtils() {
    }

    public static String slugify(String text) {
        if (text == null) {
            return "";
        }
        // "đ" không tách được dấu bằng NFD nên thay tay trước
        String s = text.replace('đ', 'd').replace('Đ', 'D');
        s = DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        s = NON_ALNUM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("-");
        s = s.replaceAll("^-+|-+$", "");
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return s.isEmpty() ? "item" : s;
    }

    /** Slug chưa bị dùng: thêm hậu tố -2, -3... khi trùng. */
    public static String unique(String text, Predicate<String> taken) {
        String base = slugify(text);
        String candidate = base;
        for (int i = 2; taken.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
