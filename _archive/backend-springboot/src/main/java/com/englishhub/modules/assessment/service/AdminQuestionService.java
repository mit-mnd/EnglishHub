package com.englishhub.modules.assessment.service;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.dto.PageResponse;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.common.util.PageableUtils;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminQuestionDetail;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminQuestionRow;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.OptionInput;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.OptionView;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.QuestionRequest;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.QuestionUsage;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Question;
import com.englishhub.modules.assessment.entity.QuestionOption;
import com.englishhub.modules.assessment.repository.QuestionRepository;
import com.englishhub.modules.assessment.service.QuestionDefinitionValidator.InvalidQuestionException;
import com.englishhub.modules.assessment.service.QuestionDefinitionValidator.OptionDef;
import com.englishhub.modules.catalog.repository.LevelRepository;
import com.englishhub.modules.cms.ContentUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Quản trị ngân hàng câu hỏi (dùng chung cho bài xếp lớp, quiz bài học, luyện tập). */
@Service
@RequiredArgsConstructor
public class AdminQuestionService {

    private static final Set<String> SORTS = Set.of("id", "difficulty", "createdAt", "updatedAt");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final int PREVIEW_LENGTH = 140;

    private final QuestionRepository questionRepository;
    private final LevelRepository levelRepository;
    private final QuestionDefinitionValidator validator;
    private final ContentUsageRepository usage;

    @Transactional(readOnly = true)
    public PageResponse<AdminQuestionRow> search(QuestionType type, Skill skill, Integer levelId, Boolean active,
                                                 String keyword, Long excludeTestId, Pageable pageable) {
        Page<Question> page = questionRepository.adminSearch(type, skill, levelId, active,
                PageableUtils.likePattern(keyword), excludeTestId, PageableUtils.requireSortable(pageable, SORTS));
        Map<Long, Long> testCounts = new HashMap<>();
        if (page.hasContent()) {
            for (Object[] row : questionRepository.countTests(page.getContent().stream().map(Question::getId).toList())) {
                testCounts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return PageResponse.of(page, q -> new AdminQuestionRow(q.getId(), q.getQuestionType(), q.getSkill(),
                q.getLevel() == null ? null : q.getLevel().getCode(), preview(q.getContent()), q.getDifficulty(),
                q.getDefaultPoints(), q.isActive(), testCounts.getOrDefault(q.getId(), 0L), q.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminQuestionDetail get(Long id) {
        Question q = find(id);
        return new AdminQuestionDetail(q.getId(), q.getQuestionType(), q.getSkill(),
                q.getLevel() == null ? null : q.getLevel().getId(), q.getContent(), q.getPassage(), q.getMediaUrl(),
                q.getAnswerKey(), q.getExplanation(), q.getDifficulty(), q.getDefaultPoints(), q.isActive(),
                q.getOptions().stream()
                        .map(o -> new OptionView(o.getId(), o.getContent(), o.isCorrect(), o.getMatchText(), o.getCorrectOrder()))
                        .toList(),
                new QuestionUsage(usage.questionAnswers(id), usage.testsContainingQuestion(id)),
                q.getUpdatedAt());
    }

    @Transactional
    public AdminQuestionDetail create(QuestionRequest request, Long adminId) {
        Question q = new Question();
        q.setCreatedBy(adminId);
        apply(q, request);
        questionRepository.save(q);
        return get(q.getId());
    }

    /**
     * Sửa câu hỏi. Không cho đổi dạng câu hỏi khi đã có học viên trả lời (bài làm cũ lưu theo định dạng của dạng cũ).
     * Đổi đáp án vẫn được phép: điểm các bài đã nộp giữ nguyên, chỉ áp dụng cho lượt làm sau.
     */
    @Transactional
    public AdminQuestionDetail update(Long id, QuestionRequest request) {
        Question q = find(id);
        if (q.getQuestionType() != request.questionType() && usage.questionAnswers(id) > 0) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE,
                    "Câu hỏi đã có học viên trả lời nên không thể đổi dạng câu hỏi, hãy tạo câu hỏi mới");
        }
        apply(q, request);
        questionRepository.flush();
        return get(id);
    }

    @Transactional
    public AdminQuestionDetail setActive(Long id, boolean active) {
        find(id).setActive(active);
        questionRepository.flush();
        return get(id);
    }

    @Transactional
    public AdminQuestionDetail duplicate(Long id, Long adminId) {
        Question src = find(id);
        Question copy = new Question();
        copy.setQuestionType(src.getQuestionType());
        copy.setSkill(src.getSkill());
        copy.setLevel(src.getLevel());
        copy.setTopicId(src.getTopicId());
        copy.setContent(src.getContent());
        copy.setPassage(src.getPassage());
        copy.setMediaUrl(src.getMediaUrl());
        copy.setAnswerKey(src.getAnswerKey());
        copy.setExplanation(src.getExplanation());
        copy.setDifficulty(src.getDifficulty());
        copy.setDefaultPoints(src.getDefaultPoints());
        copy.setActive(src.isActive());
        copy.setCreatedBy(adminId);
        for (QuestionOption o : src.getOptions()) {
            copy.getOptions().add(option(copy, o.getContent(), o.isCorrect(), o.getMatchText(), o.getCorrectOrder(), o.getSortOrder()));
        }
        questionRepository.save(copy);
        return get(copy.getId());
    }

    /** Chỉ xóa câu chưa có ai trả lời và không nằm trong bài test nào; ngược lại nên tắt (active = false). */
    @Transactional
    public void delete(Long id) {
        Question q = find(id);
        List<String> tests = usage.testsContainingQuestion(id);
        if (!tests.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE,
                    "Câu hỏi đang nằm trong bài test: " + String.join(", ", tests) + ". Hãy gỡ khỏi bài test hoặc tắt câu hỏi");
        }
        if (usage.questionAnswers(id) > 0) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "Câu hỏi đã có học viên trả lời, hãy tắt câu hỏi thay vì xóa");
        }
        questionRepository.delete(q);
    }

