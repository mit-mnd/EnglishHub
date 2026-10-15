package com.englishhub.modules.vocabulary.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DictionaryServiceTest {

    @Test
    void normalizeStripsPunctuationAndCase() {
        assertThat(DictionaryService.normalize("  City.  ")).isEqualTo("city");
        assertThat(DictionaryService.normalize("\"Hello,")).isEqualTo("hello");
        assertThat(DictionaryService.normalize("people’s")).isEqualTo("people's");
        assertThat(DictionaryService.normalize("take   off")).isEqualTo("take off");
    }

    @Test
    void candidatesKeepOriginalFirstThenLemmas() {
        assertThat(DictionaryService.candidates("souvenirs")).startsWith("souvenirs").contains("souvenir");
        assertThat(DictionaryService.candidates("cities")).contains("city");
        assertThat(DictionaryService.candidates("studied")).contains("study");
        assertThat(DictionaryService.candidates("stopped")).contains("stop");
        assertThat(DictionaryService.candidates("liked")).contains("like");
        assertThat(DictionaryService.candidates("running")).contains("run");
        assertThat(DictionaryService.candidates("making")).contains("make");
        assertThat(DictionaryService.candidates("linh's")).contains("linh");
        assertThat(DictionaryService.candidates("class")).containsExactly("class");
        assertThat(DictionaryService.candidates("take off")).containsExactly("take off");
    }
}
