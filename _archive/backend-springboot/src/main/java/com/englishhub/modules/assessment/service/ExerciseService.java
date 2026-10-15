package com.englishhub.modules.assessment.service;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.integration.ai.writing.WritingEvaluator.WritingFeedback;
import com.englishhub.modules.assessment.dto.ExerciseDtos.CheckAnswerRequest;
import com.englishhub.modules.assessment.dto.ExerciseDtos.CheckResult;
import com.englishhub.modules.assessment.dto.ExerciseDtos.ExerciseAttemptResponse;
import com.englishhub.modules.assessment.dto.ExerciseDtos.ExerciseResult;
import com.englishhub.modules.assessment.dto.ExerciseDtos.ExerciseSummary;
import com.englishhub.modules.assessment.dto.PlacementDtos.QuestionReview;
import com.englishhub.modules.assessment.entity.Assessment;
import com.englishhub.modules.assessment.entity.AssessmentQuestion;
import com.englishhub.modules.assessment.entity.AttemptAnswer;
import com.englishhub.modules.assessment.entity.Enums.AttemptStatus;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.entity.TestAttempt;
import com.englishhub.modules.assessment.grading.AnswerCodec;
import com.englishhub.modules.assessment.grading.AnswerGrader;
import com.englishhub.modules.assessment.grading.AnswerPayload;
import com.englishhub.modules.assessment.grading.GradeResult;
import com.englishhub.modules.assessment.repository.AssessmentRepository;
import com.englishhub.modules.assessment.repository.TestAttemptRepository;
import com.englishhub.modules.assessment.service.QuestionPresenter.EssayConfig;
import com.englishhub.modules.progress.dto.LessonProgressDtos.CompleteRequest;
import com.englishhub.modules.progress.repository.ActivityWriteRepository.DailyDelta;
import com.englishhub.modules.progress.service.ActivityService;
import com.englishhub.modules.progress.service.LessonProgressService;
import com.englishhub.modules.skill.dto.SkillDtos.SpeakingResultResponse;
import com.englishhub.modules.skill.dto.SkillDtos.WritingResultResponse;
import com.englishhub.modules.skill.entity.SpeakingSubmission;
import com.englishhub.modules.skill.service.SpeakingService;
import com.englishhub.modules.skill.service.WritingService;
import com.englishhub.modules.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bài tập / quiz trong bài học (TestType LESSON_QUIZ, PRACTICE).
 *
 * <p>Khác Placement Test: mỗi câu được chấm ngay khi học viên bấm "Kiểm tra" và hiện lời giải luôn.
 * ESSAY chấm bằng {@link WritingService}, SPEAKING lấy điểm từ bản ghi âm đã chấm qua {@link SpeakingService}.
 * Hoàn thành bài tập với điểm >= ngưỡng đạt sẽ đánh dấu hoàn thành bài học gắn kèm.</p>
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private static final BigDecimal DEFAULT_PASSING = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Ngưỡng điểm thành phần (0-100) để coi câu nói/viết là "đạt". */
    private static final int SPEAKING_PASS = 70, WRITING_PASS = 60;

    private final AssessmentRepository assessmentRepository;
    private final TestAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final AnswerGrader grader;
    private final AnswerCodec codec;
    private final QuestionPresenter presenter;
    private final WritingService writingService;
    private final SpeakingService speakingService;
    private final ActivityService activityService;
    private final LessonProgressService lessonProgressService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ExerciseSummary> listForLesson(Long userId, Long lessonId) {
        List<Assessment> tests = assessmentRepository.findByLessonIdAndPublishedTrueOrderByIdAsc(lessonId).stream()
                .filter(t -> t.getTestType() != TestType.PLACEMENT)
                .toList();
        if (tests.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TestAttempt>> attemptsByTest = attemptRepository
                .findByUserAndTests(userId, tests.stream().map(Assessment::getId).toList()).stream()
                .collect(Collectors.groupingBy(a -> a.getAssessment().getId()));
        Instant now = clock.instant();
        return tests.stream().map(t -> {
            List<TestAttempt> attempts = attemptsByTest.getOrDefault(t.getId(), List.of());
            BigDecimal best = attempts.stream().map(TestAttempt::getPercentage).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            Long inProgress = attempts.stream()
                    .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS && !a.isExpired(now, Duration.ZERO))
                    .map(TestAttempt::getId).findFirst().orElse(null);
            Set<Skill> skills = t.getQuestions().stream().map(aq -> aq.getQuestion().getSkill())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Skill.class)));
            BigDecimal passing = passing(t);
            return new ExerciseSummary(t.getId(), t.getTitle(), t.getDescription(), t.getTestType(), t.getQuestions().size(),
                    t.getDurationMinutes(), passing, skills,
                    (int) attempts.stream().filter(a -> a.getStatus() != AttemptStatus.IN_PROGRESS).count(),
                    best, best != null && best.compareTo(passing) >= 0, inProgress);
        }).toList();
    }

    /** Tiếp tục bài đang làm dở (còn hạn) hoặc bắt đầu lượt mới. */
    @Transactional
    public ExerciseAttemptResponse start(Long userId, Long testId) {
        var user = userRepository.findForUpdate(userId).orElseThrow(() -> BusinessException.notFound("người dùng", userId));
        Assessment test = requireExercise(testId);
        List<AssessmentQuestion> questions = assessmentRepository.findQuestionsWithOptions(testId);
        Instant now = clock.instant();

        TestAttempt attempt = attemptRepository
                .findFirstByUserIdAndAssessmentIdAndStatusOrderByStartedAtDesc(userId, testId, AttemptStatus.IN_PROGRESS)
                .orElse(null);
        if (attempt != null && attempt.isExpired(now, Duration.ZERO)) {
            close(attempt, AttemptStatus.EXPIRED, questions, now);
            attempt = null;
        }
        if (attempt == null) {
            attempt = new TestAttempt();
            attempt.setUser(user);
            attempt.setAssessment(test);
            attempt.setStartedAt(now);
            if (test.getDurationMinutes() != null) {
                attempt.setExpiresAt(now.plus(Duration.ofMinutes(test.getDurationMinutes())));
            }
            attempt.setMaxScore(totalPoints(questions));
            attemptRepository.save(attempt);
        }

        Map<Long, AssessmentQuestion> byId = index(questions);
        Map<Long, CheckResult> checked = new LinkedHashMap<>();
        for (AttemptAnswer answer : attempt.getAnswers()) {
            AssessmentQuestion aq = byId.get(answer.getQuestion().getId());
            if (aq != null && answer.getScore() != null) {
                checked.put(aq.getQuestion().getId(), toCheckResult(userId, aq, answer));
            }
        }
        return new ExerciseAttemptResponse(presenter.attemptResponse(attempt, test, questions, now), passing(test), checked);
    }

    /** Chấm một câu. Mỗi câu chỉ chấm một lần trong một lượt làm. */
    @Transactional
    public CheckResult check(Long userId, Long attemptId, CheckAnswerRequest request) {
        TestAttempt attempt = lockInProgress(userId, attemptId);
        List<AssessmentQuestion> questions = assessmentRepository.findQuestionsWithOptions(attempt.getAssessment().getId());
        AssessmentQuestion aq = index(questions).get(request.questionId());
        if (aq == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_IN_TEST);
        }
        Question question = aq.getQuestion();
        AttemptAnswer answer = attempt.findAnswer(question.getId()).orElse(null);
        if (answer != null && answer.getScore() != null) {
            throw new BusinessException(ErrorCode.ANSWER_ALREADY_CHECKED);
        }
        if (answer == null) {
            answer = new AttemptAnswer();
            answer.setQuestion(question);
            attempt.addAnswer(answer);
        }

        AnswerPayload payload = request.toPayload();
        BigDecimal points = aq.effectivePoints();
        Long lessonId = attempt.getAssessment().getLessonId();
        switch (question.getQuestionType()) {
            case ESSAY -> {
                if (payload.answerText() == null || payload.answerText().isBlank()) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bạn chưa viết bài");
                }
                EssayConfig cfg = presenter.essayConfig(question);
                WritingResultResponse result = writingService.evaluate(userId, payload.answerText(), question.getContent(),
                        cfg.minWords(), cfg.maxWords(), lessonId, question.getId());
                setResult(answer, result.feedback().score() >= WRITING_PASS, scaled(points, result.feedback().score()));
                answer.setFeedback(toJson(Map.of("writingSubmissionId", result.submissionId(), "writing", result.feedback())));
            }
            case SPEAKING -> {
                if (request.speakingSubmissionId() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hãy thu âm trước khi kiểm tra");
                }
                SpeakingSubmission sub = speakingService.requireScored(userId, request.speakingSubmissionId());
                if (!question.getId().equals(sub.getQuestionId())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bản ghi âm không thuộc câu này");
                }
                double pron = sub.getPronunciationScore().doubleValue();
                payload = new AnswerPayload(null, null, null, null, sub.getRecognizedText());
                setResult(answer, pron >= SPEAKING_PASS, scaled(points, pron));
                answer.setFeedback(toJson(Map.of("speakingSubmissionId", sub.getId())));
            }
            default -> {
                if (!AnswerGrader.referencesOwnOptions(question, payload)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phương án trả lời không thuộc câu hỏi");
                }
                GradeResult grade = grader.grade(question, payload, points);
                setResult(answer, grade.correct(), grade.score());
            }
        }
        codec.write(answer, question.getQuestionType(), payload);
        answer.setAnsweredAt(clock.instant());
        return toCheckResult(userId, aq, answer);
    }

    /** Nộp bài: tính điểm, ghi nhận hoạt động; đạt ngưỡng thì hoàn thành bài học gắn kèm. Idempotent. */
    @Transactional
    public ExerciseResult finish(Long userId, Long attemptId) {
        TestAttempt attempt = attemptRepository.findForUpdate(attemptId, userId)
                .orElseThrow(() -> BusinessException.notFound("bài làm", attemptId));
        Assessment test = requireExercise(attempt.getAssessment().getId());
        List<AssessmentQuestion> questions = assessmentRepository.findQuestionsWithOptions(test.getId());
        boolean lessonCompleted = false;

        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            close(attempt, AttemptStatus.SUBMITTED, questions, clock.instant());
            activityService.record(userId, new DailyDelta(0, 0, 0, 0, 1, attempt.getPercentage()));
            if (attempt.getPercentage().compareTo(passing(test)) >= 0 && test.getLessonId() != null) {
                lessonProgressService.complete(userId, test.getLessonId(), new CompleteRequest(attempt.getPercentage()));
                lessonCompleted = true;
            }
        }
        return toResult(attempt, test, questions, lessonCompleted);
    }

    @Transactional(readOnly = true)
    public ExerciseResult result(Long userId, Long attemptId) {
        TestAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> BusinessException.notFound("bài làm", attemptId));
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
        }
        Assessment test = attempt.getAssessment();
        return toResult(attempt, test, assessmentRepository.findQuestionsWithOptions(test.getId()), false);
    }

    // ------------------------------------------------------------------ helpers

    /** Chốt lượt làm: câu chưa trả lời = 0 điểm. */
    private void close(TestAttempt attempt, AttemptStatus status, List<AssessmentQuestion> questions, Instant now) {
        BigDecimal max = totalPoints(questions);
        BigDecimal score = attempt.getAnswers().stream().map(AttemptAnswer::getScore).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        attempt.setStatus(status);
        attempt.setSubmittedAt(now);
        attempt.setScore(score);
        attempt.setMaxScore(max);
        attempt.setPercentage(max.signum() == 0 ? BigDecimal.ZERO
                : score.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP).min(HUNDRED));
    }

    private ExerciseResult toResult(TestAttempt attempt, Assessment test, List<AssessmentQuestion> questions,
                                    boolean lessonCompleted) {
        Map<Long, AttemptAnswer> answers = attempt.getAnswers().stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
        List<QuestionReview> review = new ArrayList<>();
        int number = 0;
        for (AssessmentQuestion aq : questions) {
            review.add(presenter.review(aq, ++number, answers.get(aq.getQuestion().getId())));
        }
        int correct = (int) answers.values().stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count();
        int answered = (int) answers.values().stream().filter(a -> a.getScore() != null).count();
        BigDecimal passing = passing(test);
        return new ExerciseResult(attempt.getId(), test.getId(), test.getTitle(), attempt.getScore(), attempt.getMaxScore(),
                attempt.getPercentage(), passing, attempt.getPercentage().compareTo(passing) >= 0, correct, answered,
                questions.size(), lessonCompleted, review);
    }

    private CheckResult toCheckResult(Long userId, AssessmentQuestion aq, AttemptAnswer answer) {
        WritingFeedback writing = null;
        SpeakingResultResponse speaking = null;
        JsonNode feedback = readJson(answer.getFeedback());
        if (feedback != null && feedback.has("writing")) {
            writing = objectMapper.convertValue(feedback.get("writing"), WritingFeedback.class);
        }
        if (feedback != null && feedback.has("speakingSubmissionId")) {
            speaking = speakingService.get(userId, feedback.get("speakingSubmissionId").asLong());
        }
        return new CheckResult(aq.getQuestion().getId(), answer.getCorrect(), answer.getScore(), aq.effectivePoints(),
                presenter.review(aq, 0, answer), writing, speaking);
    }

    private TestAttempt lockInProgress(Long userId, Long attemptId) {
        TestAttempt attempt = attemptRepository.findForUpdate(attemptId, userId)
                .orElseThrow(() -> BusinessException.notFound("bài làm", attemptId));
        if (attempt.getAssessment().getTestType() == TestType.PLACEMENT) {
            throw BusinessException.notFound("bài làm", attemptId);
        }
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_IN_PROGRESS);
        }
        if (attempt.isExpired(clock.instant(), Duration.ofSeconds(30))) {
            throw new BusinessException(ErrorCode.ATTEMPT_EXPIRED);
        }
        return attempt;
    }

    private Assessment requireExercise(Long testId) {
        return assessmentRepository.findById(testId)
                .filter(t -> t.isPublished() && t.getTestType() != TestType.PLACEMENT)
                .orElseThrow(() -> BusinessException.notFound("bài tập", testId));
    }

    private static void setResult(AttemptAnswer answer, Boolean correct, BigDecimal score) {
        answer.setCorrect(correct);
        answer.setScore(score);
    }

    /** Điểm câu = điểm tối đa × (điểm thành phần / 100). */
    private static BigDecimal scaled(BigDecimal points, double percent) {
        return points.multiply(BigDecimal.valueOf(Math.max(0, Math.min(100, percent))))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal passing(Assessment test) {
        return test.getPassingPercent() != null ? test.getPassingPercent() : DEFAULT_PASSING;
    }

    private static BigDecimal totalPoints(List<AssessmentQuestion> questions) {
        return questions.stream().map(AssessmentQuestion::effectivePoints).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<Long, AssessmentQuestion> index(List<AssessmentQuestion> questions) {
        return questions.stream().collect(Collectors.toMap(aq -> aq.getQuestion().getId(), Function.identity()));
    }

    private JsonNode readJson(String json) {
        try {
            return json == null ? null : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
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