    private void apply(Question q, QuestionRequest r) {
        List<OptionInput> inputs = r.options() == null ? List.of() : r.options();
        String answerKey;
        try {
            answerKey = validator.validate(r.questionType(), r.content(), r.answerKey(), inputs.stream()
                    .map(o -> new OptionDef(o.content(), o.correct(), o.matchText(), o.correctOrder())).toList());
        } catch (InvalidQuestionException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
        q.setQuestionType(r.questionType());
        q.setSkill(r.skill());
        q.setLevel(r.levelId() == null ? null
                : levelRepository.findById(r.levelId()).orElseThrow(() -> BusinessException.notFound("trình độ", r.levelId())));
        q.setContent(r.content().trim());
        q.setPassage(blankToNull(r.passage()));
        q.setMediaUrl(blankToNull(r.mediaUrl()));
        q.setAnswerKey(answerKey);
        q.setExplanation(blankToNull(r.explanation()));
        q.setDifficulty(r.difficulty());
        q.setDefaultPoints(r.defaultPoints());
        q.setActive(r.active());

        q.getOptions().clear();
        boolean matching = r.questionType() == QuestionType.MATCHING;
        boolean ordering = r.questionType() == QuestionType.ORDERING;
        boolean choice = r.questionType() == QuestionType.SINGLE_CHOICE || r.questionType() == QuestionType.MULTIPLE_CHOICE;
        for (int i = 0; i < inputs.size(); i++) {
            OptionInput in = inputs.get(i);
            // Chỉ giữ thuộc tính có ý nghĩa với dạng câu hỏi (tránh dữ liệu thừa gây hiểu nhầm khi chấm)
            q.getOptions().add(option(q, in.content().trim(), choice && in.correct(),
                    matching ? in.matchText().trim() : null, ordering ? in.correctOrder() : null, i + 1));
        }
    }

    private static QuestionOption option(Question q, String content, boolean correct, String matchText, Integer order, int sort) {
        QuestionOption o = new QuestionOption();
        o.setQuestion(q);
        o.setContent(content);
        o.setCorrect(correct);
        o.setMatchText(matchText);
        o.setCorrectOrder(order);
        o.setSortOrder(sort);
        return o;
    }

    private Question find(Long id) {
        return questionRepository.findWithOptionsById(id).orElseThrow(() -> BusinessException.notFound("câu hỏi", id));
    }

    /** Đoạn xem trước: bỏ thẻ HTML, gộp khoảng trắng, cắt 140 ký tự. */
    static String preview(String content) {
        String text = TAGS.matcher(content == null ? "" : content).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH - 1) + "…";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
