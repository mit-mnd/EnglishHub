package com.englishhub.modules.assessment.service;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminTestDetail;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminTestRow;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.TestQuestionInput;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.TestQuestionView;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.TestRequest;
import com.englishhub.modules.assessment.entity.Assessment;
import com.englishhub.modules.assessment.entity.AssessmentQuestion;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.repository.AssessmentQuestionRepository;
import com.englishhub.modules.assessment.repository.AssessmentRepository;
import com.englishhub.modules.assessment.repository.QuestionRepository;
import com.englishhub.modules.catalog.entity.Lesson;
import com.englishhub.modules.catalog.repository.LessonRepository;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.cms.ContentUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quản trị bài test: thông tin chung, danh sách câu hỏi (thứ tự + điểm riêng), xuất bản, nhân bản, xóa.
 * <p>
 * Quy tắc giữ toàn vẹn lịch sử: khi bài test đã có lượt làm thì không được đổi danh sách câu hỏi
 * (kết quả cũ, điểm theo kỹ năng, phần xem lại đều dựa trên danh sách này) - admin nhân bản rồi sửa bản mới.
 * Bài xếp lớp đang dùng = bài PLACEMENT đã xuất bản mới nhất.
 */
@Service
@RequiredArgsConstructor
public class AdminTestService {

    private static final Set<String> SORTS = Set.of("title", "createdAt", "updatedAt");

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository assessmentQuestionRepository;
    private final QuestionRepository questionRepository;
    private final LessonRepository lessonRepository;
    private final LevelRepository levelRepository;
    private final ContentUsageRepository usage;

