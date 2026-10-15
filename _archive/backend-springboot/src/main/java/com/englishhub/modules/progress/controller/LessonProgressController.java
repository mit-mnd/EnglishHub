package com.englishhub.modules.progress.controller;

import com.englishhub.modules.catalog.dto.CatalogDtos.ProgressInfo;
import com.englishhub.modules.progress.dto.LessonProgressDtos.CompleteRequest;
import com.englishhub.modules.progress.dto.LessonProgressDtos.CompleteResponse;
import com.englishhub.modules.progress.dto.LessonProgressDtos.HeartbeatRequest;
import com.englishhub.modules.progress.service.LessonProgressService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tiến độ bài học")
@RestController
@RequestMapping("/api/v1/lessons/{lessonId}")
@RequiredArgsConstructor
public class LessonProgressController {

    private final LessonProgressService lessonProgressService;

    @Operation(summary = "Ghi nhận thời gian học / vị trí media / % đã học (gọi định kỳ ~30s)")
    @PostMapping("/progress")
    public ProgressInfo heartbeat(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long lessonId,
                                  @Valid @RequestBody HeartbeatRequest request) {
        return lessonProgressService.heartbeat(principal.id(), lessonId, request);
    }

    @Operation(summary = "Đánh dấu hoàn thành bài học")
    @PostMapping("/complete")
    public CompleteResponse complete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long lessonId,
                                     @Valid @RequestBody(required = false) CompleteRequest request) {
        return lessonProgressService.complete(principal.id(), lessonId, request);
    }
}
