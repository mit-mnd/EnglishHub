package com.englishhub.modules.assessment.controller;

import com.englishhub.modules.assessment.dto.ExerciseDtos.CheckAnswerRequest;
import com.englishhub.modules.assessment.dto.ExerciseDtos.CheckResult;
import com.englishhub.modules.assessment.dto.ExerciseDtos.ExerciseAttemptResponse;
import com.englishhub.modules.assessment.dto.ExerciseDtos.ExerciseResult;
import com.englishhub.modules.assessment.dto.ExerciseDtos.ExerciseSummary;
import com.englishhub.modules.assessment.service.ExerciseService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Bài tập trong bài học")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    @Operation(summary = "Danh sách bài tập / quiz của bài học kèm kết quả tốt nhất")
    @GetMapping("/lessons/{lessonId}/exercises")
    public List<ExerciseSummary> list(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long lessonId) {
        return exerciseService.listForLesson(principal.id(), lessonId);
    }

    @Operation(summary = "Bắt đầu / tiếp tục làm bài tập")
    @PostMapping("/exercises/{testId}/attempts")
    public ExerciseAttemptResponse start(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long testId) {
        return exerciseService.start(principal.id(), testId);
    }

    @Operation(summary = "Chấm một câu (trả về đáp án đúng, lời giải, nhận xét bài viết / phát âm)")
    @PostMapping("/exercises/attempts/{attemptId}/check")
    public CheckResult check(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long attemptId,
                             @Valid @RequestBody CheckAnswerRequest request) {
        return exerciseService.check(principal.id(), attemptId, request);
    }

    @Operation(summary = "Nộp bài tập: điểm tổng, đạt/chưa đạt, hoàn thành bài học nếu đạt")
    @PostMapping("/exercises/attempts/{attemptId}/finish")
    public ExerciseResult finish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long attemptId) {
        return exerciseService.finish(principal.id(), attemptId);
    }

    @Operation(summary = "Xem lại kết quả một lượt làm đã nộp")
    @GetMapping("/exercises/attempts/{attemptId}/result")
    public ExerciseResult result(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long attemptId) {
        return exerciseService.result(principal.id(), attemptId);
    }
}
