package com.englishhub.modules.vocabulary.service;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.common.util.SlugUtils;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.cms.ContentUsageRepository;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.AdminSetDetail;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.AdminSetRow;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.AdminWord;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.SetRequest;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.SetWordView;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.WordRequest;
import com.englishhub.modules.vocabulary.entity.VocabularySet;
import com.englishhub.modules.vocabulary.entity.VocabularySetWord;
import com.englishhub.modules.vocabulary.entity.Word;
import com.englishhub.modules.vocabulary.repository.VocabularySetRepository;
import com.englishhub.modules.vocabulary.repository.WordRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Quản trị kho từ (Words) và bộ từ vựng hệ thống (VocabularySets không có chủ sở hữu). */
@Service
@RequiredArgsConstructor
public class AdminVocabularyService {

    private static final Set<String> WORD_SORTS = Set.of("headword", "createdAt", "updatedAt");
    private static final Set<String> SET_SORTS = Set.of("name", "createdAt", "updatedAt");

    private final WordRepository wordRepository;
    private final VocabularySetRepository setRepository;
    private final LevelRepository levelRepository;
    private final ContentUsageRepository usage;
    private final EntityManager entityManager;

    // ------------------------------------------------------------------ Kho từ

    @Transactional(readOnly = true)
    public PageResponse<AdminWord> searchWords(String keyword, Integer levelId, String partOfSpeech, Word.Source source,
                                               Long excludeSetId, Pageable pageable) {
        Page<Word> page = wordRepository.adminSearch(PageableUtils.likePattern(keyword), levelId, blankToNull(partOfSpeech),
                source, excludeSetId, PageableUtils.requireSortable(pageable, WORD_SORTS));
        // Số học viên đã lưu từ không cần ở danh sách (đỡ N truy vấn); xem ở chi tiết
        return PageResponse.of(page, w -> toWord(w, -1));
    }

    @Transactional(readOnly = true)
    public AdminWord getWord(Long id) {
        return toWord(findWord(id), usage.wordLearners(id));
    }

    @Transactional
    public AdminWord createWord(WordRequest request) {
        Word w = new Word();
        w.setSource(Word.Source.ADMIN);
        applyWord(w, request);
        wordRepository.save(w);
        return getWord(w.getId());
    }

    @Transactional
    public AdminWord updateWord(Long id, WordRequest request) {
        Word w = findWord(id);
        applyWord(w, request);
        wordRepository.flush();
        return getWord(id);
    }