    @Transactional(readOnly = true)
    public PageResponse<AdminTestRow> search(TestType type, Long lessonId, Boolean published, String keyword, Pageable pageable) {
        Page<Assessment> page = assessmentRepository.adminSearch(type, lessonId, published, PageableUtils.likePattern(keyword),
                PageableUtils.requireSortable(pageable, SORTS));
        List<Long> ids = page.getContent().stream().map(Assessment::getId).toList();
        Map<Long, Long> questionCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : assessmentRepository.countQuestions(ids)) {
                questionCounts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        Map<Long, Long> attempts = usage.attemptCounts(ids);
        Map<Long, String> lessonTitles = lessonTitles(page.getContent().stream().map(Assessment::getLessonId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Long activePlacement = activePlacementId();
        return PageResponse.of(page, t -> new AdminTestRow(t.getId(), t.getTitle(), t.getTestType(), t.getLessonId(),
                lessonTitles.get(t.getLessonId()), t.getLevelId(), t.getDurationMinutes(), t.isPublished(),
                t.getId().equals(activePlacement), questionCounts.getOrDefault(t.getId(), 0L),
                attempts.getOrDefault(t.getId(), 0L), t.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminTestDetail get(Long id) {
        Assessment t = find(id);
        List<AssessmentQuestion> items = assessmentRepository.findQuestionsWithOptions(id);
        BigDecimal total = items.stream().map(AssessmentQuestion::effectivePoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        String lessonTitle = t.getLessonId() == null ? null : lessonTitles(Set.of(t.getLessonId())).get(t.getLessonId());
        return new AdminTestDetail(t.getId(), t.getTitle(), t.getDescription(), t.getTestType(), t.getLessonId(), lessonTitle,
                t.getLevelId(), t.getDurationMinutes(), t.getPassingPercent(), t.isShuffleQuestions(), t.getMaxAttempts(),
                t.isPublished(), t.getId().equals(activePlacementId()), usage.testAttempts(id), total,
                items.stream().map(aq -> new TestQuestionView(aq.getQuestion().getId(), aq.getSortOrder(), aq.getPoints(),
                        aq.effectivePoints(), aq.getQuestion().getQuestionType(), aq.getQuestion().getSkill(),
                        AdminQuestionService.preview(aq.getQuestion().getContent()), aq.getQuestion().isActive())).toList(),
                t.getUpdatedAt());
    }

    @Transactional
    public AdminTestDetail create(TestRequest request, Long adminId) {
        Assessment t = new Assessment();
        t.setCreatedBy(adminId);
        apply(t, request, 0);
        assessmentRepository.save(t);
        return get(t.getId());
    }

    @Transactional
    public AdminTestDetail update(Long id, TestRequest request) {
        Assessment t = find(id);
        if (t.getTestType() != request.testType() && usage.testAttempts(id) > 0) {
            throw new BusinessException(ErrorCode.TEST_HAS_ATTEMPTS, "Bài test đã có lượt làm nên không thể đổi loại bài test");
        }
        apply(t, request, assessmentRepository.findQuestionsWithOptions(id).size());
        assessmentRepository.flush();
        return get(id);
    }

    @Transactional
    public AdminTestDetail setPublished(Long id, boolean published) {
        Assessment t = find(id);
        if (published) {
            requirePublishable(t, assessmentRepository.findQuestionsWithOptions(id).size());
        }
        t.setPublished(published);
        assessmentRepository.flush();
        return get(id);
    }

    /** Thay toàn bộ danh sách câu hỏi theo thứ tự gửi lên; points = null dùng điểm mặc định của câu hỏi. */
    @Transactional
    public AdminTestDetail replaceQuestions(Long id, List<TestQuestionInput> items) {
        Assessment t = find(id);
        if (usage.testAttempts(id) > 0) {
            throw new BusinessException(ErrorCode.TEST_HAS_ATTEMPTS);
        }
        Set<Long> seen = new HashSet<>();
        for (TestQuestionInput in : items) {
            if (!seen.add(in.questionId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Câu hỏi #" + in.questionId() + " bị lặp trong bài test");
            }
        }
        Map<Long, Question> questions = questionRepository.findAllById(seen).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        for (TestQuestionInput in : items) {
            Question q = questions.get(in.questionId());
            if (q == null) {
                throw BusinessException.notFound("câu hỏi", in.questionId());
            }
            if (!q.isActive()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Câu hỏi #" + q.getId() + " đang tắt, không thể đưa vào bài test");
            }
            if (t.getTestType() == TestType.PLACEMENT && !q.getQuestionType().isAutoGradable()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Bài xếp lớp chỉ dùng câu hỏi chấm tự động (không dùng câu Nói / Viết tự luận) - câu #" + q.getId());
            }
        }
        if (t.isPublished() && items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bài test đang xuất bản phải có ít nhất 1 câu hỏi");
        }

        assessmentQuestionRepository.deleteByTestId(id);
        Assessment managed = find(id);   // context đã được clear sau lệnh xóa hàng loạt
        for (int i = 0; i < items.size(); i++) {
            AssessmentQuestion aq = new AssessmentQuestion();
            aq.setId(new AssessmentQuestion.Key(id, items.get(i).questionId()));
            aq.setAssessment(managed);
            aq.setQuestion(questionRepository.getReferenceById(items.get(i).questionId()));
            aq.setSortOrder(i + 1);
            aq.setPoints(items.get(i).points());
            assessmentQuestionRepository.save(aq);
        }
        assessmentQuestionRepository.flush();
        return get(id);
    }

    /** Bản sao chưa xuất bản (cùng câu hỏi, điểm) - cách sửa bài test đã có người làm. */
    @Transactional
    public AdminTestDetail duplicate(Long id, Long adminId) {
        Assessment src = find(id);
        List<AssessmentQuestion> items = assessmentRepository.findQuestionsWithOptions(id);
        Assessment copy = new Assessment();
        copy.setTitle(truncate(src.getTitle() + " (bản sao)", 200));
        copy.setDescription(src.getDescription());
        copy.setTestType(src.getTestType());
        copy.setLessonId(src.getLessonId());
        copy.setLevelId(src.getLevelId());
        copy.setDurationMinutes(src.getDurationMinutes());
        copy.setPassingPercent(src.getPassingPercent());
        copy.setShuffleQuestions(src.isShuffleQuestions());
        copy.setMaxAttempts(src.getMaxAttempts());
        copy.setPublished(false);
        copy.setCreatedBy(adminId);
        assessmentRepository.save(copy);
        for (AssessmentQuestion aq : items) {
            AssessmentQuestion n = new AssessmentQuestion();
            n.setId(new AssessmentQuestion.Key(copy.getId(), aq.getQuestion().getId()));
            n.setAssessment(copy);
            n.setQuestion(aq.getQuestion());
            n.setSortOrder(aq.getSortOrder());
            n.setPoints(aq.getPoints());
            assessmentQuestionRepository.save(n);
        }
        assessmentQuestionRepository.flush();
        return get(copy.getId());
    }

    @Transactional
    public void delete(Long id) {
        Assessment t = find(id);
        if (usage.testAttempts(id) > 0) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "Bài test đã có lượt làm, hãy bỏ xuất bản thay vì xóa");
        }
        assessmentRepository.delete(t);   // TestQuestions xóa theo ON DELETE CASCADE
    }

    private void apply(Assessment t, TestRequest r, int questionCount) {
        if (r.testType() == TestType.LESSON_QUIZ) {
            if (r.lessonId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quiz bài học cần chọn bài học");
            }
            if (!lessonRepository.existsById(r.lessonId())) {
                throw BusinessException.notFound("bài học", r.lessonId());
            }
        } else if (r.lessonId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ quiz bài học mới gắn với bài học");
        }
        if (r.testType() == TestType.PLACEMENT && r.durationMinutes() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bài xếp lớp cần giới hạn thời gian");
        }
        if (r.levelId() != null && !levelRepository.existsById(r.levelId())) {
            throw BusinessException.notFound("trình độ", r.levelId());
        }
        t.setTitle(r.title().trim());
        t.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        t.setTestType(r.testType());
        t.setLessonId(r.lessonId());
        t.setLevelId(r.levelId());
        t.setDurationMinutes(r.durationMinutes());
        t.setPassingPercent(r.passingPercent());
        t.setShuffleQuestions(r.shuffleQuestions());
        t.setMaxAttempts(r.maxAttempts());
        if (r.published()) {
            requirePublishable(t, questionCount);
        }
        t.setPublished(r.published());
    }

    private static void requirePublishable(Assessment t, int questionCount) {
        if (questionCount == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thêm ít nhất 1 câu hỏi trước khi xuất bản bài test");
        }
    }

    private Long activePlacementId() {
        return assessmentRepository.findFirstByTestTypeAndPublishedTrueOrderByIdDesc(TestType.PLACEMENT)
                .map(Assessment::getId).orElse(null);
    }

    private Map<Long, String> lessonTitles(Set<Long> lessonIds) {
        // HashMap (không dùng Map.of) vì get(null) với bài test không gắn bài học phải trả null, không ném NPE
        Map<Long, String> titles = new HashMap<>();
        lessonRepository.findAllById(lessonIds).forEach(l -> titles.put(l.getId(), l.getTitle()));
        return titles;
    }

    private Assessment find(Long id) {
        return assessmentRepository.findById(id).orElseThrow(() -> BusinessException.notFound("bài test", id));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
