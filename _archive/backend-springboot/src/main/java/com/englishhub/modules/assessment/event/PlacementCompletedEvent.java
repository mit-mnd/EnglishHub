package com.englishhub.modules.assessment.event;

/** Học viên vừa hoàn thành (nộp hoặc hết giờ) bài kiểm tra xếp lớp. */
public record PlacementCompletedEvent(Long userId, Long attemptId) {
}
