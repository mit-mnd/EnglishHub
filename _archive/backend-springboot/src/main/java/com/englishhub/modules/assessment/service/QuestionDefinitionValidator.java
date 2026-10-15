package com.englishhub.modules.assessment.service;

import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm tra cấu trúc câu hỏi theo từng dạng trước khi lưu, để bộ chấm ({@code AnswerGrader})
 * và màn làm bài luôn nhận dữ liệu hợp lệ:
 * <ul>
 *   <li>SINGLE_CHOICE: 2-8 phương án, đúng 1 phương án đúng.</li>
 *   <li>MULTIPLE_CHOICE: 2-8 phương án, ít nhất 1 phương án đúng.</li>
 *   <li>FILL_BLANK: ô trống {{1}}..{{n}} liên tiếp trong đề; AnswerKey là JSON n mảng đáp án chấp nhận.</li>
 *   <li>MATCHING: 2-10 cặp, vế trái không trùng, vế phải bắt buộc.</li>
 *   <li>ORDERING: 2-10 mảnh, CorrectOrder là hoán vị của 1..n.</li>
 *   <li>SPEAKING: AnswerKey là câu mẫu cần đọc; ESSAY: không có đáp án cố định.</li>
 * </ul>
 * Trả về AnswerKey đã chuẩn hóa (JSON gọn / văn bản đã trim / null).
 */
@Component
public final class QuestionDefinitionValidator {

    public record OptionDef(String content, boolean correct, String matchText, Integer correctOrder) {
    }

    public static final class InvalidQuestionException extends RuntimeException {
        public InvalidQuestionException(String message) {
            super(message);
        }
    }

    private static final Pattern BLANK = Pattern.compile("\\{\\{(\\d+)}}");
    private static final TypeReference<List<List<String>>> BLANKS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public QuestionDefinitionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String validate(QuestionType type, String content, String answerKey, List<OptionDef> options) {
        List<OptionDef> opts = options == null ? List.of() : options;
        return switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                requireCount(opts, 2, 8, "phương án");
                requireDistinct(opts.stream().map(OptionDef::content).toList(), "Các phương án không được trùng nhau");
                long correct = opts.stream().filter(OptionDef::correct).count();
                if (type == QuestionType.SINGLE_CHOICE && correct != 1) {
                    throw new InvalidQuestionException("Câu một đáp án phải có đúng 1 phương án đúng");
                }
                if (correct < 1) {
                    throw new InvalidQuestionException("Cần ít nhất 1 phương án đúng");
                }
                yield null;
            }
            case FILL_BLANK -> {
                noOptions(opts);
                yield validateFillBlank(content, answerKey);
            }
            case MATCHING -> {
                requireCount(opts, 2, 10, "cặp nối");
                requireDistinct(opts.stream().map(OptionDef::content).toList(), "Vế trái của các cặp không được trùng nhau");
                if (opts.stream().anyMatch(o -> o.matchText() == null || o.matchText().isBlank())) {
                    throw new InvalidQuestionException("Mỗi cặp nối cần có vế phải");
                }
                yield null;
            }
            case ORDERING -> {
                requireCount(opts, 2, 10, "mảnh");
                Set<Integer> orders = new TreeSet<>();
                for (OptionDef o : opts) {
                    if (o.correctOrder() == null || o.correctOrder() < 1 || o.correctOrder() > opts.size() || !orders.add(o.correctOrder())) {
                        throw new InvalidQuestionException("Thứ tự đúng phải là các số 1.." + opts.size() + " không trùng nhau");
                    }
                }
                yield null;
            }
            case SPEAKING -> {
                noOptions(opts);
                if (answerKey == null || answerKey.isBlank()) {
                    throw new InvalidQuestionException("Câu luyện nói cần câu mẫu (đáp án) để chấm phát âm");
                }
                if (answerKey.trim().length() > 1000) {
                    throw new InvalidQuestionException("Câu mẫu luyện nói tối đa 1000 ký tự");
                }
                yield answerKey.trim();
            }
            case ESSAY -> {
                noOptions(opts);
                yield null;
            }
        };
    }

    private String validateFillBlank(String content, String answerKey) {
        Set<Integer> numbers = new TreeSet<>();
        Matcher m = BLANK.matcher(content == null ? "" : content);
        while (m.find()) {
            numbers.add(Integer.parseInt(m.group(1)));
        }
        if (numbers.isEmpty()) {
            throw new InvalidQuestionException("Đề bài cần ít nhất 1 ô trống dạng {{1}}");
        }
        int n = numbers.size();
        if (!numbers.equals(rangeSet(n))) {
            throw new InvalidQuestionException("Các ô trống phải đánh số liên tiếp {{1}}..{{" + n + "}}");
        }
        List<List<String>> blanks;
        try {
            blanks = answerKey == null ? null : objectMapper.readValue(answerKey, BLANKS);
        } catch (JsonProcessingException e) {
            throw new InvalidQuestionException("Đáp án phải là JSON dạng [[\"went\"],[\"has been\",\"'s been\"]]");
        }
        if (blanks == null || blanks.size() != n) {
            throw new InvalidQuestionException("Cần đáp án cho đủ " + n + " ô trống");
        }
        List<List<String>> cleaned = new ArrayList<>();
        for (int i = 0; i < blanks.size(); i++) {
            List<String> accepted = blanks.get(i) == null ? List.of() : blanks.get(i).stream()
                    .filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
            if (accepted.isEmpty()) {
                throw new InvalidQuestionException("Ô trống {{" + (i + 1) + "}} chưa có đáp án");
            }
            cleaned.add(accepted);
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<Integer> rangeSet(int n) {
        Set<Integer> s = new TreeSet<>();
        for (int i = 1; i <= n; i++) {
            s.add(i);
        }
        return s;
    }

    private static void requireCount(List<OptionDef> opts, int min, int max, String what) {
        if (opts.size() < min || opts.size() > max) {
            throw new InvalidQuestionException("Cần từ " + min + " đến " + max + " " + what);
        }
        if (opts.stream().anyMatch(o -> o.content() == null || o.content().isBlank())) {
            throw new InvalidQuestionException("Nội dung " + what + " không được để trống");
        }
    }

    private static void requireDistinct(List<String> values, String message) {
        Set<String> seen = new HashSet<>();
        for (String v : values) {
            if (!seen.add(v.trim().toLowerCase(Locale.ROOT))) {
                throw new InvalidQuestionException(message);
            }
        }
    }

    private static void noOptions(List<OptionDef> opts) {
        if (!opts.isEmpty()) {
            throw new InvalidQuestionException("Dạng câu hỏi này không dùng phương án lựa chọn");
        }
    }
}
