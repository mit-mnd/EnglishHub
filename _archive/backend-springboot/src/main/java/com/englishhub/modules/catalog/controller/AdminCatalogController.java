package com.englishhub.modules.catalog.controller;

import com.englishhub.common.domain.Skill;
import com.englishhub.common.dto.PageResponse;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminCourseDetail;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminCourseRow;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminLessonDetail;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.AdminLessonRow;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.CourseRequest;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.LessonRequest;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.PublishRequest;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.ReorderRequest;
import com.englishhub.modules.catalog.dto.AdminCatalogDtos.TranscriptRequest;
import com.englishhub.modules.catalog.service.AdminCourseService;
import com.englishhub.modules.catalog.service.AdminLessonService;
import com.englishhub.modules.user.entity.LearningGoal;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import java.util.List;

@Tag(name = "Admin - khóa học & bài học")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final AdminCourseService courseService;
    private final AdminLessonService lessonService;

    // ------------------------------------------------------------------ Khóa học

    @Operation(summary = "Tìm khóa học (cả bản nháp). sort: title | sortOrder | createdAt | updatedAt")
    @GetMapping("/courses")
    public PageResponse<AdminCourseRow> searchCourses(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer levelId,
                                                      @RequestParam(required = false) LearningGoal goal,
                                                      @RequestParam(required = false) Boolean published,
                                                      @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return courseService.search(keyword, levelId, goal, published, pageable);
    }

    @Operation(summary = "Tất cả khóa học dạng gọn (cho ô chọn)")
    @GetMapping("/courses/options")
    public List<AdminCourseRow> courseOptions() {
        return courseService.options();
    }

    @GetMapping("/courses/{id}")
    public AdminCourseDetail getCourse(@PathVariable Long id) {
        return courseService.get(id);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCourseDetail createCourse(@Valid @RequestBody CourseRequest request,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return courseService.create(request, principal.id());
    }

    @PutMapping("/courses/{id}")
    public AdminCourseDetail updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return courseService.update(id, request);
    }

    @PatchMapping("/courses/{id}/publish")
    public AdminCourseDetail publishCourse(@PathVariable Long id, @RequestBody PublishRequest request) {
        return courseService.setPublished(id, request.published());
    }

    @Operation(summary = "Sắp xếp lại bài học trong khóa (gửi đủ id theo thứ tự mới)")
    @PutMapping("/courses/{id}/lesson-order")
    public AdminCourseDetail reorderLessons(@PathVariable Long id, @Valid @RequestBody ReorderRequest request) {
        return courseService.reorderLessons(id, request.ids());
    }

    @Operation(summary = "Xóa khóa học (chỉ khi chưa có học viên học, ngược lại 409 CONTENT_IN_USE)")
    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@PathVariable Long id) {
        courseService.delete(id);
    }

    // ------------------------------------------------------------------ Bài học

    @Operation(summary = "Tìm bài học (cả bản nháp). sort: title | sortOrder | createdAt | updatedAt")
    @GetMapping("/lessons")
    public PageResponse<AdminLessonRow> searchLessons(@RequestParam(required = false) Long courseId,
                                                      @RequestParam(required = false) Skill skill,
                                                      @RequestParam(required = false) Integer levelId,
                                                      @RequestParam(required = false) Boolean published,
                                                      @RequestParam(required = false) String keyword,
                                                      @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return lessonService.search(courseId, skill, levelId, published, keyword, pageable);
    }

    @GetMapping("/lessons/{id}")
    public AdminLessonDetail getLesson(@PathVariable Long id) {
        return lessonService.get(id);
    }

    @PostMapping("/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminLessonDetail createLesson(@Valid @RequestBody LessonRequest request) {
        return lessonService.create(request);
    }

    @PutMapping("/lessons/{id}")
    public AdminLessonDetail updateLesson(@PathVariable Long id, @Valid @RequestBody LessonRequest request) {
        return lessonService.update(id, request);
    }

    @PatchMapping("/lessons/{id}/publish")
    public AdminLessonDetail publishLesson(@PathVariable Long id, @RequestBody PublishRequest request) {
        return lessonService.setPublished(id, request.published());
    }

    @Operation(summary = "Thay toàn bộ phụ đề có mốc thời gian của bài")
    @PutMapping("/lessons/{id}/transcript")
    public AdminLessonDetail replaceTranscript(@PathVariable Long id, @Valid @RequestBody TranscriptRequest request) {
        return lessonService.replaceTranscript(id, request.segments());
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(@PathVariable Long id) {
        lessonService.delete(id);
    }
}
