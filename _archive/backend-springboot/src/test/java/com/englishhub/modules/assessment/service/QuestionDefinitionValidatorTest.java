package com.englishhub.modules.assessment.service;

import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.service.QuestionDefinitionValidator.InvalidQuestionException;
import com.englishhub.modules.assessment.service.QuestionDefinitionValidator.OptionDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionDefinitionValidatorTest {

    private final QuestionDefinitionValidator validator = new QuestionDefinitionValidator(new ObjectMapper());

    private static OptionDef opt(String content, boolean correct) {
        return new OptionDef(content, correct, null, null);
    }

    @Test
    void singleChoiceNeedsExactlyOneCorrect() {
        assertThat(validator.validate(QuestionType.SINGLE_CHOICE, "Q", null, List.of(opt("a", true), opt("b", false)))).isNull();
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CHOICE, "Q", null,
                List.of(opt("a", true), opt("b", true)))).isInstanceOf(InvalidQuestionException.class);
        assertThatThrownBy(() -> validator.validate(QuestionType.SINGLE_CHOICE, "Q", null, List.of(opt("a", true))))
                .hasMessageContaining("2 đến 8");
        assertThatThrownBy(() -> validator.validate(QuestionType.MULTIPLE_CHOICE, "Q", null,
                List.of(opt("A", true), opt(" a ", false)))).hasMessageContaining("trùng");
    }

    @Test
    void fillBlankChecksPlaceholdersAgainstAnswerKey() {
        String key = validator.validate(QuestionType.FILL_BLANK, "She {{1}} tea, he {{2}} coffee.",
                "[[\" drinks \", \"\"], [\"drank\", \"drank\"]]", List.of());
        assertThat(key).isEqualTo("[[\"drinks\"],[\"drank\"]]");

        assertThatThrownBy(() -> validator.validate(QuestionType.FILL_BLANK, "No blank", "[[\"x\"]]", List.of()))
                .hasMessageContaining("{{1}}");
        assertThatThrownBy(() -> validator.validate(QuestionType.FILL_BLANK, "{{1}} and {{3}}", "[[\"a\"],[\"b\"]]", List.of()))
                .hasMessageContaining("liên tiếp");
        assertThatThrownBy(() -> validator.validate(QuestionType.FILL_BLANK, "{{1}} {{2}}", "[[\"a\"]]", List.of()))
                .hasMessageContaining("đủ 2");
        assertThatThrownBy(() -> validator.validate(QuestionType.FILL_BLANK, "{{1}}", "went", List.of()))
                .hasMessageContaining("JSON");
        assertThatThrownBy(() -> validator.validate(QuestionType.FILL_BLANK, "{{1}}", "[[\" \"]]", List.of()))
                .hasMessageContaining("chưa có đáp án");
    }

    @Test
    void orderingNeedsPermutation() {
        List<OptionDef> ok = List.of(new OptionDef("I", false, null, 1), new OptionDef("am", false, null, 2),
                new OptionDef("here", false, null, 3));
        assertThat(validator.validate(QuestionType.ORDERING, "Q", null, ok)).isNull();
        List<OptionDef> dup = List.of(new OptionDef("I", false, null, 1), new OptionDef("am", false, null, 1));
        assertThatThrownBy(() -> validator.validate(QuestionType.ORDERING, "Q", null, dup)).hasMessageContaining("1..2");
    }

    @Test
    void matchingNeedsRightSide() {
        List<OptionDef> missing = List.of(new OptionDef("cat", false, "con mèo", null), new OptionDef("dog", false, " ", null));
        assertThatThrownBy(() -> validator.validate(QuestionType.MATCHING, "Q", null, missing)).hasMessageContaining("vế phải");
    }

    @Test
    void speakingNeedsReferenceAndEssayNeedsNothing() {
        assertThat(validator.validate(QuestionType.SPEAKING, "Read", "  Hello there.  ", List.of())).isEqualTo("Hello there.");
        assertThatThrownBy(() -> validator.validate(QuestionType.SPEAKING, "Read", " ", List.of())).hasMessageContaining("câu mẫu");
        assertThat(validator.validate(QuestionType.ESSAY, "Write", "ignored", List.of())).isNull();
        assertThatThrownBy(() -> validator.validate(QuestionType.ESSAY, "Write", null, List.of(opt("a", true))))
                .hasMessageContaining("không dùng phương án");
    }
}
