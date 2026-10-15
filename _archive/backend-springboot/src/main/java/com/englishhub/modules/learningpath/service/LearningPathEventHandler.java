package com.englishhub.modules.learningpath.service;

import com.englishhub.modules.assessment.event.PlacementCompletedEvent;
import com.englishhub.modules.progress.event.LessonProgressEvents.LessonCompleted;
import com.englishhub.modules.progress.event.LessonProgressEvents.LessonStarted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cập nhật lộ trình theo sự kiện từ các module khác.
 * Listener đồng bộ: chạy trong cùng transaction với nghiệp vụ phát sự kiện (nộp bài, học bài).
 */
@Component
@RequiredArgsConstructor
public class LearningPathEventHandler {

    private final LearningPathService learningPathService;

    @EventListener
    public void onPlacementCompleted(PlacementCompletedEvent event) {
        learningPathService.generateIfAbsent(event.userId());
    }

    @EventListener
    public void onLessonStarted(LessonStarted event) {
        learningPathService.markLessonStarted(event.userId(), event.lessonId());
    }

    @EventListener
    public void onLessonCompleted(LessonCompleted event) {
        learningPathService.markLessonCompleted(event.userId(), event.lessonId());
    }
}
