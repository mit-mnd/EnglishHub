package com.englishhub.modules.learningpath.controller;

import com.englishhub.modules.learningpath.dto.LearningPathDtos.AddLessonRequest;
import com.englishhub.modules.learningpath.dto.LearningPathDtos.GeneratePathRequest;
import com.englishhub.modules.learningpath.dto.LearningPathDtos.LearningPathResponse;
import com.englishhub.modules.learningpath.dto.LearningPathDtos.ReorderRequest;
import com.englishhub.modules.learningpath.service.LearningPathService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Lộ trình học")
@RestController
@RequestMapping("/api/v1/learning-path")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService learningPathService;

    @Operation(summary = "Lộ trình đang chạy (204 nếu chưa có)")
    @GetMapping
    public ResponseEntity<LearningPathResponse> active(@AuthenticationPrincipal UserPrincipal principal) {
        return learningPathService.getActive(principal.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Tạo (lại) lộ trình. Bỏ trống body = theo kết quả xếp lớp và mục tiêu hiện tại")
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningPathResponse generate(@AuthenticationPrincipal UserPrincipal principal,
                                         @Valid @RequestBody(required = false) GeneratePathRequest request) {
        return learningPathService.generate(principal.id(), request);
    }

    @Operation(summary = "Thêm bài học vào cuối lộ trình")
    @PostMapping("/items")
    public LearningPathResponse addLesson(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody AddLessonRequest request) {
        return learningPathService.addLesson(principal.id(), request.lessonId());
    }

    @Operation(summary = "Xóa bài khỏi lộ trình")
    @DeleteMapping("/items/{itemId}")
    public LearningPathResponse removeItem(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long itemId) {
        return learningPathService.removeItem(principal.id(), itemId);
    }

    @Operation(summary = "Bỏ qua bài (đã biết)")
    @PostMapping("/items/{itemId}/skip")
    public LearningPathResponse skip(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long itemId) {
        return learningPathService.skipItem(principal.id(), itemId);
    }

    @Operation(summary = "Hủy bỏ qua")
    @PostMapping("/items/{itemId}/unskip")
    public LearningPathResponse unskip(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long itemId) {
        return learningPathService.unskipItem(principal.id(), itemId);
    }

    @Operation(summary = "Sắp xếp lại thứ tự bài (gửi toàn bộ itemIds theo thứ tự mới)")
    @PutMapping("/items/order")
    public LearningPathResponse reorder(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody ReorderRequest request) {
        return learningPathService.reorder(principal.id(), request.itemIds());
    }
}
