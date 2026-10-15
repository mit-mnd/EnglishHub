package com.englishhub.integration.ai.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * So khớp câu mẫu với câu nhận dạng được theo từng từ (Levenshtein trên chuỗi từ).
 *
 * <p>Từ viết tắt được tách ra khi so sánh ("I'm" = "I am") để nhận dạng giọng nói trả về dạng nào cũng khớp;
 * kết quả vẫn trả theo đúng từ hiển thị trong câu mẫu.</p>
 */
public final class WordAligner {

    public enum Status { CORRECT, MISPRONOUNCED, OMITTED }

    /** Kết quả cho một từ hiển thị của câu mẫu. */
    public record WordResult(String word, Status status) {
    }

    public record Alignment(List<WordResult> words, int matched, int substituted, int deleted, int inserted,
                            int referenceTokens) {
    }

    private static final Map<String, String> CONTRACTIONS = Map.ofEntries(
            Map.entry("i'm", "i am"), Map.entry("you're", "you are"), Map.entry("we're", "we are"),
            Map.entry("they're", "they are"), Map.entry("he's", "he is"), Map.entry("she's", "she is"),
            Map.entry("it's", "it is"), Map.entry("that's", "that is"), Map.entry("what's", "what is"),
            Map.entry("there's", "there is"), Map.entry("i've", "i have"), Map.entry("you've", "you have"),
            Map.entry("we've", "we have"), Map.entry("i'll", "i will"), Map.entry("you'll", "you will"),
            Map.entry("we'll", "we will"), Map.entry("i'd", "i would"), Map.entry("don't", "do not"),
            Map.entry("doesn't", "does not"), Map.entry("didn't", "did not"), Map.entry("isn't", "is not"),
            Map.entry("aren't", "are not"), Map.entry("wasn't", "was not"), Map.entry("can't", "cannot"),
            Map.entry("won't", "will not"), Map.entry("let's", "let us"));

    private WordAligner() {
    }

    public static Alignment align(String reference, String hypothesis) {
        // Câu mẫu: mỗi từ hiển thị -> 1..n token so sánh
        List<String> display = new ArrayList<>();
        List<String> refTokens = new ArrayList<>();
        List<Integer> owner = new ArrayList<>();
        for (String word : reference.trim().split("\\s+")) {
            List<String> tokens = tokens(word);
            if (tokens.isEmpty()) {
                continue;
            }
            display.add(word);
            for (String t : tokens) {
                refTokens.add(t);
                owner.add(display.size() - 1);
            }
        }
        List<String> hyp = new ArrayList<>();
        if (hypothesis != null) {
            for (String word : hypothesis.trim().split("\\s+")) {
                hyp.addAll(tokens(word));
            }
        }

        // Quy hoạch động khoảng cách chỉnh sửa
        int n = refTokens.size();
        int m = hyp.size();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = refTokens.get(i - 1).equals(hyp.get(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }

        // Truy vết: trạng thái từng token của câu mẫu
        Status[] tokenStatus = new Status[n];
        int matched = 0, substituted = 0, deleted = 0, inserted = 0;
        int i = n, j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + (refTokens.get(i - 1).equals(hyp.get(j - 1)) ? 0 : 1)) {
                boolean same = refTokens.get(i - 1).equals(hyp.get(j - 1));
                tokenStatus[i - 1] = same ? Status.CORRECT : Status.MISPRONOUNCED;
                if (same) matched++; else substituted++;
                i--;
                j--;
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                tokenStatus[i - 1] = Status.OMITTED;
                deleted++;
                i--;
            } else {
                inserted++;
                j--;
            }
        }

        // Gộp token về từ hiển thị: đúng hết = CORRECT, bỏ sót hết = OMITTED, còn lại = MISPRONOUNCED
        List<WordResult> words = new ArrayList<>();
        for (int w = 0; w < display.size(); w++) {
            boolean allCorrect = true, allOmitted = true;
            for (int t = 0; t < n; t++) {
                if (owner.get(t) != w) {
                    continue;
                }
                allCorrect &= tokenStatus[t] == Status.CORRECT;
                allOmitted &= tokenStatus[t] == Status.OMITTED;
            }
            words.add(new WordResult(display.get(w),
                    allCorrect ? Status.CORRECT : allOmitted ? Status.OMITTED : Status.MISPRONOUNCED));
        }
        return new Alignment(words, matched, substituted, deleted, inserted, n);
    }

    /** "Hello," -> [hello]; "I'm" -> [i, am]; "6.50" -> [6.50]. */
    static List<String> tokens(String word) {
        String w = word.toLowerCase(Locale.ROOT).replace('’', '\'')
                .replaceAll("^[^a-z0-9']+|[^a-z0-9']+$", "")
                .replaceAll("^'+|'+$", "");
        if (w.isEmpty()) {
            return List.of();
        }
        String expanded = CONTRACTIONS.getOrDefault(w, w);
        return List.of(expanded.split(" "));
    }
}
