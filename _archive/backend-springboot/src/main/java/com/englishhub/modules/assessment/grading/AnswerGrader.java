package com.englishhub.modules.assessment.grading;

import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.entity.QuestionOption;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chấm tự động các dạng câu hỏi khách quan.
 * <ul>
 *   <li>SINGLE/MULTIPLE_CHOICE, ORDERING: đúng hết mới có điểm.</li>
 *   <li>FILL_BLANK, MATCHING: chấm điểm một phần theo tỉ lệ ô/cặp đúng.</li>
 *   <li>ESSAY, SPEAKING: không chấm ở đây (trả về pending, chờ AI/giáo viên).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AnswerGrader {

    private static final TypeReference<List<List<String>>> BLANKS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public GradeResult grade(Question question, AnswerPayload answer, BigDecimal points) {
        if (!question.getQuestionType().isAutoGradable()) {
            return GradeResult.pending();
        }
        if (answer == null) {
            return new GradeResult(false, BigDecimal.ZERO);
        }
        return switch (question.getQuestionType()) {
            case SINGLE_CHOICE -> allOrNothing(isSingleChoiceCorrect(question, answer), points);
            case MULTIPLE_CHOICE -> allOrNothing(isMultipleChoiceCorrect(question, answer), points);
            case ORDERING -> allOrNothing(isOrderingCorrect(question, answer), points);
            case FILL_BLANK -> gradeFillBlank(question, answer, points);
            case MATCHING -> gradeMatching(question, answer, points);
            case SPEAKING, ESSAY -> GradeResult.pending();
        };
    }

    /** Câu trả lời chỉ tham chiếu các phương án của chính câu hỏi (chặn client sửa request). */
    public static boolean referencesOwnOptions(Question question, AnswerPayload payload) {
        Set<Long> valid = question.getOptions().stream().map(QuestionOption::getId).collect(Collectors.toSet());
        List<Long> referenced = new java.util.ArrayList<>();
        Optional.ofNullable(payload.selectedOptionIds()).ifPresent(referenced::addAll);
        Optional.ofNullable(payload.orderedOptionIds()).ifPresent(referenced::addAll);
        Optional.ofNullable(payload.matches()).ifPresent(m -> referenced.addAll(m.keySet()));
        return !referenced.contains(null) && valid.containsAll(referenced);
    }

    /** Số ô trống của câu FILL_BLANK (= số phần tử trong AnswerKey). */
    public int blankCount(Question question) {
        return parseBlanks(question).size();
    }

    /** Danh sách đáp án chấp nhận cho từng ô (dùng khi hiển thị lời giải). */
    public List<List<String>> acceptedBlankAnswers(Question question) {
        return parseBlanks(question);
    }

    private boolean isSingleChoiceCorrect(Question question, AnswerPayload answer) {
        List<Long> selected = answer.selectedOptionIds();
        if (selected == null || selected.size() != 1) {
            return false;
        }
        return correctOptionIds(question).equals(Set.of(selected.get(0)));
    }

    private boolean isMultipleChoiceCorrect(Question question, AnswerPayload answer) {
        List<Long> selected = answer.selectedOptionIds();
        return selected != null && !selected.isEmpty() && correctOptionIds(question).equals(new HashSet<>(selected));
    }

    /** So sánh theo NỘI DUNG từng mảnh (không theo id) để mảnh trùng chữ, vd. hai chữ "the", vẫn chấm đúng. */
    private boolean isOrderingCorrect(Question question, AnswerPayload answer) {
        List<Long> ordered = answer.orderedOptionIds();
        List<QuestionOption> options = question.getOptions();
        if (ordered == null || ordered.size() != options.size()) {
            return false;
        }
        Map<Long, String> contentById = options.stream()
                .collect(Collectors.toMap(QuestionOption::getId, o -> normalize(o.getContent())));
        List<String> expected = options.stream()
                .sorted(Comparator.comparing(QuestionOption::getCorrectOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(o -> normalize(o.getContent()))
                .toList();
        List<String> actual = ordered.stream().map(contentById::get).toList();
        return expected.equals(actual);
    }

    private GradeResult gradeFillBlank(Question question, AnswerPayload answer, BigDecimal points) {
        List<List<String>> blanks = parseBlanks(question);
        List<String> given = Optional.ofNullable(answer.textAnswers()).orElse(List.of());
        int correct = 0;
        for (int i = 0; i < blanks.size(); i++) {
            String value = i < given.size() ? normalize(given.get(i)) : "";
            if (!value.isEmpty() && blanks.get(i).stream().map(AnswerGrader::normalize).anyMatch(value::equals)) {
                correct++;
            }
        }
        return partial(correct, blanks.size(), points);
    }

    private GradeResult gradeMatching(Question question, AnswerPayload answer, BigDecimal points) {
        Map<Long, String> matches = Optional.ofNullable(answer.matches()).orElse(Map.of());
        List<QuestionOption> pairs = question.getOptions();
        long correct = pairs.stream()
                .filter(o -> o.getMatchText() != null)
                .filter(o -> normalize(o.getMatchText()).equals(normalize(matches.get(o.getId()))))
                .count();
        return partial((int) correct, pairs.size(), points);
    }

    private static Set<Long> correctOptionIds(Question question) {
        return question.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::getId)
                .collect(Collectors.toSet());
    }

    private List<List<String>> parseBlanks(Question question) {
        try {
            List<List<String>> blanks = objectMapper.readValue(question.getAnswerKey(), BLANKS_TYPE);
            return blanks.stream().map(b -> b.stream().filter(Objects::nonNull).toList()).toList();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("AnswerKey của câu hỏi " + question.getId() + " không đúng định dạng", e);
        }
    }

    private static GradeResult allOrNothing(boolean correct, BigDecimal points) {
        return new GradeResult(correct, correct ? scale(points) : scale(BigDecimal.ZERO));
    }

    private static GradeResult partial(int correct, int total, BigDecimal points) {
        if (total == 0) {
            return new GradeResult(false, scale(BigDecimal.ZERO));
        }
        BigDecimal score = points.multiply(BigDecimal.valueOf(correct))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new GradeResult(correct == total, score);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Chuẩn hóa để so sánh câu trả lời tự luận ngắn: bỏ khoảng trắng thừa, không phân biệt hoa thường,
     * thống nhất dấu nháy cong/thẳng, bỏ dấu câu ở cuối.
     */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String s = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replace('’', '\'').replace('‘', '\'')
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[.!?]+$", "")
                .trim();
        return s.toLowerCase(Locale.ROOT);
    }
}
