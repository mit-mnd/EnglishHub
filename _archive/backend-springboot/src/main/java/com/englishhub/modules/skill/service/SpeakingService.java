package com.englishhub.modules.skill.service;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.integration.ai.speech.PronunciationScorer;
import com.englishhub.integration.ai.speech.PronunciationScorer.PronunciationResult;
import com.englishhub.integration.ai.speech.PronunciationScorer.SpeechInput;
import com.englishhub.integration.ai.speech.PronunciationScorer.WordScore;
import com.englishhub.integration.ai.speech.WavInfo;
import com.englishhub.integration.storage.FileStorageService;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.repository.QuestionRepository;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.skill.dto.SkillDtos.SpeakingRequest;
import com.englishhub.modules.skill.dto.SkillDtos.SpeakingResultResponse;
import com.englishhub.modules.skill.entity.SpeakingSubmission;
import com.englishhub.modules.skill.repository.SpeakingSubmissionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Luyện nói: nhận file WAV, lưu lại, gọi {@link PronunciationScorer} và trả kết quả kèm gợi ý luyện tập.
 * Bản ghi âm chỉ chủ sở hữu nghe lại được (không public).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingService {

    private static final long MIN_DURATION_MS = 500;
    private static final long MAX_DURATION_MS = 60_000;

    private final SpeakingSubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;
    private final LessonRepository lessonRepository;
    private final PronunciationScorer scorer;
    private final FileStorageService storage;
    private final ObjectMapper objectMapper;

    /** Không rollback khi chấm lỗi: vẫn lưu bản ghi FAILED để truy vết. */
    @Transactional(noRollbackFor = BusinessException.class)
    public SpeakingResultResponse submit(Long userId, byte[] audio, SpeakingRequest request) {
        long durationMs = WavInfo.durationMs(audio == null ? new byte[0] : audio)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUDIO, "File ghi âm phải ở định dạng WAV"));
        if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS) {
            throw new BusinessException(ErrorCode.INVALID_AUDIO, "Bản ghi âm phải dài từ 0,5 đến 60 giây");
        }
        String reference = resolveReferenceText(request);
        if (request.lessonId() != null && !lessonRepository.existsById(request.lessonId())) {
            throw BusinessException.notFound("bài học", request.lessonId());
        }

        SpeakingSubmission submission = new SpeakingSubmission();
        submission.setUserId(userId);
        submission.setLessonId(request.lessonId());
        submission.setQuestionId(request.questionId());
        submission.setReferenceText(reference);
        submission.setAudioUrl(storage.store("speaking/" + userId, "wav", audio));
        submission.setProvider(scorer.getClass().getSimpleName());
        submissionRepository.save(submission);

        try {
            PronunciationResult result = scorer.score(new SpeechInput(audio, "audio/wav", durationMs, reference,
                    request.clientTranscript()));
            submission.setProvider(result.provider());
            submission.setRecognizedText(truncate(result.recognizedText(), 1000));
            submission.setPronunciationScore(decimal(result.pronunciationScore()));
            submission.setAccuracyScore(result.accuracyScore() == null ? null : decimal(result.accuracyScore()));
            submission.setFluencyScore(decimal(result.fluencyScore()));
            submission.setCompletenessScore(decimal(result.completenessScore()));
            submission.setDetailJson(toJson(Map.of("estimated", result.estimated(), "words", result.words())));
            submission.setStatus(SpeakingSubmission.Status.SCORED);
        } catch (RuntimeException e) {
            log.warn("Chấm phát âm thất bại cho submission {}: {}", submission.getId(), e.getMessage());
            submission.setStatus(SpeakingSubmission.Status.FAILED);
            submission.setErrorMessage(truncate(e.getMessage(), 500));
            throw new BusinessException(ErrorCode.SPEECH_SCORING_FAILED);
        }
        return toResponse(submission);
    }

    @Transactional(readOnly = true)
    public SpeakingResultResponse get(Long userId, Long id) {
        return toResponse(require(userId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SpeakingResultResponse> history(Long userId, Long lessonId, Pageable pageable) {
        var page = lessonId == null
                ? submissionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : submissionRepository.findByUserIdAndLessonIdOrderByCreatedAtDesc(userId, lessonId, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public byte[] audio(Long userId, Long id) {
        return storage.read(require(userId, id).getAudioUrl());
    }

    /** Dùng cho bài tập: lấy bài nộp đã chấm của chính học viên. */
    @Transactional(readOnly = true)
    public SpeakingSubmission requireScored(Long userId, Long id) {
        SpeakingSubmission submission = require(userId, id);
        if (submission.getStatus() != SpeakingSubmission.Status.SCORED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bản ghi âm chưa được chấm");
        }
        return submission;
    }

    private String resolveReferenceText(SpeakingRequest request) {
        if (request.questionId() != null) {
            Question question = questionRepository.findById(request.questionId())
                    .filter(q -> q.isActive() && q.getQuestionType() == QuestionType.SPEAKING)
                    .orElseThrow(() -> BusinessException.notFound("câu luyện nói", request.questionId()));
            return question.getAnswerKey();
        }
        if (request.referenceText() == null || request.referenceText().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu câu mẫu cần đọc");
        }
        return request.referenceText().trim();
    }

    private SpeakingSubmission require(Long userId, Long id) {
        return submissionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> BusinessException.notFound("bản ghi âm", id));
    }

    public SpeakingResultResponse toResponse(SpeakingSubmission s) {
        Detail detail = parseDetail(s.getDetailJson());
        return new SpeakingResultResponse(s.getId(), s.getReferenceText(), s.getRecognizedText(), s.getProvider(),
                detail.estimated(), s.getPronunciationScore(), s.getAccuracyScore(), s.getFluencyScore(),
                s.getCompletenessScore(), detail.words(), tips(s, detail),
                "/api/v1/speaking/submissions/" + s.getId() + "/audio", s.getCreatedAt());
    }

    /** Gợi ý luyện tập bằng tiếng Việt dựa trên điểm và các từ sai/bỏ sót. */
    static List<String> tips(SpeakingSubmission s, Detail detail) {
        List<String> tips = new ArrayList<>();
        if (s.getStatus() != SpeakingSubmission.Status.SCORED) {
            return tips;
        }
        if (detail.estimated()) {
            tips.add("Trình duyệt chưa hỗ trợ nhận dạng giọng nói nên điểm chỉ là ước lượng. Hãy dùng Chrome hoặc Edge để được chấm từng từ.");
        }
        List<String> omitted = detail.words().stream().filter(w -> "OMISSION".equals(w.errorType())).map(WordScore::word).toList();
        List<String> wrong = detail.words().stream().filter(w -> "MISPRONUNCIATION".equals(w.errorType())).map(WordScore::word).toList();
        if (!omitted.isEmpty()) tips.add("Bạn bỏ sót: " + String.join(", ", omitted) + ". Hãy đọc đủ từng từ.");
        if (!wrong.isEmpty()) tips.add("Luyện lại phát âm các từ: " + String.join(", ", wrong) + " (bấm \"Nghe mẫu\" để nghe lại).");
        if (s.getFluencyScore() != null && s.getFluencyScore().compareTo(BigDecimal.valueOf(70)) < 0) {
            tips.add("Nói liền mạch hơn: đọc cả cụm từ, tránh ngắt quãng giữa các từ.");
        }
        if (s.getPronunciationScore() != null && s.getPronunciationScore().compareTo(BigDecimal.valueOf(85)) >= 0
                && omitted.isEmpty() && wrong.isEmpty() && !detail.estimated()) {
            tips.add("Tuyệt vời! Phát âm rõ ràng, đầy đủ. Thử nói nhanh và tự nhiên hơn nhé.");
        }
        return tips;
    }

    record Detail(boolean estimated, List<WordScore> words) {
    }

    private Detail parseDetail(String json) {
        if (json == null) {
            return new Detail(false, List.of());
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<WordScore> words = objectMapper.convertValue(map.get("words"), new TypeReference<>() {
            });
            return new Detail(Boolean.TRUE.equals(map.get("estimated")), words == null ? List.of() : words);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new Detail(false, List.of());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
