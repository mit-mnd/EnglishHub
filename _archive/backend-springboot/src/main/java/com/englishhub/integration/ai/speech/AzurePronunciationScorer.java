package com.englishhub.integration.ai.speech;

import com.englishhub.common.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chấm phát âm bằng Azure AI Speech - Pronunciation Assessment (REST API cho audio ngắn, tối đa ~30-60 giây).
 * Bật bằng: AI_SPEECH_PROVIDER=azure, AZURE_SPEECH_KEY, AZURE_SPEECH_REGION.
 *
 * <p>Audio phải là WAV PCM 16 kHz mono - frontend đã ghi đúng định dạng này nên không cần chuyển đổi.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "englishhub.ai.speech.provider", havingValue = "azure")
public class AzurePronunciationScorer implements PronunciationScorer {

    private static final String PROVIDER = "AZURE";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String key;

    public AzurePronunciationScorer(AppProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        AppProperties.Ai.Speech cfg = props.ai().speech();
        if (cfg.azureKey() == null || cfg.azureKey().isBlank() || cfg.azureRegion() == null || cfg.azureRegion().isBlank()) {
            throw new IllegalStateException("AI_SPEECH_PROVIDER=azure cần AZURE_SPEECH_KEY và AZURE_SPEECH_REGION");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.timeout());
        factory.setReadTimeout(cfg.timeout());
        this.client = builder
                .baseUrl("https://" + cfg.azureRegion() + ".stt.speech.microsoft.com")
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.key = cfg.azureKey();
    }

    @Override
    public PronunciationResult score(SpeechInput input) {
        String config = toJson(Map.of(
                "ReferenceText", input.referenceText(),
                "GradingSystem", "HundredMark",
                "Granularity", "Word",
                "Dimension", "Comprehensive",
                "EnableMiscue", true));
        String raw = client.post()
                .uri("/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed")
                .header("Ocp-Apim-Subscription-Key", key)
                .header("Pronunciation-Assessment", Base64.getEncoder().encodeToString(config.getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                .header("Accept", "application/json")
                .body(input.audio())
                .retrieve()
                .body(String.class);
        return parse(raw);
    }

    /** Hỗ trợ cả 2 dạng phản hồi: điểm nằm trong NBest[0].PronunciationAssessment hoặc trực tiếp trong NBest[0]. */
    private PronunciationResult parse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!"Success".equals(root.path("RecognitionStatus").asText())) {
                throw new IllegalStateException("Azure không nhận dạng được giọng nói: " + root.path("RecognitionStatus").asText());
            }
            JsonNode best = root.path("NBest").path(0);
            JsonNode pa = best.has("PronunciationAssessment") ? best.path("PronunciationAssessment") : best;
            List<WordScore> words = new ArrayList<>();
            for (JsonNode w : best.path("Words")) {
                JsonNode wpa = w.has("PronunciationAssessment") ? w.path("PronunciationAssessment") : w;
                words.add(new WordScore(w.path("Word").asText(),
                        wpa.has("AccuracyScore") ? (int) Math.round(wpa.path("AccuracyScore").asDouble()) : null,
                        wpa.path("ErrorType").asText("None").toUpperCase(Locale.ROOT)));
            }
            return new PronunciationResult(PROVIDER, false, root.path("DisplayText").asText(null),
                    pa.path("PronScore").asDouble(), pa.path("AccuracyScore").asDouble(),
                    pa.path("FluencyScore").asDouble(), pa.path("CompletenessScore").asDouble(), words, raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Phản hồi Azure không hợp lệ", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
