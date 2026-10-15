package com.englishhub.modules.progress.controller;

import com.englishhub.modules.progress.dto.DashboardDtos.DashboardRange;
import com.englishhub.modules.progress.dto.DashboardDtos.DashboardResponse;
import com.englishhub.modules.progress.service.DashboardService;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Số liệu dashboard: streak, mục tiêu hôm nay, tổng quan, biểu đồ, lộ trình. range = 7d | 30d | 90d | 365d")
    @GetMapping
    public DashboardResponse getDashboard(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestParam(defaultValue = "7d") String range) {
        return dashboardService.getDashboard(principal.id(), DashboardRange.fromCode(range));
    }
}
