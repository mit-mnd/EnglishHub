package com.englishhub.common.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void removesVietnameseDiacriticsAndSymbols() {
        assertThat(SlugUtils.slugify("Chào hỏi & Giới thiệu bản thân")).isEqualTo("chao-hoi-gioi-thieu-ban-than");
        assertThat(SlugUtils.slugify("Đường đến TOEIC 650+")).isEqualTo("duong-den-toeic-650");
        assertThat(SlugUtils.slugify("  --Ôn tập!!  ")).isEqualTo("on-tap");
    }

    @Test
    void neverEmpty() {
        assertThat(SlugUtils.slugify("!!!")).isEqualTo("item");
        assertThat(SlugUtils.slugify(null)).isEmpty();
    }

    @Test
    void appendsSuffixWhenTaken() {
        Set<String> taken = Set.of("bai-1", "bai-1-2");
        assertThat(SlugUtils.unique("Bài 1", taken::contains)).isEqualTo("bai-1-3");
        assertThat(SlugUtils.unique("Bài 2", taken::contains)).isEqualTo("bai-2");
    }
}
