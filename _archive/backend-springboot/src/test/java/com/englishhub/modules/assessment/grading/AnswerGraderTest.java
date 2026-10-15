package com.englishhub.modules.assessment.grading;

import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.entity.QuestionOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerGraderTest {

    private static final BigDecimal TWO = new BigDecimal("2");

    private final AnswerGrader grader = new AnswerGrader(new ObjectMapper());

    @Test
    void singleChoice_correctOptionGetsFullPoints() {
        Question q = question(QuestionType.SINGLE_CHOICE,
                option(1L, "am", false), option(2L, "is", true), option(3L, "are", false));

        assertThat(grader.grade(q, choice(2L), TWO)).isEqualTo(new GradeResult(true, new BigDecimal("2.00")));
        assertThat(grader.grade(q, choice(1L), TWO)).isEqualTo(new GradeResult(false, new BigDecimal("0.00")));
        assertThat(grader.grade(q, choice(1L, 2L), TWO).correct()).isFalse();
    }

    @Test
    void multipleChoice_requiresExactSet() {
        Question q = question(QuestionType.MULTIPLE_CHOICE,
                option(1L, "a", true), option(2L, "b", true), option(3L, "c", false));

        assertThat(grader.grade(q, choice(2L, 1L), TWO).correct()).isTrue();
        assertThat(grader.grade(q, choice(1L), TWO).correct()).isFalse();
        assertThat(grader.grade(q, choice(1L, 2L, 3L), TWO).correct()).isFalse();
    }

    @Test
    void fillBlank_partialCreditAndNormalization() {
        Question q = question(QuestionType.FILL_BLANK);
        q.setAnswerKey("[[\"drinks\"],[\"is drinking\",\"'s drinking\"]]");

        AnswerPayload bothRight = new AnswerPayload(null, List.of("  Drinks ", "’s   drinking."), null, null, null);
        assertThat(grader.grade(q, bothRight, TWO)).isEqualTo(new GradeResult(true, new BigDecimal("2.00")));

        AnswerPayload oneRight = new AnswerPayload(null, List.of("drinks", "drink"), null, null, null);
        assertThat(grader.grade(q, oneRight, TWO)).isEqualTo(new GradeResult(false, new BigDecimal("1.00")));

        assertThat(grader.blankCount(q)).isEqualTo(2);
    }

    @Test
    void matching_partialCredit() {
        QuestionOption always = option(1L, "always", false);
        always.setMatchText("luôn luôn");
        QuestionOption never = option(2L, "never", false);
        never.setMatchText("không bao giờ");
        Question q = question(QuestionType.MATCHING, always, never);

        AnswerPayload half = new AnswerPayload(null, null, Map.of(1L, "luôn luôn", 2L, "thỉnh thoảng"), null, null);
        assertThat(grader.grade(q, half, TWO)).isEqualTo(new GradeResult(false, new BigDecimal("1.00")));
    }

    @Test
    void ordering_comparesByContentSoDuplicateWordsWork() {
        QuestionOption the1 = option(1L, "the", false);
        the1.setCorrectOrder(1);
        QuestionOption cat = option(2L, "cat", false);
        cat.setCorrectOrder(2);
        QuestionOption ate = option(3L, "ate", false);
        ate.setCorrectOrder(3);
        QuestionOption the2 = option(4L, "the", false);
        the2.setCorrectOrder(4);
        QuestionOption fish = option(5L, "fish", false);
        fish.setCorrectOrder(5);
        Question q = question(QuestionType.ORDERING, fish, the2, ate, cat, the1);

        // Hai chữ "the" đổi chỗ cho nhau vẫn phải được tính đúng
        AnswerPayload swappedThe = new AnswerPayload(null, null, null, List.of(4L, 2L, 3L, 1L, 5L), null);
        assertThat(grader.grade(q, swappedThe, TWO).correct()).isTrue();

        AnswerPayload wrong = new AnswerPayload(null, null, null, List.of(2L, 1L, 3L, 4L, 5L), null);
        assertThat(grader.grade(q, wrong, TWO).correct()).isFalse();
    }

    @Test
    void essayAndUnanswered() {
        assertThat(grader.grade(question(QuestionType.ESSAY), AnswerPayload.empty(), TWO).isGraded()).isFalse();
        Question q = question(QuestionType.SINGLE_CHOICE, option(1L, "x", true));
        assertThat(grader.grade(q, null, TWO)).isEqualTo(new GradeResult(false, BigDecimal.ZERO));
    }

    private static AnswerPayload choice(Long... ids) {
        return new AnswerPayload(List.of(ids), null, null, null, null);
    }

    private static Question question(QuestionType type, QuestionOption... options) {
        Question q = new Question();
        q.setId(100L);
        q.setQuestionType(type);
        for (QuestionOption o : options) {
            o.setQuestion(q);
            q.getOptions().add(o);
        }
        return q;
    }

    private static QuestionOption option(Long id, String content, boolean correct) {
        QuestionOption o = new QuestionOption();
        o.setId(id);
        o.setContent(content);
        o.setCorrect(correct);
        return o;
    }
}
