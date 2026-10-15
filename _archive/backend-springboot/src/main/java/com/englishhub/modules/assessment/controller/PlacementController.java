package com.englishhub.modules.assessment.controller;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.AnswerRequest;
import com.englishhub.modules.assessment.dto.PlacementDtos.AttemptResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.AttemptSummaryResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.PlacementResultResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.PlacementTestInfoResponse;
import com.englishhub.modules.assessment.dto.PlacementDtos.SubmitAttemptRequest;
import com.englishhub.modules.assessment.service.PlacementService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Placement Test")
@RestController
@RequestMapping("/api/v1/placement")
@RequiredArgsConstructor
public class PlacementController {

    private final PlacementService placementService;

    @Operation(summary = "Thông tin bài xếp lớp + bài đang làm dở + kết quả gần nhất")
    @GetMapping("/test")
    public PlacementTestInfoResponse getTestInfo(@AuthenticationPrincipal UserPrincipal principal) {
        return placementService.getTestInfo(principal.id());
    }

    @Operation(summary = "Bắt đầu bài mới hoặc tiếp tục bài đang làm (trả về câu hỏi, không kèm đáp án)")
    @PostMapping("/attempts")
    public AttemptResponse start(@AuthenticationPrincipal UserPrincipal principal) {
        return placementService.startOrResume(principal.id());
    }

    @Operation(summary = "Lưu tạm câu trả lời (autosave)")
    @PutMapping("/attempts/{attemptId}/answers")
    public ResponseEntity<Void> saveAnswer(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long attemptId,
                                           @Valid @RequestBody AnswerRequest request) {
        placementService.saveAnswer(principal.id(), attemptId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Nộp bài và nhận kết quả xếp lớp")
    @PostMapping("/attempts/{attemptId}/submit")
    public PlacementResultResponse submit(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long attemptId,
                                          @Valid @RequestBody(required = false) SubmitAttemptRequest request) {
        return placementService.submit(principal.id(), attemptId, request);
    }

    @Operation(summary = "Xem lại kết quả một bài đã nộp")
    @GetMapping("/attempts/{attemptId}/result")
    public PlacementResultResponse getResult(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long attemptId) {
        return placementService.getResult(principal.id(), attemptId);
    }

    @Operation(summary = "Lịch sử làm bài xếp lớp")
    @GetMapping("/attempts")
    public PageResponse<AttemptSummaryResponse> history(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 10, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return placementService.history(principal.id(), pageable);
    }
}