    /** Chỉ xóa từ chưa có học viên nào lưu vào sổ (giữ lịch sử ôn tập). Từ trong bộ từ sẽ tự gỡ khỏi bộ. */
    @Transactional
    public void deleteWord(Long id) {
        Word w = findWord(id);
        long learners = usage.wordLearners(id);
        if (learners > 0) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, learners + " học viên đã lưu từ này vào sổ nên không thể xóa");
        }
        wordRepository.delete(w);
    }

    private void applyWord(Word w, WordRequest r) {
        String headword = r.headword().trim();
        boolean duplicate = w.getId() == null
                ? wordRepository.existsByHeadwordAndPartOfSpeech(headword, r.partOfSpeech())
                : wordRepository.existsByHeadwordAndPartOfSpeechAndIdNot(headword, r.partOfSpeech(), w.getId());
        if (duplicate) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Từ '" + headword + "' (" + r.partOfSpeech() + ") đã có trong kho");
        }
        requireLevel(r.levelId());
        w.setHeadword(headword);
        w.setPartOfSpeech(r.partOfSpeech());
        w.setPhoneticUk(blankToNull(r.phoneticUk()));
        w.setPhoneticUs(blankToNull(r.phoneticUs()));
        w.setAudioUrlUk(blankToNull(r.audioUrlUk()));
        w.setAudioUrlUs(blankToNull(r.audioUrlUs()));
        w.setMeaningVi(r.meaningVi().trim());
        w.setDefinitionEn(blankToNull(r.definitionEn()));
        w.setExample(blankToNull(r.example()));
        w.setExampleTranslation(blankToNull(r.exampleTranslation()));
        w.setImageUrl(blankToNull(r.imageUrl()));
        w.setLevelId(r.levelId());
    }

    private Word findWord(Long id) {
        return wordRepository.findById(id).orElseThrow(() -> BusinessException.notFound("từ vựng", id));
    }

    private static AdminWord toWord(Word w, long learners) {
        return new AdminWord(w.getId(), w.getHeadword(), w.getPartOfSpeech(), w.getPhoneticUk(), w.getPhoneticUs(),
                w.getAudioUrlUk(), w.getAudioUrlUs(), w.getMeaningVi(), w.getDefinitionEn(), w.getExample(),
                w.getExampleTranslation(), w.getImageUrl(), w.getLevelId(), w.getSource(), learners, w.getUpdatedAt());
    }

    // ------------------------------------------------------------------ Bộ từ

    @Transactional(readOnly = true)
    public PageResponse<AdminSetRow> searchSets(String keyword, Integer levelId, Boolean published, Pageable pageable) {
        Page<VocabularySet> page = setRepository.adminSearch(PageableUtils.likePattern(keyword), levelId, published,
                PageableUtils.requireSortable(pageable, SET_SORTS));
        Map<Long, Long> counts = new HashMap<>();
        if (page.hasContent()) {
            for (Object[] row : setRepository.countWords(page.getContent().stream().map(VocabularySet::getId).toList())) {
                counts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return PageResponse.of(page, s -> new AdminSetRow(s.getId(), s.getName(), s.getSlug(), s.getLevelId(),
                s.isPublished(), counts.getOrDefault(s.getId(), 0L), s.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminSetDetail getSet(Long id) {
        VocabularySet s = findSet(id);
        List<SetWordView> words = setRepository.findWordsOfSets(List.of(id)).stream()
                .map(sw -> {
                    Word w = sw.getWord();
                    return new SetWordView(w.getId(), w.getHeadword(), w.getPartOfSpeech(),
                            w.getPhoneticUs() != null ? w.getPhoneticUs() : w.getPhoneticUk(), w.getMeaningVi(), sw.getSortOrder());
                })
                .toList();
        return new AdminSetDetail(s.getId(), s.getName(), s.getSlug(), s.getDescription(), s.getLevelId(),
                s.getCoverImageUrl(), s.isPublished(), s.getUpdatedAt(), words);
    }

    @Transactional
    public AdminSetDetail createSet(SetRequest request) {
        VocabularySet s = new VocabularySet();
        applySet(s, request, 0);
        setRepository.save(s);
        return getSet(s.getId());
    }

    @Transactional
    public AdminSetDetail updateSet(Long id, SetRequest request) {
        VocabularySet s = findSet(id);
        applySet(s, request, setRepository.findWordsOfSets(List.of(id)).size());
        setRepository.flush();
        return getSet(id);
    }

    /** Thay danh sách từ của bộ theo thứ tự gửi lên. Từ học viên đã thêm vào sổ vẫn giữ nguyên. */
    @Transactional
    public AdminSetDetail replaceSetWords(Long id, List<Long> wordIds) {
        VocabularySet s = findSet(id);
        Set<Long> unique = new HashSet<>(wordIds);
        if (unique.size() != wordIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh sách từ bị lặp");
        }
        if (s.isPublished() && wordIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bộ từ đang xuất bản phải có ít nhất 1 từ");
        }
        Map<Long, Word> words = wordRepository.findAllById(unique).stream().collect(Collectors.toMap(Word::getId, Function.identity()));
        for (Long wordId : wordIds) {
            if (!words.containsKey(wordId)) {
                throw BusinessException.notFound("từ vựng", wordId);
            }
        }
        setRepository.deleteWordsOfSet(id);
        VocabularySet managed = findSet(id);   // context đã được clear sau lệnh xóa hàng loạt
        for (int i = 0; i < wordIds.size(); i++) {
            VocabularySetWord sw = new VocabularySetWord();
            sw.setId(new VocabularySetWord.Key(id, wordIds.get(i)));
            sw.setSet(managed);
            sw.setWord(entityManager.getReference(Word.class, wordIds.get(i)));
            sw.setSortOrder(i + 1);
            entityManager.persist(sw);
        }
        entityManager.flush();
        return getSet(id);
    }

    @Transactional
    public AdminSetDetail setSetPublished(Long id, boolean published) {
        VocabularySet s = findSet(id);
        if (published && setRepository.findWordsOfSets(List.of(id)).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thêm từ vào bộ trước khi xuất bản");
        }
        s.setPublished(published);
        setRepository.flush();
        return getSet(id);
    }

    /** Xóa bộ từ: chỉ gỡ liên kết, các từ trong kho và sổ từ của học viên vẫn giữ nguyên. */
    @Transactional
    public void deleteSet(Long id) {
        setRepository.delete(findSet(id));
    }

    private void applySet(VocabularySet s, SetRequest r, int wordCount) {
        requireLevel(r.levelId());
        String slug = r.slug() == null || r.slug().isBlank()
                ? (s.getSlug() != null && r.name().equals(s.getName()) ? s.getSlug()
                    : SlugUtils.unique(r.name(), x -> slugTaken(x, s.getId())))
                : r.slug();
        if (slugTaken(slug, s.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Slug '" + slug + "' đã được dùng cho bộ từ khác");
        }
        if (r.published() && wordCount == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thêm từ vào bộ trước khi xuất bản");
        }
        s.setName(r.name().trim());
        s.setSlug(slug);
        s.setDescription(blankToNull(r.description()));
        s.setLevelId(r.levelId());
        s.setCoverImageUrl(blankToNull(r.coverImageUrl()));
        s.setPublished(r.published());
    }

    private boolean slugTaken(String slug, Long selfId) {
        return selfId == null ? setRepository.existsBySlug(slug) : setRepository.existsBySlugAndIdNot(slug, selfId);
    }

    private VocabularySet findSet(Long id) {
        return setRepository.findSystemSet(id).orElseThrow(() -> BusinessException.notFound("bộ từ vựng", id));
    }

    private void requireLevel(Integer levelId) {
        if (levelId != null && !levelRepository.existsById(levelId)) {
            throw BusinessException.notFound("trình độ", levelId);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
