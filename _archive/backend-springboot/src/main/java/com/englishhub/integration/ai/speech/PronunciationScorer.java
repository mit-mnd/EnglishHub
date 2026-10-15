package com.englishhub.integration.ai.speech;

import java.util.List;

/**
 * Cổng chấm phát âm. Bản mặc định là Mock; có thể thay bằng Azure Speech (đã có adapter) hoặc dịch vụ khác
 * mà không sửa nghiệp vụ - chỉ đổi cấu hình {@code englishhub.ai.speech.provider}.
 */
public interface PronunciationScorer {

    PronunciationResult score(SpeechInput input);

    /**
     * @param audio           WAV PCM 16 kHz mono (trình duyệt ghi sẵn đúng định dạng)
     * @param durationMs      thời lượng bản ghi (đọc từ header WAV)
     * @param clientTranscript văn bản trình duyệt tự nhận dạng được (Web Speech API) - có thể null
     */
    record SpeechInput(byte[] audio, String contentType, long durationMs, String referenceText, String clientTranscript) {
    }

    /** errorType: NONE | MISPRONUNCIATION | OMISSION | INSERTION | UNKNOWN. accuracy null nếu không xác định. */
    record WordScore(String word, Integer accuracy, String errorType) {
    }

    /**
     * Thang điểm 0-100. {@code estimated = true}: không có dữ liệu nhận dạng, điểm chỉ mang tính ước lượng
     * (khi đó accuracyScore = null).
     */
    record PronunciationResult(
            String provider,
            boolean estimated,
            String recognizedText,
            double pronunciationScore,
            Double accuracyScore,
            double fluencyScore,
            double completenessScore,
            List<WordScore> words,
            String rawJson) {
    }
}
