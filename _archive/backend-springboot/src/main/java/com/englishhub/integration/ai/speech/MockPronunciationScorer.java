package com.englishhub.integration.ai.speech;

import com.englishhub.integration.ai.speech.WordAligner.Alignment;
import com.englishhub.integration.ai.speech.WordAligner.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Bộ chấm "Mock" dùng khi chưa cấu hình dịch vụ AI thật. Không phân tích âm thanh, nhưng vẫn cho phản hồi có ích:
 * <ul>
 *   <li>Có văn bản trình duyệt nhận dạng được (Chrome/Edge): so khớp từng từ với câu mẫu
 *       -> độ chính xác, độ đầy đủ; độ trôi chảy tính từ tốc độ nói (từ/phút).</li>
 *   <li>Không có: chỉ ước lượng từ thời lượng ghi âm so với độ dài câu (estimated = true, tối đa 80 điểm).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "englishhub.ai.speech.provider", havingValue = "mock", matchIfMissing = true)
public class MockPronunciationScorer implements PronunciationScorer {

    static final String PROVIDER = "MOCK";
    /** Tốc độ nói tự nhiên của người học (từ/phút). */
    private static final double MIN_WPM = 80, MAX_WPM = 170;

    @Override
    public PronunciationResult score(SpeechInput input) {
        boolean hasTranscript = input.clientTranscript() != null && !input.clientTranscript().isBlank();
        return hasTranscript ? scoreWithTranscript(input) : estimateFromDuration(input);
    }

    private PronunciationResult scoreWithTranscript(SpeechInput input) {
        Alignment a = WordAligner.align(input.referenceText(), input.clientTranscript());
        int spoken = a.matched() + a.substituted();
        double completeness = pct(spoken, a.referenceTokens());
        double accuracy = spoken == 0 ? 0 : pct(a.matched(), spoken);

        double minutes = Math.max(input.durationMs(), 1) / 60000.0;
        double wpm = spoken / minutes;
        double fluency = wpm < MIN_WPM ? 100 - (MIN_WPM - wpm) * 1.2
                : wpm > MAX_WPM ? 100 - (wpm - MAX_WPM) * 0.8 : 100;
        fluency = clamp(fluency - a.inserted() * 5, 30, 100);    // từ thừa ~ ngập ngừng, lặp lại

        double pron = accuracy * 0.5 + completeness * 0.3 + fluency * 0.2;
        List<WordScore> words = a.words().stream()
                .map(w -> new WordScore(w.word(), switch (w.status()) {
                    case CORRECT -> 95;
                    case MISPRONOUNCED -> 45;
                    case OMITTED -> 0;
                }, w.status() == Status.CORRECT ? "NONE" : w.status() == Status.OMITTED ? "OMISSION" : "MISPRONUNCIATION"))
                .toList();
        return new PronunciationResult(PROVIDER, false, input.clientTranscript().trim(), round(pron), round(accuracy),
                round(fluency), round(completeness), words, null);
    }

    private PronunciationResult estimateFromDuration(SpeechInput input) {
        String[] display = input.referenceText().trim().split("\\s+");
        double expectedMs = display.length * 450.0 + 600;     // ~2,2 từ/giây + khoảng lặng đầu
        double ratio = input.durationMs() / expectedMs;
        double completeness = ratio < 1 ? clamp(ratio * 100, 0, 100) : ratio <= 2.5 ? 100 : 70;
        double fluency = ratio >= 0.7 && ratio <= 1.8 ? 85 : 60;
        double pron = Math.min(80, completeness * 0.6 + fluency * 0.4);
        List<WordScore> words = Arrays.stream(display).map(w -> new WordScore(w, null, "UNKNOWN")).toList();
        return new PronunciationResult(PROVIDER, true, null, round(pron), null, round(fluency), round(completeness),
                words, null);
    }

    private static double pct(int part, int total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
