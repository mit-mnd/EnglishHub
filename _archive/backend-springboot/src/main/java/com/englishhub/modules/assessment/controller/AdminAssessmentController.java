package com.englishhub.modules.assessment.controller;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.ActiveRequest;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminQuestionDetail;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminQuestionRow;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminTestDetail;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.AdminTestRow;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.QuestionRequest;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.TestQuestionsRequest;
import com.englishhub.modules.assessment.dto.AdminAssessmentDtos.TestRequest;
import com.englishhub.modules.assessment.entity.Enums.QuestionType;
import com.englishhub.modules.assessment.entity.Enums.TestType;
import com.englishhub.modules.assessment.service.AdminQuestionService;
import com.englishhub.modules.assessment.service.AdminTestService;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.PublishRequest;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - câu hỏi & bài test")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAssessmentController {

    private final AdminQuestionService questionService;
    private final AdminTestService testService;

    // ------------------------------------------------------------------ Ngân hàng câu hỏi

    @Operation(summary = "Tìm câu hỏi. excludeTestId: ẩn câu đã có trong bài test đó. sort: id | difficulty | createdAt | updatedAt")
    @GetMapping("/questions")
    public PageResponse<AdminQuestionRow> searchQuestions(@RequestParam(required = false) QuestionType type,
                                                          @RequestParam(required = false) Skill skill,
                                                          @RequestParam(required = false) Integer levelId,
                                                          @RequestParam(required = false) Boolean active,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Long excludeTestId,
                                                          @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return questionService.search(type, skill, levelId, active, keyword, excludeTestId, pageable);
    }

    @GetMapping("/questions/{id}")
    public AdminQuestionDetail getQuestion(@PathVariable Long id) {
        return questionService.get(id);
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminQuestionDetail createQuestion(@Valid @RequestBody QuestionRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return questionService.create(request, principal.id());
    }

    @PutMapping("/questions/{id}")
    public AdminQuestionDetail updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return questionService.update(id, request);
    }

    @Operation(summary = "Bật / tắt câu hỏi (câu tắt không thể đưa vào bài test mới)")
    @PatchMapping("/questions/{id}/active")
    public AdminQuestionDetail setQuestionActive(@PathVariable Long id, @RequestBody ActiveRequest request) {
        return questionService.setActive(id, request.active());
    }

    @PostMapping("/questions/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminQuestionDetail duplicateQuestion(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return questionService.duplicate(id, principal.id());
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable Long id) {
        questionService.delete(id);
    }

    // ------------------------------------------------------------------ Bài test

    @Operation(summary = "Tìm bài test. sort: title | createdAt | updatedAt")
    @GetMapping("/tests")
    public PageResponse<AdminTestRow> searchTests(@RequestParam(required = false) TestType type,
                                                  @RequestParam(required = false) Long lessonId,
                                                  @RequestParam(required = false) Boolean published,
                                                  @RequestParam(required = false) String keyword,
                                                  @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return testService.search(type, lessonId, published, keyword, pageable);
    }

    @GetMapping("/tests/{id}")
    public AdminTestDetail getTest(@PathVariable Long id) {
        return testService.get(id);
    }

    @PostMapping("/tests")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminTestDetail createTest(@Valid @RequestBody TestRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return testService.create(request, principal.id());
    }

    @PutMapping("/tests/{id}")
    public AdminTestDetail updateTest(@PathVariable Long id, @Valid @RequestBody TestRequest request) {
        return testService.update(id, request);
    }

    @PatchMapping("/tests/{id}/publish")
    public AdminTestDetail publishTest(@PathVariable Long id, @RequestBody PublishRequest request) {
        return testService.setPublished(id, request.published());
    }

    @Operation(summary = "Thay danh sách câu hỏi (theo thứ tự, điểm riêng tùy chọn). 409 TEST_HAS_ATTEMPTS nếu đã có lượt làm")
    @PutMapping("/tests/{id}/questions")
    public AdminTestDetail replaceTestQuestions(@PathVariable Long id, @Valid @RequestBody TestQuestionsRequest request) {
        return testService.replaceQuestions(id, request.items());
    }

    @PostMapping("/tests/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminTestDetail duplicateTest(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return testService.duplicate(id, principal.id());
    }

    @DeleteMapping("/tests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTest(@PathVariable Long id) {
        testService.delete(id);
    }
}
