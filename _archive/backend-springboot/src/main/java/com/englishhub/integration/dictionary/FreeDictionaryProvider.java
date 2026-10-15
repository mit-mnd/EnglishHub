package com.englishhub.integration.dictionary;

import com.englishhub.common.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tra từ qua Free Dictionary API (https://dictionaryapi.dev) - miễn phí, không cần API key.
 * Lỗi mạng / timeout không làm hỏng luồng học: trả về danh sách rỗng và ghi log.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "englishhub.dictionary.provider", havingValue = "free-dictionary", matchIfMissing = true)
public class FreeDictionaryProvider implements DictionaryProvider {

    private static final Set<String> KNOWN_POS = Set.of(
            "noun", "verb", "adjective", "adverb", "pronoun", "preposition", "conjunction", "interjection");
    private static final int MAX_ENTRIES = 3;

    private final RestClient client;

    public FreeDictionaryProvider(AppProperties props, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.dictionary().timeout());
        factory.setReadTimeout(props.dictionary().timeout());
        this.client = builder.baseUrl(props.dictionary().baseUrl()).requestFactory(factory).build();
    }

    @Override
    public List<DictionaryEntry> lookup(String word) {
        try {
            JsonNode root = client.get().uri("/{word}", word).retrieve().body(JsonNode.class);
            return root == null || !root.isArray() || root.isEmpty() ? List.of() : parse(root);
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        } catch (RestClientException e) {
            log.warn("Dictionary API lỗi khi tra '{}': {}", word, e.getMessage());
            return List.of();
        }
    }

    /** Gộp mọi entry trả về; mỗi từ loại lấy định nghĩa đầu tiên và ví dụ đầu tiên tìm được. */
    private static List<DictionaryEntry> parse(JsonNode root) {
        JsonNode first = root.get(0);
        String headword = truncate(first.path("word").asText(), 100);
        String fallbackPhonetic = text(first, "phonetic");
        String phoneticUk = null, phoneticUs = null, audioUk = null, audioUs = null;

        for (JsonNode entry : root) {
            for (JsonNode p : entry.path("phonetics")) {
                String audio = text(p, "audio");
                String phonetic = text(p, "text");
                if (audio != null && audio.contains("-uk.") && audioUk == null) {
                    audioUk = audio;
                    phoneticUk = phonetic != null ? phonetic : phoneticUk;
                } else if (audio != null && audio.contains("-us.") && audioUs == null) {
                    audioUs = audio;
                    phoneticUs = phonetic != null ? phonetic : phoneticUs;
                } else if (phonetic != null && fallbackPhonetic == null) {
                    fallbackPhonetic = phonetic;
                }
            }
        }
        phoneticUk = phoneticUk != null ? phoneticUk : fallbackPhonetic;
        phoneticUs = phoneticUs != null ? phoneticUs : fallbackPhonetic;

        Map<String, String[]> byPos = new LinkedHashMap<>();   // pos -> [definition, example]
        for (JsonNode entry : root) {
            for (JsonNode meaning : entry.path("meanings")) {
                String pos = meaning.path("partOfSpeech").asText("").toLowerCase();
                pos = KNOWN_POS.contains(pos) ? pos : "other";
                String[] slot = byPos.computeIfAbsent(pos, k -> new String[2]);
                for (JsonNode def : meaning.path("definitions")) {
                    if (slot[0] == null) {
                        slot[0] = text(def, "definition");
                    }
                    if (slot[1] == null) {
                        slot[1] = text(def, "example");
                    }
                }
            }
        }

        List<DictionaryEntry> result = new ArrayList<>();
        for (Map.Entry<String, String[]> e : byPos.entrySet()) {
            if (result.size() == MAX_ENTRIES || e.getValue()[0] == null) {
                continue;
            }
            result.add(new DictionaryEntry(headword, e.getKey(), truncate(phoneticUk, 100), truncate(phoneticUs, 100),
                    truncate(audioUk, 500), truncate(audioUs, 500),
                    truncate(e.getValue()[0], 1000), truncate(e.getValue()[1], 1000)));
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
