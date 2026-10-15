package com.englishhub.modules.vocabulary.controller;

import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.PublishRequest;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.AdminSetDetail;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.AdminSetRow;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.AdminWord;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.SetRequest;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.SetWordsRequest;
import com.englishhub.modules.vocabulary.dto.AdminVocabularyDtos.WordRequest;
import com.englishhub.modules.vocabulary.entity.Word;
import com.englishhub.modules.vocabulary.service.AdminVocabularyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Tag(name = "Admin - từ vựng")
@RestController
@RequestMapping("/api/v1/admin/vocabulary")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVocabularyController {

    private final AdminVocabularyService service;

    @Operation(summary = "Tìm từ trong kho. excludeSetId: ẩn từ đã có trong bộ đó. sort: headword | createdAt | updatedAt")
    @GetMapping("/words")
    public PageResponse<AdminWord> searchWords(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer levelId,
                                               @RequestParam(required = false) String partOfSpeech,
                                               @RequestParam(required = false) Word.Source source,
                                               @RequestParam(required = false) Long excludeSetId,
                                               @PageableDefault(sort = "headword") Pageable pageable) {
        return service.searchWords(keyword, levelId, partOfSpeech, source, excludeSetId, pageable);
    }

    @GetMapping("/words/{id}")
    public AdminWord getWord(@PathVariable Long id) {
        return service.getWord(id);
    }

    @PostMapping("/words")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminWord createWord(@Valid @RequestBody WordRequest request) {
        return service.createWord(request);
    }

    @PutMapping("/words/{id}")
    public AdminWord updateWord(@PathVariable Long id, @Valid @RequestBody WordRequest request) {
        return service.updateWord(id, request);
    }

    @DeleteMapping("/words/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWord(@PathVariable Long id) {
        service.deleteWord(id);
    }

    @Operation(summary = "Bộ từ hệ thống (cả bản nháp). sort: name | createdAt | updatedAt")
    @GetMapping("/sets")
    public PageResponse<AdminSetRow> searchSets(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer levelId,
                                                @RequestParam(required = false) Boolean published,
                                                @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.searchSets(keyword, levelId, published, pageable);
    }

    @GetMapping("/sets/{id}")
    public AdminSetDetail getSet(@PathVariable Long id) {
        return service.getSet(id);
    }

    @PostMapping("/sets")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSetDetail createSet(@Valid @RequestBody SetRequest request) {
        return service.createSet(request);
    }

    @PutMapping("/sets/{id}")
    public AdminSetDetail updateSet(@PathVariable Long id, @Valid @RequestBody SetRequest request) {
        return service.updateSet(id, request);
    }

    @Operation(summary = "Thay danh sách từ của bộ (theo thứ tự)")
    @PutMapping("/sets/{id}/words")
    public AdminSetDetail replaceSetWords(@PathVariable Long id, @Valid @RequestBody SetWordsRequest request) {
        return service.replaceSetWords(id, request.wordIds());
    }

    @PatchMapping("/sets/{id}/publish")
    public AdminSetDetail publishSet(@PathVariable Long id, @RequestBody PublishRequest request) {
        return service.setSetPublished(id, request.published());
    }

    @DeleteMapping("/sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSet(@PathVariable Long id) {
        service.deleteSet(id);
    }
}
