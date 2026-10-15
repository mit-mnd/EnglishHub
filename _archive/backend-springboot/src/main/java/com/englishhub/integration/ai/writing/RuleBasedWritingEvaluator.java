package com.englishhub.integration.ai.writing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chấm bài viết theo quy tắc - không cần dịch vụ ngoài, phù hợp trình độ A1-B1.
 *
 * <p>Phát hiện: lỗi chính tả thường gặp, "i" viết thường, chữ đầu câu viết thường, lặp từ, a/an,
 * hòa hợp chủ ngữ - động từ cơ bản, khoảng trắng/dấu câu, câu quá dài, thiếu dấu kết thúc.</p>
 *
 * <p>Điểm tổng = 30% hoàn thành yêu cầu (số từ) + 35% ngữ pháp/chính tả (lỗi trên 100 từ)
 * + 20% từ vựng (độ đa dạng) + 15% mạch lạc (từ nối, số câu).</p>
 */
@Component
@ConditionalOnProperty(name = "englishhub.ai.writing.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedWritingEvaluator implements WritingEvaluator {

    static final String PROVIDER = "RULE_BASED";

    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*");

    private static final Map<String, String> MISSPELLINGS = Map.ofEntries(
            Map.entry("recieve", "receive"), Map.entry("becuase", "because"), Map.entry("beacuse", "because"),
            Map.entry("definately", "definitely"), Map.entry("alot", "a lot"), Map.entry("wich", "which"),
            Map.entry("thier", "their"), Map.entry("untill", "until"), Map.entry("tommorow", "tomorrow"),
            Map.entry("tomorow", "tomorrow"), Map.entry("accomodation", "accommodation"), Map.entry("beleive", "believe"),
            Map.entry("goverment", "government"), Map.entry("enviroment", "environment"), Map.entry("seperate", "separate"),
            Map.entry("occured", "occurred"), Map.entry("freind", "friend"), Map.entry("wierd", "weird"),
            Map.entry("realy", "really"), Map.entry("finaly", "finally"), Map.entry("beautifull", "beautiful"),
            Map.entry("wonderfull", "wonderful"), Map.entry("intresting", "interesting"), Map.entry("diffrent", "different"),
            Map.entry("resturant", "restaurant"), Map.entry("wether", "whether"),
            Map.entry("whit", "with"), Map.entry("becouse", "because"), Map.entry("studing", "studying"),
            Map.entry("comming", "coming"), Map.entry("writting", "writing"), Map.entry("goed", "went"),
            Map.entry("buyed", "bought"), Map.entry("eated", "ate"), Map.entry("swimmed", "swam"),
            Map.entry("teached", "taught"), Map.entry("thinked", "thought"), Map.entry("catched", "caught"));

    /** Từ bắt đầu bằng nguyên âm nhưng phát âm phụ âm (dùng "a"). */
    private static final List<String> CONSONANT_SOUND_PREFIXES =
            List.of("uni", "use", "usu", "uti", "ure", "euro", "eu", "one", "once", "ewe", "ufo");
    /** Từ bắt đầu bằng "h" câm (dùng "an"). */
    private static final List<String> SILENT_H_PREFIXES = List.of("hour", "honest", "honor", "honour", "heir");

    /** Trợ động từ đứng trước -> "she have" là đúng (does she have, can she have...). */
    private static final Set<String> AUXILIARIES = Set.of("does", "did", "doesn't", "didn't", "will", "would", "can",
            "could", "should", "may", "might", "must", "to", "let", "make", "makes", "made", "help", "helps");

    private static final Set<String> LINKERS = Set.of("first", "firstly", "second", "then", "next", "after", "finally",
            "however", "because", "so", "also", "but", "although", "moreover", "furthermore", "therefore", "besides",
            "later", "meanwhile", "while", "since", "example", "instance", "addition", "conclusion", "overall", "lastly");

    private static final Set<String> STOPWORDS = Set.of("the", "a", "an", "and", "or", "but", "to", "of", "in", "on",
            "at", "for", "with", "is", "are", "was", "were", "be", "i", "you", "he", "she", "it", "we", "they", "my",
            "your", "his", "her", "our", "their", "this", "that", "very", "so", "then", "have", "has", "had", "do", "did");

    @Override
    public WritingFeedback evaluate(WritingInput input) {
        String text = input.text() == null ? "" : input.text();
        List<String> words = words(text);
        int wordCount = words.size();
        int sentenceCount = countSentences(text);

        if (wordCount == 0) {
            return new WritingFeedback(PROVIDER, 0, new Criteria(0, 0, 0, 0), 0, 0, List.of(), text, List.of(),
                    List.of("Bạn chưa viết nội dung nào."));
        }

        List<Issue> issues = resolveOverlaps(findIssues(text));
        int task = taskScore(wordCount, input.minWords(), input.maxWords());
        long errors = issues.stream().filter(i -> !i.type().equals("STYLE")).count();
        // Lỗi viết hoa / dấu câu nhẹ hơn lỗi ngữ pháp / chính tả (tính nửa lỗi)
        double weightedErrors = issues.stream().mapToDouble(i -> switch (i.type()) {
            case "GRAMMAR", "SPELLING" -> 1.0;
            case "CAPITALIZATION", "PUNCTUATION" -> 0.5;
            default -> 0.0;
        }).sum();
        int grammar = clamp((int) Math.round(100 - weightedErrors * 100.0 / wordCount * 5), 20, 100);
        int vocabulary = vocabularyScore(words);
        int coherence = coherenceScore(words, sentenceCount);
        int score = (int) Math.round(task * 0.30 + grammar * 0.35 + vocabulary * 0.20 + coherence * 0.15);

        Criteria criteria = new Criteria(task, grammar, vocabulary, coherence);
        return new WritingFeedback(PROVIDER, score, criteria, wordCount, sentenceCount, issues,
                applyCorrections(text, issues), strengths(criteria, errors),
                improvements(criteria, wordCount, input.minWords(), input.maxWords(), issues));
    }

    // ------------------------------------------------------------------ phát hiện lỗi

    private static List<Issue> findIssues(String text) {
        List<Issue> issues = new ArrayList<>();

        // Chính tả thường gặp (kể cả quá khứ sai của động từ bất quy tắc)
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            String fix = MISSPELLINGS.get(m.group().toLowerCase(Locale.ROOT));
            if (fix != null && !fix.equalsIgnoreCase(m.group())) {
                boolean irregular = m.group().toLowerCase(Locale.ROOT).endsWith("ed");
                issues.add(new Issue(m.start(), m.end(), irregular ? "GRAMMAR" : "SPELLING", m.group(),
                        matchCase(m.group(), fix),
                        irregular ? "Động từ bất quy tắc: dạng quá khứ là \"" + fix + "\"" : "Sai chính tả"));
            }
        }

        // Đại từ "I" luôn viết hoa (i, i'm, i've...)
        m = Pattern.compile("(?<![A-Za-z'])i(?![A-Za-z])").matcher(text);
        while (m.find()) {
            issues.add(new Issue(m.start(), m.end(), "CAPITALIZATION", "i", "I", "Đại từ \"I\" luôn viết hoa"));
        }

        // Chữ cái đầu câu phải viết hoa
        m = Pattern.compile("(?:^|[.!?]\\s+)([a-z])").matcher(text);
        while (m.find()) {
            String letter = m.group(1);
            issues.add(new Issue(m.start(1), m.end(1), "CAPITALIZATION", letter, letter.toUpperCase(Locale.ROOT),
                    "Chữ cái đầu câu cần viết hoa"));
        }

        // Lặp từ liền nhau: "the the"
        m = Pattern.compile("\\b([A-Za-z]+)\\s+\\1\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            if (!m.group(1).equalsIgnoreCase("had") && !m.group(1).equalsIgnoreCase("that")) {
                issues.add(new Issue(m.start(), m.end(), "GRAMMAR", m.group(), m.group(1), "Lặp từ \"" + m.group(1) + "\""));
            }
        }

        // a / an theo âm đầu của từ phía sau
        m = Pattern.compile("\\b(a|an)\\s+([A-Za-z]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            String article = m.group(1);
            boolean vowelSound = startsWithVowelSound(m.group(2));
            if (article.equalsIgnoreCase("a") && vowelSound) {
                issues.add(new Issue(m.start(1), m.end(1), "GRAMMAR", article, matchCase(article, "an"),
                        "Dùng \"an\" trước từ bắt đầu bằng nguyên âm: an " + m.group(2)));
            } else if (article.equalsIgnoreCase("an") && !vowelSound) {
                issues.add(new Issue(m.start(1), m.end(1), "GRAMMAR", article, matchCase(article, "a"),
                        "Dùng \"a\" trước từ bắt đầu bằng phụ âm: a " + m.group(2)));
            }
        }

        // Hòa hợp chủ ngữ - động từ cơ bản
        subjectVerb(text, issues, "(he|she|it)\\s+(don't)", "doesn't", true);
        subjectVerb(text, issues, "(he|she|it)\\s+(have)", "has", true);
        subjectVerb(text, issues, "(he|she|it)\\s+(are)", "is", false);
        subjectVerb(text, issues, "(i)\\s+(is|are)", "am", false);
        subjectVerb(text, issues, "(you|we|they)\\s+(is)", "are", false);
        subjectVerb(text, issues, "(you|we|they)\\s+(was)", "were", false);
        subjectVerb(text, issues, "(i|you|we|they)\\s+(has)", "have", false);
        subjectVerb(text, issues, "(i|you|we|they)\\s+(doesn't)", "don't", false);

        // Khoảng trắng & dấu câu
        m = Pattern.compile("(?<=\\S) {2,}(?=\\S)").matcher(text);
        while (m.find()) {
            issues.add(new Issue(m.start(), m.end(), "STYLE", m.group(), " ", "Thừa khoảng trắng"));
        }
        m = Pattern.compile("\\s+([,.!?;:])").matcher(text);
        while (m.find()) {
            issues.add(new Issue(m.start(), m.end(), "PUNCTUATION", m.group(), m.group(1), "Không đặt khoảng trắng trước dấu câu"));
        }
        m = Pattern.compile("([,;:!?])(?=[A-Za-z])").matcher(text);
        while (m.find()) {
            issues.add(new Issue(m.start(), m.end(), "PUNCTUATION", m.group(), m.group(1) + " ", "Cần khoảng trắng sau dấu câu"));
        }

        // Câu quá dài (khó đọc)
        Matcher sentence = Pattern.compile("[^.!?]+[.!?]*").matcher(text);
        while (sentence.find()) {
            if (words(sentence.group()).size() > 35) {
                issues.add(new Issue(sentence.start(), sentence.end(), "STYLE", sentence.group().trim(), null,
                        "Câu quá dài (> 35 từ), nên tách thành 2-3 câu ngắn"));
            }
        }

        // Thiếu dấu kết thúc ở cuối bài
        String trimmed = text.stripTrailing();
        if (!trimmed.isEmpty() && !trimmed.matches("(?s).*[.!?\"')]$")) {
            Matcher last = Pattern.compile("[A-Za-z']+$").matcher(trimmed);
            if (last.find()) {
                issues.add(new Issue(last.start(), last.end(), "PUNCTUATION", last.group(), last.group() + ".",
                        "Thiếu dấu chấm kết thúc câu"));
            }
        }
        return issues;
    }

    /** Thêm lỗi hòa hợp chủ ngữ - động từ; bỏ qua khi phía trước có trợ động từ ("does she have" là đúng). */
    private static void subjectVerb(String text, List<Issue> issues, String regex, String fix, boolean checkAuxiliary) {
        Matcher m = Pattern.compile("\\b" + regex + "\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            if (checkAuxiliary && AUXILIARIES.contains(previousWord(text, m.start()))) {
                continue;
            }
            issues.add(new Issue(m.start(2), m.end(2), "GRAMMAR", m.group(2), fix,
                    "Chủ ngữ \"" + m.group(1) + "\" đi với \"" + fix + "\""));
        }
    }

    private static String previousWord(String text, int index) {
        Matcher m = Pattern.compile("([A-Za-z']+)\\s*$").matcher(text.substring(0, index));
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
    }

    static boolean startsWithVowelSound(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (SILENT_H_PREFIXES.stream().anyMatch(w::startsWith)) {
            return true;
        }
        if (CONSONANT_SOUND_PREFIXES.stream().anyMatch(w::startsWith)) {
            return false;
        }
        return "aeiou".indexOf(w.charAt(0)) >= 0;
    }

    /** Giữ lỗi xuất hiện trước khi hai lỗi chồng lên cùng một đoạn văn bản. */
    private static List<Issue> resolveOverlaps(List<Issue> issues) {
        List<Issue> sorted = new ArrayList<>(issues);
        sorted.sort(Comparator.comparingInt(Issue::start).thenComparingInt(i -> -(i.end() - i.start())));
        List<Issue> result = new ArrayList<>();
        int lastEnd = -1;
        for (Issue issue : sorted) {
            if (issue.start() >= lastEnd) {
                result.add(issue);
                lastEnd = issue.end();
            }
        }
        return result;
    }

    /** Áp các gợi ý sửa từ cuối lên đầu để vị trí của các lỗi phía trước không bị lệch. */
    static String applyCorrections(String text, List<Issue> issues) {
        StringBuilder sb = new StringBuilder(text);
        issues.stream()
                .filter(i -> i.suggestion() != null)
                .sorted(Comparator.comparingInt(Issue::start).reversed())
                .forEach(i -> sb.replace(i.start(), i.end(), i.suggestion()));
        return sb.toString();
    }

    // ------------------------------------------------------------------ chấm điểm

    private static int taskScore(int wordCount, Integer min, Integer max) {
        if (min == null) {
            return wordCount >= 20 ? 100 : wordCount * 5;
        }
        if (wordCount < min) {
            return Math.max(10, (int) Math.round(100.0 * wordCount / min));
        }
        if (max != null && wordCount > max * 1.2) {
            return 80;
        }
        return 100;
    }

    private static int vocabularyScore(List<String> words) {
        Set<String> unique = new HashSet<>();
        int content = 0;
        double totalLength = 0;
        for (String w : words) {
            String lower = w.toLowerCase(Locale.ROOT);
            totalLength += lower.length();
            if (!STOPWORDS.contains(lower) && lower.length() >= 3) {
                content++;
                unique.add(lower);
            }
        }
        double variety = content == 0 ? 0 : (double) unique.size() / content;
        double avgLength = totalLength / words.size();
        int score = (int) Math.round(40 + variety * 55 + (avgLength > 4.5 ? 5 : 0));
        return clamp(score, 30, 100);
    }

    private static int coherenceScore(List<String> words, int sentences) {
        long linkers = words.stream().map(w -> w.toLowerCase(Locale.ROOT)).filter(LINKERS::contains).distinct().count();
        int score = linkers == 0 ? 55 : linkers == 1 ? 70 : linkers == 2 ? 85 : 100;
        if (words.size() >= 40 && sentences < 3) {
            score -= 10;
        }
        return clamp(score, 30, 100);
    }

    private static List<String> strengths(Criteria c, long errors) {
        List<String> list = new ArrayList<>();
        if (c.taskAchievement() == 100) list.add("Bài viết đáp ứng yêu cầu về độ dài.");
        if (errors == 0) list.add("Không phát hiện lỗi ngữ pháp/chính tả cơ bản — rất tốt!");
        else if (c.grammar() >= 80) list.add("Ngữ pháp và chính tả khá chắc chắn.");
        if (c.vocabulary() >= 80) list.add("Vốn từ đa dạng, ít lặp từ.");
        if (c.coherence() >= 85) list.add("Dùng từ nối hợp lý, ý văn mạch lạc.");
        return list;
    }

    private static List<String> improvements(Criteria c, int wordCount, Integer min, Integer max, List<Issue> issues) {
        List<String> list = new ArrayList<>();
        if (min != null && wordCount < min) {
            list.add("Bài viết còn ngắn: " + wordCount + "/" + min + " từ tối thiểu. Hãy bổ sung thêm chi tiết, ví dụ.");
        } else if (max != null && wordCount > max * 1.2) {
            list.add("Bài viết dài hơn yêu cầu (" + wordCount + " từ, tối đa ~" + max + "). Hãy viết súc tích hơn.");
        }
        long spelling = issues.stream().filter(i -> i.type().equals("SPELLING")).count();
        long grammar = issues.stream().filter(i -> i.type().equals("GRAMMAR")).count();
        if (spelling > 0) list.add("Kiểm tra lại chính tả (" + spelling + " lỗi).");
        if (grammar > 0) list.add("Chú ý ngữ pháp: chia động từ, mạo từ a/an (" + grammar + " lỗi).");
        if (issues.stream().anyMatch(i -> i.type().equals("CAPITALIZATION"))) list.add("Viết hoa chữ đầu câu và đại từ \"I\".");
        if (c.coherence() < 70) list.add("Thêm từ nối (first, then, after that, finally, because, however...) để bài mạch lạc hơn.");
        if (c.vocabulary() < 65) list.add("Đa dạng từ vựng hơn, tránh lặp lại một từ nhiều lần.");
        return list;
    }

    // ------------------------------------------------------------------ tiện ích

    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            words.add(m.group());
        }
        return words;
    }

    private static int countSentences(String text) {
        int count = 0;
        for (String part : text.split("[.!?]+")) {
            if (WORD.matcher(part).find()) {
                count++;
            }
        }
        return count;
    }

    private static String matchCase(String original, String replacement) {
        return Character.isUpperCase(original.charAt(0))
                ? Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1) : replacement;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
