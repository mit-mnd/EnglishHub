package com.englishhub.modules.vocabulary.controller;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.LookupResponse;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.SaveWordRequest;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.UpdateNoteRequest;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.UserWordResponse;
import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.service.DictionaryService;
import com.englishhub.modules.vocabulary.service.UserWordService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Từ điển & sổ từ vựng")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VocabularyController {

    private final DictionaryService dictionaryService;
    private final UserWordService userWordService;

    @Operation(summary = "Tra từ / cụm từ (kho nội bộ trước, sau đó từ điển ngoài)")
    @GetMapping("/dictionary/lookup")
    public LookupResponse lookup(@AuthenticationPrincipal UserPrincipal principal,
                                 @RequestParam @NotBlank String q) {
        return dictionaryService.lookup(principal.id(), q);
    }

    @Operation(summary = "Sổ từ của tôi (phân trang, tìm kiếm). sort: createdAt | dueAt | status")
    @GetMapping("/vocabulary/my-words")
    public PageResponse<UserWordResponse> myWords(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserWord.Status status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userWordService.list(principal.id(), keyword, status, pageable);
    }

    @Operation(summary = "Các từ đã lưu (chữ thường) - để tô sáng trong bài đọc")
    @GetMapping("/vocabulary/my-words/headwords")
    public List<String> savedHeadwords(@AuthenticationPrincipal UserPrincipal principal) {
        return userWordService.savedHeadwords(principal.id());
    }

    @Operation(summary = "Lưu từ vào sổ (idempotent)")
    @PostMapping("/vocabulary/my-words")
    public UserWordResponse save(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody SaveWordRequest request) {
        return userWordService.save(principal.id(), request);
    }

    @Operation(summary = "Cập nhật ghi chú / nghĩa tự ghi")
    @PatchMapping("/vocabulary/my-words/{id}")
    public UserWordResponse updateNote(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                       @Valid @RequestBody UpdateNoteRequest request) {
        return userWordService.updateNote(principal.id(), id, request.note());
    }

    @Operation(summary = "Xóa từ khỏi sổ")
    @DeleteMapping("/vocabulary/my-words/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        userWordService.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
