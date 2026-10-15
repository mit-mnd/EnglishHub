package com.englishhub.modules.assessment.service;

import com.englishhub.common.config.AppProperties;
import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.modules.assessment.dto.PlacementDtos.AnswerRequest;
import com.englishhub.modules.assessment.dto.PlacementDtos.AttemptResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.AttemptSummaryResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.PlacementResultResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.PlacementTestInfoResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.QuestionReview;
import com.englishhub.modules.assessment.dto.PlacementDtos.SkillScore;
import com.englishhub.modules.assessment.dto.PlacementDtos.SubmitAttemptRequest;
import com.englishhub.modules.assessment.entity.Assessment;
import com.englishhub.modules.assessment.entity.AssessmentQuestion;
import com.englishhub.modules.assessment.entity.AttemptAnswer;
import com.englishhub.modules.assessment.entity.Enums.AttemptStatus;
import com.englishhub.common.domain.Skill;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.entity.TestAttempt;
import com.englishhub.modules.assessment.grading.AnswerCodec;
import com.englishhub.modules.assessment.grading.AnswerGrader;
import com.englishhub.modules.assessment.grading.AnswerPayload;
import com.englishhub.modules.assessment.grading.GradeResult;
import com.englishhub.modules.assessment.repository.AssessmentRepository;
import com.englishhub.modules.assessment.repository.TestAttemptRepository;
import com.englishhub.modules.catalog.dto.LevelResponse;
import com.englishhub.modules.catalog.entity.Level;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.englishhub.modules.assessment.event.PlacementCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Luồng Placement Test: bắt đầu/tiếp tục bài làm -> lưu từng câu (autosave) -> nộp bài -> chấm điểm
 * -> xếp level và cập nhật Users.CurrentLevelId.
 *
 * <p>Thời gian làm bài được kiểm soát ở server (ExpiresAt + grace period); client chỉ hiển thị đồng hồ.
 * Bài quá hạn được tự chấm với các câu đã lưu và đánh dấu EXPIRED.</p>
 */
