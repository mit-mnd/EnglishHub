package com.englishhub.modules.catalog.controller;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.catalog.dto.CatalogDtos.CourseDetail;
import com.englishhub.modules.catalog.dto.CatalogDtos.CourseSummary;
import com.englishhub.modules.catalog.dto.CatalogDtos.LessonCard;
import com.englishhub.modules.catalog.dto.CatalogDtos.LessonDetail;
import com.englishhub.modules.catalog.service.CatalogService;
import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Khóa học & bài học")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(summary = "Danh sách khóa học (lọc theo level, mục tiêu) kèm tiến độ")
    @GetMapping("/courses")
    public List<CourseSummary> courses(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(required = false) Integer levelId,
                                       @RequestParam(required = false) LearningGoal goal) {
        return catalogService.listCourses(principal.id(), levelId, goal);
    }

    @Operation(summary = "Chi tiết khóa học và danh sách bài học")
    @GetMapping("/courses/{courseId}")
    public CourseDetail course(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long courseId) {
        return catalogService.getCourse(principal.id(), courseId);
    }

    @Operation(summary = "Tìm bài học (lọc kỹ năng, level, từ khóa)")
    @GetMapping("/lessons")
    public PageResponse<LessonCard> searchLessons(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Skill skill,
            @RequestParam(required = false) Integer levelId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return catalogService.searchLessons(principal.id(), skill, levelId, keyword, pageable);
    }

    @Operation(summary = "Chi tiết bài học: nội dung, media, phụ đề, tiến độ, bài trước/sau")
    @GetMapping("/lessons/{lessonId}")
    public LessonDetail lesson(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long lessonId) {
        return catalogService.getLesson(principal.id(), lessonId);
    }
}
