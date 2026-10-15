package com.englishhub.integration.ai.writing;

import com.englishhub.integration.ai.writing.WritingEvaluator.Issue;
import com.englishhub.integration.ai.writing.WritingEvaluator.WritingFeedback;
import com.englishhub.integration.ai.writing.WritingEvaluator.WritingInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedWritingEvaluatorTest {

    private final RuleBasedWritingEvaluator evaluator = new RuleBasedWritingEvaluator();

    private List<Issue> issuesOf(String text) {
        return evaluator.evaluate(new WritingInput(text, null, null, null)).issues();
    }

    @Test
    void detectsCommonLearnerMistakesWithSuggestions() {
        String text = "last weekend i goed to a beach with my freind. She have a umbrella and we swimmed .";
        WritingFeedback f = evaluator.evaluate(new WritingInput(text, null, null, null));

        assertThat(f.issues()).extracting(Issue::original)
                .contains("l", "i", "goed", "freind", "have", "a", "swimmed", " .");
        assertThat(f.correctedText())
                .isEqualTo("Last weekend I went to a beach with my friend. She has an umbrella and we swam.");
    }

    @Test
    void doesNotFlagCorrectSentences() {
        assertThat(issuesOf("Does she have an hour? I am a university student and it is an honest answer.")).isEmpty();
    }

    @Test
    void flagsRepeatedWordsSpacingAndMissingFinalPunctuation() {
        List<Issue> issues = issuesOf("I like the the park,it is  big");
        assertThat(issues).extracting(Issue::type).contains("GRAMMAR", "PUNCTUATION", "STYLE");
        assertThat(issues).anyMatch(i -> "big.".equals(i.suggestion()));
    }

    @Test
    void scoresWordCountAgainstRequirement() {
        String shortText = "I went to the park. It was fun.";
        WritingFeedback f = evaluator.evaluate(new WritingInput(shortText, null, 80, 120));
        assertThat(f.wordCount()).isEqualTo(8);
        assertThat(f.criteria().taskAchievement()).isLessThan(20);
        assertThat(f.improvements()).anyMatch(s -> s.contains("8/80"));
    }

    @Test
    void wellWrittenParagraphScoresHigh() {
        String text = "Last weekend was wonderful. First, I visited my grandparents in the countryside. "
                + "Then we cooked a delicious lunch together and talked about our family. "
                + "In the afternoon, my cousins and I went fishing near the river, but we did not catch anything. "
                + "However, we laughed a lot because my little brother fell into the water. "
                + "Finally, we returned home in the evening, tired but happy. "
                + "I hope I can visit them again soon because the fresh air and quiet villages always help me relax "
                + "after a busy week at school.";
        WritingFeedback f = evaluator.evaluate(new WritingInput(text, null, 80, 120));
        assertThat(f.issues()).isEmpty();
        assertThat(f.score()).isGreaterThanOrEqualTo(90);
        assertThat(f.strengths()).isNotEmpty();
    }

    @Test
    void emptyTextScoresZero() {
        assertThat(evaluator.evaluate(new WritingInput("   ", null, 50, 100)).score()).isZero();
    }
}