@Service
@RequiredArgsConstructor
public class PlacementService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AssessmentRepository assessmentRepository;
    private final TestAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final LevelRepository levelRepository;
    private final AnswerGrader grader;
    private final AnswerCodec codec;
    private final QuestionPresenter presenter;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PlacementTestInfoResponse getTestInfo(Long userId) {
        Assessment test = activeTest();
        Instant now = clock.instant();
        Long inProgressId = attemptRepository
                .findFirstByUserIdAndAssessmentIdAndStatusOrderByStartedAtDesc(userId, test.getId(), AttemptStatus.IN_PROGRESS)
                .filter(a -> !a.isExpired(now, grace()))
                .map(TestAttempt::getId)
                .orElse(null);
        AttemptSummaryResponse latest = attemptRepository
                .findFirstByUserIdAndAssessmentTestTypeAndStatusNotOrderBySubmittedAtDesc(
                        userId, TestType.PLACEMENT, AttemptStatus.IN_PROGRESS)
                .map(PlacementService::toSummary)
                .orElse(null);
        return new PlacementTestInfoResponse(test.getId(), test.getTitle(), test.getDescription(),
                test.getDurationMinutes(), test.getQuestions().size(), inProgressId, latest);
    }

    /** Tiếp tục bài đang làm nếu còn hạn; nếu không thì tạo bài làm mới. */
    @Transactional
    public AttemptResponse startOrResume(Long userId) {
        // Khóa dòng user: 2 request "bắt đầu" đồng thời không tạo ra 2 bài làm
        User user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("người dùng", userId));
        Assessment test = activeTest();
        List<AssessmentQuestion> questions = loadQuestions(test.getId());
        Instant now = clock.instant();

        Optional<TestAttempt> current = attemptRepository.findFirstByUserIdAndAssessmentIdAndStatusOrderByStartedAtDesc(
                userId, test.getId(), AttemptStatus.IN_PROGRESS);
        if (current.isPresent()) {
            TestAttempt attempt = current.get();
            if (!attempt.isExpired(now, grace())) {
                return toAttemptResponse(attempt, test, questions, now);
            }
            finalizeAttempt(attempt, questions, AttemptStatus.EXPIRED, now);
        }

        TestAttempt attempt = new TestAttempt();
        attempt.setUser(user);
        attempt.setAssessment(test);
        attempt.setStartedAt(now);
        if (test.getDurationMinutes() != null) {
            attempt.setExpiresAt(now.plus(Duration.ofMinutes(test.getDurationMinutes())));
        }
        attempt.setMaxScore(questions.stream().map(AssessmentQuestion::effectivePoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        attemptRepository.save(attempt);
        return toAttemptResponse(attempt, test, questions, now);
    }

    /** Autosave một câu trả lời (gọi mỗi khi học viên chọn/nhập đáp án). */
    @Transactional
    public void saveAnswer(Long userId, Long attemptId, AnswerRequest request) {
        TestAttempt attempt = lockAttempt(userId, attemptId);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_IN_PROGRESS);
        }
        Instant now = clock.instant();
        if (attempt.isExpired(now, grace())) {
            throw new BusinessException(ErrorCode.ATTEMPT_EXPIRED);
        }
        Map<Long, AssessmentQuestion> byQuestionId = indexByQuestionId(loadQuestions(attempt.getAssessment().getId()));
        upsertAnswer(attempt, byQuestionId, request, now);
    }

    /**
     * Nộp bài. Idempotent: nộp lại bài đã chấm sẽ trả về kết quả cũ.
     * Nếu đã quá hạn, các câu trả lời gửi kèm bị bỏ qua và chỉ chấm các câu đã autosave.
     */
    @Transactional
    public PlacementResultResponse submit(Long userId, Long attemptId, SubmitAttemptRequest request) {
        TestAttempt attempt = lockAttempt(userId, attemptId);
        List<AssessmentQuestion> questions = loadQuestions(attempt.getAssessment().getId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return toResult(attempt, questions);
        }
        Instant now = clock.instant();
        boolean expired = attempt.isExpired(now, grace());
        if (!expired && request != null && request.answers() != null) {
            Map<Long, AssessmentQuestion> byQuestionId = indexByQuestionId(questions);
            request.answers().forEach(answer -> upsertAnswer(attempt, byQuestionId, answer, now));
        }
        finalizeAttempt(attempt, questions, expired ? AttemptStatus.EXPIRED : AttemptStatus.SUBMITTED, now);
        return toResult(attempt, questions);
    }

    @Transactional(readOnly = true)
    public PlacementResultResponse getResult(Long userId, Long attemptId) {
        TestAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> BusinessException.notFound("bài làm", attemptId));
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
        }
        return toResult(attempt, loadQuestions(attempt.getAssessment().getId()));
    }

    /** Ngưỡng % dưới mức này thì coi là kỹ năng yếu (được ưu tiên trong lộ trình). */
    private static final BigDecimal WEAK_SKILL_THRESHOLD = BigDecimal.valueOf(60);

    /** Kết quả xếp lớp gần nhất (dùng cho module lộ trình). */
    public record PlacementSnapshot(Long attemptId, Integer levelId, Set<Skill> weakSkills) {
    }

    @Transactional(readOnly = true)
    public Optional<PlacementSnapshot> latestSnapshot(Long userId) {
        return attemptRepository.findFirstByUserIdAndAssessmentTestTypeAndStatusNotOrderBySubmittedAtDesc(
                        userId, TestType.PLACEMENT, AttemptStatus.IN_PROGRESS)
                .map(a -> new PlacementSnapshot(
                        a.getId(),
                        a.getResultLevel() == null ? null : a.getResultLevel().getId(),
                        parseSkillScores(a.getSkillScoresJson()).stream()
                                .filter(s -> s.percentage().compareTo(WEAK_SKILL_THRESHOLD) < 0)
                                .map(SkillScore::skill)
                                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Skill.class)))));
    }

    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> history(Long userId, Pageable pageable) {
        Pageable safePageable = PageableUtils.requireSortable(pageable, Set.of("startedAt", "submittedAt", "percentage"));
        return PageResponse.of(attemptRepository.findByUserIdAndAssessmentTestTypeAndStatusNot(
                userId, TestType.PLACEMENT, AttemptStatus.IN_PROGRESS, safePageable), PlacementService::toSummary);
    }

    // ------------------------------------------------------------------ chấm điểm

    /**
     * Chấm toàn bộ bài, tính điểm theo kỹ năng, xếp level và cập nhật hồ sơ học viên.
     * Câu bỏ trống được tính 0 điểm. Điểm tối đa luôn tính trên toàn bộ câu hỏi của bài.
     */
    private void finalizeAttempt(TestAttempt attempt, List<AssessmentQuestion> questions, AttemptStatus status,
                                 Instant now) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        Map<Skill, BigDecimal[]> bySkill = new EnumMap<>(Skill.class);   // [điểm đạt, điểm tối đa]

        for (AssessmentQuestion aq : questions) {
            Question question = aq.getQuestion();
            BigDecimal points = aq.effectivePoints();
            Optional<AttemptAnswer> answer = attempt.findAnswer(question.getId());
            GradeResult grade = answer
                    .map(a -> grader.grade(question, codec.read(a, question.getQuestionType()), points))
                    .orElseGet(() -> grader.grade(question, null, points));
            answer.ifPresent(a -> {
                a.setCorrect(grade.correct());
                a.setScore(grade.score());
            });

            BigDecimal earned = grade.isGraded() ? grade.score() : BigDecimal.ZERO;
            total = total.add(earned);
            max = max.add(points);
            BigDecimal[] skill = bySkill.computeIfAbsent(question.getSkill(),
                    s -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            skill[0] = skill[0].add(earned);
            skill[1] = skill[1].add(points);
        }

        BigDecimal percentage = percent(total, max);
        Level level = PlacementLevelResolver.resolve(percentage, levelRepository.findAll());

        attempt.setStatus(status);
        attempt.setSubmittedAt(status == AttemptStatus.EXPIRED && attempt.getExpiresAt() != null
                ? attempt.getExpiresAt() : now);
        attempt.setScore(total);
        attempt.setMaxScore(max);
        attempt.setPercentage(percentage);
        attempt.setResultLevel(level);
        attempt.setSkillScoresJson(toJson(bySkill.entrySet().stream()
                .map(e -> new SkillScore(e.getKey(), e.getValue()[0], e.getValue()[1], percent(e.getValue()[0], e.getValue()[1])))
                .toList()));

        User user = attempt.getUser();
        user.setCurrentLevel(level);
        user.setPlacementCompletedAt(now);

        // Module lộ trình lắng nghe để tự tạo lộ trình cho học viên chưa có (cùng transaction)
        eventPublisher.publishEvent(new PlacementCompletedEvent(user.getId(), attempt.getId()));
    }

    private void upsertAnswer(TestAttempt attempt, Map<Long, AssessmentQuestion> byQuestionId, AnswerRequest request,
                              Instant now) {
        AssessmentQuestion aq = byQuestionId.get(request.questionId());
        if (aq == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_IN_TEST);
        }
        Question question = aq.getQuestion();
        AnswerPayload payload = request.toPayload();
        validateOptionIds(question, payload);

        AttemptAnswer answer = attempt.findAnswer(question.getId()).orElseGet(() -> {
            AttemptAnswer created = new AttemptAnswer();
            created.setQuestion(question);
            attempt.addAnswer(created);
            return created;
        });
        codec.write(answer, question.getQuestionType(), payload);
        answer.setAnsweredAt(now);
        answer.setCorrect(null);
        answer.setScore(null);
    }

    /** Chặn id phương án không thuộc câu hỏi (client sửa request). */
    private static void validateOptionIds(Question question, AnswerPayload payload) {
        if (!AnswerGrader.referencesOwnOptions(question, payload)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Phương án trả lời không thuộc câu hỏi " + question.getId());
        }
    }

    // ------------------------------------------------------------------ mapping

    private AttemptResponse toAttemptResponse(TestAttempt attempt, Assessment test, List<AssessmentQuestion> questions,
                                              Instant now) {
        return presenter.attemptResponse(attempt, test, questions, now);
    }

    private PlacementResultResponse toResult(TestAttempt attempt, List<AssessmentQuestion> questions) {
        Map<Long, AttemptAnswer> answers = attempt.getAnswers().stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
        List<QuestionReview> review = new ArrayList<>();
        int number = 0;
        int correctCount = 0;
        for (AssessmentQuestion aq : questions) {
            AttemptAnswer answer = answers.get(aq.getQuestion().getId());
            if (answer != null && Boolean.TRUE.equals(answer.getCorrect())) {
                correctCount++;
            }
            review.add(presenter.review(aq, ++number, answer));
        }
        long duration = attempt.getSubmittedAt() == null ? 0
                : Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).toSeconds();
        return new PlacementResultResponse(attempt.getId(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getSubmittedAt(), duration, attempt.getScore(), attempt.getMaxScore(), attempt.getPercentage(),
                correctCount, questions.size(), LevelResponse.from(attempt.getResultLevel()),
                parseSkillScores(attempt.getSkillScoresJson()), review);
    }

    private static AttemptSummaryResponse toSummary(TestAttempt attempt) {
        return new AttemptSummaryResponse(attempt.getId(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getSubmittedAt(), attempt.getScore(), attempt.getMaxScore(), attempt.getPercentage(),
                LevelResponse.from(attempt.getResultLevel()));
    }

    // ------------------------------------------------------------------ helpers

    private Assessment activeTest() {
        return assessmentRepository.findFirstByTestTypeAndPublishedTrueOrderByIdDesc(TestType.PLACEMENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACEMENT_TEST_NOT_AVAILABLE));
    }

    private List<AssessmentQuestion> loadQuestions(Long testId) {
        List<AssessmentQuestion> questions = assessmentRepository.findQuestionsWithOptions(testId);
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.PLACEMENT_TEST_NOT_AVAILABLE);
        }
        return questions;
    }

    private TestAttempt lockAttempt(Long userId, Long attemptId) {
        return attemptRepository.findForUpdate(attemptId, userId)
                .orElseThrow(() -> BusinessException.notFound("bài làm", attemptId));
    }

    private static Map<Long, AssessmentQuestion> indexByQuestionId(Collection<AssessmentQuestion> questions) {
        return questions.stream().collect(Collectors.toMap(aq -> aq.getQuestion().getId(), Function.identity()));
    }

    private Duration grace() {
        return appProperties.placement().gracePeriod();
    }

    private static BigDecimal percent(BigDecimal value, BigDecimal max) {
        if (max.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<SkillScore> parseSkillScores(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, SkillScore[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SkillScoresJson không hợp lệ", e);
        }
    }
}
