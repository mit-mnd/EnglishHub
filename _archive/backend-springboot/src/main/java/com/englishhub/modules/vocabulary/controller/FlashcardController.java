package com.englishhub.modules.vocabulary.controller;

import com.englishhub.modules.vocabulary.dto.FlashcardDtos.AddSetResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.QueueResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.ReviewRequest;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.ReviewResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.SetDetail;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.SetSummary;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.SummaryResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.SuspendRequest;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.UserWordResponse;
import com.englishhub.modules.vocabulary.service.FlashcardService;
import com.englishhub.modules.vocabulary.service.UserWordService;
import com.englishhub.modules.vocabulary.service.VocabularySetService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Flashcard & bộ từ vựng")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;
    private final VocabularySetService setService;
    private final UserWordService userWordService;

    @Operation(summary = "Tổng quan ôn tập: số thẻ đến hạn, thẻ mới còn trong ngày, dự báo 7 ngày")
    @GetMapping("/flashcards/summary")
    public SummaryResponse summary(@AuthenticationPrincipal UserPrincipal principal) {
        return flashcardService.summary(principal.id());
    }

    @Operation(summary = "Hàng đợi thẻ cho một phiên ôn (thẻ đến hạn trước, rồi thẻ mới theo giới hạn ngày)")
    @GetMapping("/flashcards/queue")
    public QueueResponse queue(@AuthenticationPrincipal UserPrincipal principal,
                               @RequestParam(defaultValue = "20") int limit) {
        return flashcardService.queue(principal.id(), limit);
    }

    @Operation(summary = "Chấm một thẻ: AGAIN | HARD | GOOD | EASY -> lịch ôn tiếp theo (SM-2)")
    @PostMapping("/flashcards/{userWordId}/review")
    public ReviewResponse review(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long userWordId,
                                 @Valid @RequestBody ReviewRequest request) {
        return flashcardService.review(principal.id(), userWordId, request);
    }

    @Operation(summary = "Tạm ẩn / bỏ tạm ẩn một từ khỏi lịch ôn")
    @PatchMapping("/vocabulary/my-words/{id}/suspend")
    public UserWordResponse suspend(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                    @Valid @RequestBody SuspendRequest request) {
        return userWordService.setSuspended(principal.id(), id, request.suspended());
    }

    @Operation(summary = "Danh sách bộ từ vựng có sẵn (kèm số từ đã có trong sổ)")
    @GetMapping("/vocabulary/sets")
    public List<SetSummary> sets(@AuthenticationPrincipal UserPrincipal principal) {
        return setService.list(principal.id());
    }

    @Operation(summary = "Chi tiết bộ từ")
    @GetMapping("/vocabulary/sets/{id}")
    public SetDetail set(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return setService.get(principal.id(), id);
    }

    @Operation(summary = "Thêm cả bộ vào sổ từ để ôn flashcard")
    @PostMapping("/vocabulary/sets/{id}/add")
    public AddSetResponse addSet(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return setService.addToNotebook(principal.id(), id);
    }
}
