package com.englishhub.modules.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

@Tag(name = "Admin - báo cáo")
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final AdminAnalyticsService analyticsService;
    private final Clock clock;

    @Operation(summary = "Tổng quan: số học viên, học viên hoạt động, kết quả xếp lớp, phân bố level")
    @GetMapping("/overview")
    public AdminReportService.OverviewResponse overview() {
        return adminReportService.overview();
    }

    @Operation(summary = "Hoạt động theo ngày (7-365 ngày): học viên hoạt động, đăng ký mới, phút học, bài hoàn thành, lượt ôn từ")
    @GetMapping("/activity")
    public AdminAnalyticsService.ActivityReport activity(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.activity(days);
    }

    @Operation(summary = "Hiệu quả nội dung: bài học nhiều/ít hoàn thành, bài test, câu hỏi khó nhất, điểm nói/viết, từ vựng")
    @GetMapping("/content")
    public AdminAnalyticsService.ContentReport content() {
        return analyticsService.content();
    }

    @Operation(summary = "Xuất danh sách học viên kèm số liệu học tập (CSV UTF-8, mở được bằng Excel)")
    @GetMapping(value = "/learners.csv", produces = "text/csv")
    public ResponseEntity<byte[]> learnersCsv() {
        String fileName = "hoc-vien-" + LocalDate.now(clock) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(analyticsService.learnersCsv().getBytes(StandardCharsets.UTF_8));
    }
}
