package com.englishhub.modules.vocabulary.dto;

import com.englishhub.modules.vocabulary.dto.VocabularyDtos.WordResponse;
import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler.Grade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class FlashcardDtos {

    private FlashcardDtos() {
    }

    /** Một thẻ để ôn; {@code intervals} = số giây tới lần ôn sau nếu chọn từng nút (hiển thị trên nút). */
    public record FlashcardResponse(
            Long userWordId,
            UserWord.Status status,
            int repetitions,
            int intervalDays,
            Instant dueAt,
            String contextSentence,
            String note,
            WordResponse word,
            Map<Grade, Long> intervals) {
    }

    public record QueueResponse(
            List<FlashcardResponse> cards,
            int dueCount,
            int newCount,
            int newRemainingToday) {
    }

    public record ReviewRequest(
            @NotNull(message = "Thiếu mức đánh giá") Grade grade,
            @Min(0) @Max(600_000) Integer responseTimeMs) {
    }

    public record ReviewResponse(
            FlashcardResponse card,
            Grade grade,
            Instant nextDueAt,
            /** true nếu thẻ cần ôn lại ngay trong phiên này (chọn "Quên"). */
            boolean relearnInSession) {
    }

    public record ForecastDay(LocalDate date, int count) {
    }

    public record SummaryResponse(
            int total,
            int newCards,
            int learning,
            int review,
            int mastered,
            int suspended,
            int dueNow,
            int newAvailableToday,
            int newCardsPerDay,
            int reviewedToday,
            Instant nextDueAt,
            List<ForecastDay> forecast) {
    }

    public record SetSummary(
            Long id,
            String name,
            String slug,
            String description,
            Integer levelId,
            int wordCount,
            int addedCount,
            List<String> previewWords) {
    }

    public record SetDetail(SetSummary set, List<WordResponse> words) {
    }

    public record AddSetResponse(int added, int alreadyInNotebook) {
    }

    public record SuspendRequest(@NotNull Boolean suspended) {
    }
}
