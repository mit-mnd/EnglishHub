package com.englishhub.modules.game.service;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.modules.game.entity.GameResult;
import com.englishhub.modules.game.entity.GameResult.GameType;
import com.englishhub.modules.game.repository.GameResultRepository;
import com.englishhub.modules.progress.service.ActivityService;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.entity.Word;
import com.englishhub.modules.vocabulary.repository.UserWordRepository;
import com.englishhub.modules.vocabulary.repository.WordRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Trò chơi từ vựng: lấy bộ từ để chơi (ưu tiên từ trong sổ của học viên) và lưu kết quả.
 * Điểm do client tính (trò chơi luyện tập, không xếp hạng công khai nên chấp nhận được);
 * server chỉ kiểm tra giới hạn hợp lệ và ghi nhận thời gian học.
 */
@Service
@RequiredArgsConstructor
public class GameService {

    public record GameWord(Long wordId, String headword, String partOfSpeech, String phonetic, String audioUrl,
                           String meaning, String example, boolean fromNotebook) {
    }

    public record ResultRequest(
            @NotNull GameType gameType,
            @NotNull @Min(0) @Max(100) Integer score,
            @NotNull @Min(0) @Max(50) Integer correctCount,
            @NotNull @Min(1) @Max(50) Integer totalCount,
            @NotNull @Min(0) @Max(3600) Integer durationSeconds) {
    }

    public record ResultResponse(Long id, int score, int bestScore, boolean personalBest, Integer previousBest) {
    }

    public record GameStats(GameType gameType, long plays, Integer bestScore, Instant lastPlayedAt) {
    }

    /** Từ dùng cho xếp chữ / nghe chép: 1 từ đơn, 3-12 chữ cái. */
    private static final Predicate<String> SINGLE_WORD = h -> h.matches("[A-Za-z]{3,12}");
    private static final int NOTEBOOK_SCAN = 200;
    private static final int MAX_STUDY_SECONDS = 900;

    private final UserWordRepository userWordRepository;
    private final WordRepository wordRepository;
    private final GameResultRepository resultRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public List<GameWord> words(Long userId, GameType type, int requested) {
        int count = Math.max(4, Math.min(requested, 20));
        Predicate<String> accept = type == GameType.MATCHING ? h -> true : SINGLE_WORD;

        List<UserWord> notebook = new ArrayList<>(userWordRepository.findActiveForGames(userId, PageRequest.of(0, NOTEBOOK_SCAN)));
        Collections.shuffle(notebook);
        Map<String, GameWord> picked = new LinkedHashMap<>();   // khóa = headword chữ thường, tránh trùng từ
        for (UserWord uw : notebook) {
            addIfAccepted(picked, uw.getWord(), accept, true, count);
        }
        if (picked.size() < count) {
            // Sổ từ còn ít: bổ sung từ kho chung ở trình độ hiện tại trở xuống
            User user = userRepository.findWithDetailsById(userId).orElseThrow(() -> BusinessException.notFound("người dùng", userId));
            int maxLevel = user.getCurrentLevel() == null ? 2 : user.getCurrentLevel().getId();
            for (Word w : wordRepository.findRandomForGames(maxLevel, count * 3)) {
                addIfAccepted(picked, w, accept, false, count);
            }
        }
        List<GameWord> result = new ArrayList<>(picked.values());
        Collections.shuffle(result);
        return result;
    }

    @Transactional
    public ResultResponse submit(Long userId, ResultRequest request) {
        if (request.correctCount() > request.totalCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số câu đúng không thể lớn hơn tổng số câu");
        }
        Integer previousBest = resultRepository.findBestScore(userId, request.gameType()).orElse(null);
        GameResult result = new GameResult();
        result.setUserId(userId);
        result.setGameType(request.gameType());
        result.setScore(request.score());
        result.setCorrectCount(request.correctCount());
        result.setTotalCount(request.totalCount());
        result.setDurationSeconds(request.durationSeconds());
        resultRepository.save(result);

        if (request.durationSeconds() > 0) {
            activityService.recordStudyTime(userId, Math.min(request.durationSeconds(), MAX_STUDY_SECONDS));
        }
        boolean best = previousBest == null || request.score() > previousBest;
        return new ResultResponse(result.getId(), request.score(), best ? request.score() : previousBest,
                best && request.score() > 0, previousBest);
    }

    @Transactional(readOnly = true)
    public List<GameStats> stats(Long userId) {
        Map<GameType, GameStats> byType = new EnumMap<>(GameType.class);
        for (GameType t : GameType.values()) {
            byType.put(t, new GameStats(t, 0, null, null));
        }
        for (Object[] row : resultRepository.statsByType(userId)) {
            GameType type = (GameType) row[0];
            byType.put(type, new GameStats(type, ((Number) row[1]).longValue(), (Integer) row[2], (Instant) row[3]));
        }
        return List.copyOf(byType.values());
    }

    private static void addIfAccepted(Map<String, GameWord> picked, Word w, Predicate<String> accept, boolean fromNotebook,
                                      int limit) {
        String key = w.getHeadword().toLowerCase();
        String meaning = w.getMeaningVi() != null && !w.getMeaningVi().isBlank() ? w.getMeaningVi() : w.getDefinitionEn();
        if (picked.size() >= limit || picked.containsKey(key) || meaning == null || !accept.test(w.getHeadword())) {
            return;
        }
        picked.put(key, new GameWord(w.getId(), w.getHeadword(), w.getPartOfSpeech(),
                w.getPhoneticUs() != null ? w.getPhoneticUs() : w.getPhoneticUk(),
                w.getAudioUrlUs() != null ? w.getAudioUrlUs() : w.getAudioUrlUk(), meaning, w.getExample(), fromNotebook));
    }
}
