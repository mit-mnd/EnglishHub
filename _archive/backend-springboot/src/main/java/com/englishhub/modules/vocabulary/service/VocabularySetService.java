package com.englishhub.modules.vocabulary.service;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.AddSetResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.SetDetail;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.SetSummary;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.WordResponse;
import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.entity.VocabularySet;
import com.englishhub.modules.vocabulary.entity.VocabularySetWord;
import com.englishhub.modules.vocabulary.entity.Word;
import com.englishhub.modules.vocabulary.repository.UserWordRepository;
import com.englishhub.modules.vocabulary.repository.VocabularySetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bộ từ vựng có sẵn: xem và thêm cả bộ vào sổ từ để ôn bằng flashcard. */
@Service
@RequiredArgsConstructor
public class VocabularySetService {

    private static final int PREVIEW_WORDS = 5;

    private final VocabularySetRepository setRepository;
    private final UserWordRepository userWordRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SetSummary> list(Long userId) {
        List<VocabularySet> sets = setRepository.findPublishedSystemSets();
        if (sets.isEmpty()) {
            return List.of();
        }
        Map<Long, List<VocabularySetWord>> wordsBySet = setRepository
                .findWordsOfSets(sets.stream().map(VocabularySet::getId).toList()).stream()
                .collect(Collectors.groupingBy(sw -> sw.getSet().getId()));
        Map<Long, UserWord> owned = owned(userId, wordsBySet.values().stream().flatMap(List::stream).toList());
        return sets.stream().map(s -> toSummary(s, wordsBySet.getOrDefault(s.getId(), List.of()), owned)).toList();
    }

    @Transactional(readOnly = true)
    public SetDetail get(Long userId, Long setId) {
        VocabularySet set = require(setId);
        List<VocabularySetWord> words = setRepository.findWordsOfSets(List.of(setId));
        Map<Long, UserWord> owned = owned(userId, words);
        return new SetDetail(toSummary(set, words, owned),
                words.stream().map(sw -> WordResponse.of(sw.getWord(), owned.get(sw.getWord().getId()))).toList());
    }

    /** Thêm mọi từ của bộ vào sổ từ (bỏ qua từ đã có). Thẻ mới vào hàng đợi theo giới hạn thẻ mới mỗi ngày. */
    @Transactional
    public AddSetResponse addToNotebook(Long userId, Long setId) {
        require(setId);
        List<VocabularySetWord> words = setRepository.findWordsOfSets(List.of(setId));
        Map<Long, UserWord> owned = owned(userId, words);
        Instant now = clock.instant();
        int added = 0;
        for (VocabularySetWord sw : words) {
            Word word = sw.getWord();
            if (owned.containsKey(word.getId())) {
                continue;
            }
            UserWord uw = new UserWord();
            uw.setUserId(userId);
            uw.setWord(word);
            uw.setSource(UserWord.Source.VOCAB_SET);
            // Lệch nhau 1 ms để thẻ mới được học đúng thứ tự trong bộ
            uw.setDueAt(now.plusMillis(added));
            userWordRepository.save(uw);
            added++;
        }
        return new AddSetResponse(added, words.size() - added);
    }

    private SetSummary toSummary(VocabularySet s, List<VocabularySetWord> words, Map<Long, UserWord> owned) {
        int addedCount = (int) words.stream().filter(sw -> owned.containsKey(sw.getWord().getId())).count();
        return new SetSummary(s.getId(), s.getName(), s.getSlug(), s.getDescription(), s.getLevelId(), words.size(),
                addedCount, words.stream().limit(PREVIEW_WORDS).map(sw -> sw.getWord().getHeadword()).toList());
    }

    private Map<Long, UserWord> owned(Long userId, List<VocabularySetWord> words) {
        if (words.isEmpty()) {
            return Map.of();
        }
        return userWordRepository.findByUserAndWords(userId, words.stream().map(sw -> sw.getWord().getId()).toList())
                .stream().collect(Collectors.toMap(uw -> uw.getWord().getId(), Function.identity()));
    }

    private VocabularySet require(Long setId) {
        return setRepository.findPublishedSystemSet(setId).orElseThrow(() -> BusinessException.notFound("bộ từ", setId));
    }
}
