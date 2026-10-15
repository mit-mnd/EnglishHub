package com.englishhub.modules.catalog.controller;

import com.englishhub.modules.catalog.dto.LevelResponse;
import com.englishhub.modules.catalog.repository.LevelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Levels")
@RestController
@RequestMapping("/api/v1/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelRepository levelRepository;

    @Operation(summary = "Danh sách trình độ (public)")
    @GetMapping
    @Transactional(readOnly = true)
    public List<LevelResponse> getLevels() {
        return levelRepository.findAllByOrderBySortOrderAsc().stream().map(LevelResponse::from).toList();
    }
}
