package com.englishhub.modules.vocabulary.service;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.integration.dictionary.DictionaryProvider;
import com.englishhub.integration.dictionary.DictionaryProvider.DictionaryEntry;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.LookupResponse;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.WordResponse;
import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.entity.Word;
import com.englishhub.modules.vocabulary.repository.UserWordRepository;
import com.englishhub.modules.vocabulary.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tra từ cho màn hình đọc: ưu tiên kho từ nội bộ (có nghĩa tiếng Việt), không có mới gọi từ điển ngoài
 * rồi cache vào bảng Words để lần sau tra nhanh.
 */
@Service
@RequiredArgsConstructor
public class DictionaryService {

    /** Chữ cái, nháy đơn, gạch nối, khoảng trắng; tối đa 4 từ (cụm từ ngắn). */
    private static final Pattern VALID_QUERY = Pattern.compile("^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,3}$");

    private final WordRepository wordRepository;
    private final UserWordRepository userWordRepository;
    private final DictionaryProvider dictionaryProvider;

    @Transactional
    public LookupResponse lookup(Long userId, String rawQuery) {
        String query = normalize(rawQuery);
        if (query.length() > 60 || !VALID_QUERY.matcher(query).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Từ cần tra không hợp lệ");
        }
        List<String> candidates = candidates(query);

        // 1. Kho nội bộ: lấy ứng viên đầu tiên (theo thứ tự ưu tiên) có kết quả
        List<Word> local = wordRepository.findByHeadwordInOrderByIdAsc(candidates);
        for (String candidate : candidates) {
            List<Word> hits = local.stream().filter(w -> w.getHeadword().equalsIgnoreCase(candidate)).toList();
            if (!hits.isEmpty()) {
                return new LookupResponse(query, candidate, "LOCAL", toResponses(userId, hits));
            }
        }

        // 2. Từ điển ngoài: thử từ gốc tra được và tối đa 1 dạng rút gọn (tránh gọi API quá nhiều)
        for (String candidate : candidates.subList(0, Math.min(2, candidates.size()))) {
            List<DictionaryEntry> entries = dictionaryProvider.lookup(candidate);
            if (!entries.isEmpty()) {
                List<Word> saved = entries.stream().map(this::cache).toList();
                return new LookupResponse(query, saved.get(0).getHeadword(), "DICTIONARY_API", toResponses(userId, saved));
            }
        }
        return new LookupResponse(query, null, "NONE", List.of());
    }

    @Transactional(readOnly = true)
    public Word requireWord(Long wordId) {
        return wordRepository.findById(wordId).orElseThrow(() -> new BusinessException(ErrorCode.WORD_NOT_FOUND));
    }

    /** Lưu mục từ lấy từ dịch vụ ngoài (nếu chưa có cùng headword + từ loại). Nghĩa tiếng Việt để trống. */
    private Word cache(DictionaryEntry entry) {
        return wordRepository.findByHeadwordAndPartOfSpeech(entry.headword(), entry.partOfSpeech())
                .orElseGet(() -> {
                    Word word = new Word();
                    word.setHeadword(entry.headword());
                    word.setPartOfSpeech(entry.partOfSpeech());
                    word.setPhoneticUk(entry.phoneticUk());
                    word.setPhoneticUs(entry.phoneticUs());
                    word.setAudioUrlUk(entry.audioUrlUk());
                    word.setAudioUrlUs(entry.audioUrlUs());
                    word.setDefinitionEn(entry.definitionEn());
                    word.setExample(entry.example());
                    word.setSource(Word.Source.DICTIONARY_API);
                    return wordRepository.save(word);
                });
    }

    private List<WordResponse> toResponses(Long userId, List<Word> words) {
        Map<Long, UserWord> saved = userWordRepository.findByUserAndWords(userId, words.stream().map(Word::getId).toList())
                .stream().collect(Collectors.toMap(uw -> uw.getWord().getId(), Function.identity()));
        return words.stream().map(w -> WordResponse.of(w, saved.get(w.getId()))).toList();
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("^[^a-z]+|[^a-z]+$", "")    // bỏ dấu câu ở hai đầu: "city." -> "city"
                .replaceAll("\\s+", " ");
    }

    /**
     * Dạng từ gốc có thể có (lemmatization đơn giản theo quy tắc), giữ thứ tự ưu tiên:
     * từ nguyên gốc trước. Ví dụ: "souvenirs" -> [souvenirs, souvenir]; "stopped" -> [stopped, stopp, stop, stope].
     * Ứng viên sai không gây hại vì chỉ dùng để tìm trong kho từ.
     */
    static List<String> candidates(String word) {
        Set<String> result = new LinkedHashSet<>();
        result.add(word);
        if (word.contains(" ")) {
            return new ArrayList<>(result);
        }
        if (word.endsWith("'s")) {
            result.add(word.substring(0, word.length() - 2));
        }
        if (word.endsWith("ies") && word.length() > 4) {
            result.add(word.substring(0, word.length() - 3) + "y");
        }
        if (word.endsWith("es") && word.length() > 3) {
            result.add(word.substring(0, word.length() - 2));
        }
        if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) {
            result.add(word.substring(0, word.length() - 1));
        }
        if (word.endsWith("ied") && word.length() > 4) {
            result.add(word.substring(0, word.length() - 3) + "y");
        }
        if (word.endsWith("ed") && word.length() > 3) {
            String stem = word.substring(0, word.length() - 2);
            result.add(stem);
            result.add(undouble(stem));
            result.add(stem + "e");
        }
        if (word.endsWith("ing") && word.length() > 5) {
            String stem = word.substring(0, word.length() - 3);
            result.add(stem);
            result.add(undouble(stem));
            result.add(stem + "e");
        }
        result.removeIf(s -> s.length() < 2);
        return new ArrayList<>(result);
    }

    /** "stopp" -> "stop", "runn" -> "run" (phụ âm cuối bị nhân đôi khi thêm -ed/-ing). */
    private static String undouble(String stem) {
        int n = stem.length();
        return n > 2 && stem.charAt(n - 1) == stem.charAt(n - 2) ? stem.substring(0, n - 1) : stem;
    }
}
