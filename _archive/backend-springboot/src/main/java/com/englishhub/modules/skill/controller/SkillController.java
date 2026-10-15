package com.englishhub.modules.skill.controller;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.skill.dto.SkillDtos.SpeakingRequest;
import com.englishhub.modules.skill.dto.SkillDtos.SpeakingResultResponse;
import com.englishhub.modules.skill.dto.SkillDtos.WritingRequest;
import com.englishhub.modules.skill.dto.SkillDtos.WritingResultResponse;
import com.englishhub.modules.skill.service.SpeakingService;
import com.englishhub.modules.skill.service.WritingService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "Luyện nói & luyện viết")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SkillController {

    private final SpeakingService speakingService;
    private final WritingService writingService;

    @Operation(summary = "Nộp bản ghi âm (multipart: audio = file WAV 16 kHz + questionId hoặc referenceText) và nhận điểm phát âm")
    @PostMapping(value = "/speaking/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpeakingResultResponse submitSpeaking(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestPart("audio") MultipartFile audio,
                                                 @Valid @ModelAttribute SpeakingRequest request) throws IOException {
        return speakingService.submit(principal.id(), audio.getBytes(), request);
    }

    @Operation(summary = "Lịch sử luyện nói (lọc theo bài học)")
    @GetMapping("/speaking/submissions")
    public PageResponse<SpeakingResultResponse> speakingHistory(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestParam(required = false) Long lessonId,
                                                                @PageableDefault(size = 10) Pageable pageable) {
        return speakingService.history(principal.id(), lessonId, pageable);
    }

    @Operation(summary = "Nghe lại bản ghi âm của chính mình")
    @GetMapping("/speaking/submissions/{id}/audio")
    public ResponseEntity<byte[]> speakingAudio(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(CacheControl.noStore())
                .body(speakingService.audio(principal.id(), id));
    }

    @Operation(summary = "Chấm bài viết tự do (không gắn bài tập) và lưu lịch sử")
    @PostMapping("/writing/evaluate")
    public WritingResultResponse evaluateWriting(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody WritingRequest request) {
        return writingService.evaluate(principal.id(), request.text(), request.prompt(), request.minWords(),
                request.maxWords(), request.lessonId(), null);
    }
}
