package com.englishhub.modules.vocabulary.service;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.SaveWordRequest;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.UserWordResponse;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.WordResponse;
import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.entity.Word;
import com.englishhub.modules.vocabulary.repository.UserWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Sổ từ vựng cá nhân (từ lưu khi đọc bài, thêm tay...). Ôn tập flashcard SRS ở module sau dùng lại bảng này. */
@Service
@RequiredArgsConstructor
public class UserWordService {

    private static final Set<String> SORTS = Set.of("createdAt", "dueAt", "status");

    private final UserWordRepository userWordRepository;
    private final DictionaryService dictionaryService;
    private final LessonRepository lessonRepository;
    private final Clock clock;

    /** Idempotent: từ đã có trong sổ thì trả về bản ghi cũ (chỉ cập nhật ghi chú nếu có gửi). */
    @Transactional
    public UserWordResponse save(Long userId, SaveWordRequest request) {
        Word word = dictionaryService.requireWord(request.wordId());
        UserWord userWord = userWordRepository.findByUserIdAndWordId(userId, word.getId()).orElseGet(() -> {
            UserWord created = new UserWord();
            created.setUserId(userId);
            created.setWord(word);
            created.setSource(request.sourceLessonId() != null ? UserWord.Source.READING_LOOKUP : UserWord.Source.MANUAL);
            created.setSourceLessonId(request.sourceLessonId() != null
                    && lessonRepository.existsById(request.sourceLessonId()) ? request.sourceLessonId() : null);
            created.setContextSentence(blankToNull(request.contextSentence()));
            created.setDueAt(clock.instant());     // từ mới: đến hạn ôn ngay
            return userWordRepository.save(created);
        });
        if (request.note() != null) {
            userWord.setNote(blankToNull(request.note()));
        }
        return toResponse(userWord, lessonTitles(List.of(userWord)));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserWordResponse> list(Long userId, String keyword, UserWord.Status status, Pageable pageable) {
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<UserWord> page = userWordRepository.search(userId, status, pattern, PageableUtils.requireSortable(pageable, SORTS));
        Map<Long, String> titles = lessonTitles(page.getContent());
        return PageResponse.of(page, uw -> toResponse(uw, titles));
    }

    @Transactional
    public UserWordResponse updateNote(Long userId, Long id, String note) {
        UserWord userWord = require(userId, id);
        userWord.setNote(blankToNull(note));
        return toResponse(userWord, lessonTitles(List.of(userWord)));
    }

    /** Tạm ẩn từ khỏi lịch ôn (đã biết rõ / không muốn học) hoặc đưa trở lại. */
    @Transactional
    public UserWordResponse setSuspended(Long userId, Long id, boolean suspended) {
        UserWord userWord = require(userId, id);
        if (suspended) {
            userWord.setStatus(UserWord.Status.SUSPENDED);
        } else if (userWord.getStatus() == UserWord.Status.SUSPENDED) {
            FlashcardService.restoreFromSuspended(userWord, clock.instant());
        }
        return toResponse(userWord, lessonTitles(List.of(userWord)));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        userWordRepository.delete(require(userId, id));
    }

    @Transactional(readOnly = true)
    public List<String> savedHeadwords(Long userId) {
        return userWordRepository.findSavedHeadwords(userId);
    }

    private UserWord require(Long userId, Long id) {
        return userWordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> BusinessException.notFound("từ trong sổ", id));
    }

    private Map<Long, String> lessonTitles(List<UserWord> words) {
        Set<Long> ids = words.stream().map(UserWord::getSourceLessonId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of()
                : lessonRepository.findAllById(ids).stream().collect(Collectors.toMap(Lesson::getId, Lesson::getTitle));
    }

    private static UserWordResponse toResponse(UserWord uw, Map<Long, String> lessonTitles) {
        return new UserWordResponse(uw.getId(), WordResponse.of(uw.getWord(), uw), uw.getSource(), uw.getStatus(),
                uw.getNote(), uw.getContextSentence(), uw.getSourceLessonId(),
                uw.getSourceLessonId() == null ? null : lessonTitles.get(uw.getSourceLessonId()),
                uw.getDueAt(), uw.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
