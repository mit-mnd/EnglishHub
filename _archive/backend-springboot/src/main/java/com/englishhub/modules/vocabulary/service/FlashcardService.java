package com.englishhub.modules.vocabulary.service;

import com.englishhub.common.config.AppProperties;
import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.modules.progress.repository.ActivityWriteRepository.DailyDelta;
import com.englishhub.modules.progress.service.ActivityService;
import com.englishhub.modules.user.entity.User;
import com.englishhub.modules.user.repository.UserRepository;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.FlashcardResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.ForecastDay;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.QueueResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.ReviewRequest;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.ReviewResponse;
import com.englishhub.modules.vocabulary.dto.FlashcardDtos.SummaryResponse;
import com.englishhub.modules.vocabulary.dto.VocabularyDtos.WordResponse;
import com.englishhub.modules.vocabulary.entity.FlashcardReview;
import com.englishhub.modules.vocabulary.entity.UserWord;
import com.englishhub.modules.vocabulary.entity.UserWord.Status;
import com.englishhub.modules.vocabulary.repository.FlashcardReviewRepository;
import com.englishhub.modules.vocabulary.repository.UserWordRepository;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler.CardState;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler.Grade;
import com.englishhub.modules.vocabulary.srs.Sm2Scheduler.Schedule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ôn tập flashcard theo lặp lại ngắt quãng (SM-2, xem {@link Sm2Scheduler}).
 *
 * <p>Hàng đợi một phiên = thẻ đến hạn (hạn sớm nhất trước) + thẻ mới, với giới hạn số thẻ mới mỗi ngày
 * (englishhub.srs.new-cards-per-day) để học viên không bị "ngợp" khi thêm cả bộ từ.
 * Mỗi lần ôn được ghi vào FlashcardReviews và tính vào thời gian học, số từ ôn, streak.</p>
 */
@Service
@RequiredArgsConstructor
public class FlashcardService {

    /** Thẻ không nằm trong lịch ôn: thẻ mới (chưa học) và thẻ tạm ẩn. */
    static final Set<Status> NOT_SCHEDULED = EnumSet.of(Status.NEW, Status.SUSPENDED);
    private static final int MAX_QUEUE = 50;
    private static final int DEFAULT_SECONDS_PER_CARD = 8;
    private static final int MAX_SECONDS_PER_CARD = 60;
    private static final int FORECAST_DAYS = 7;

    private final UserWordRepository userWordRepository;
    private final FlashcardReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long userId) {
        ZoneId zone = zoneOf(userId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();

        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (Object[] row : userWordRepository.countByStatus(userId)) {
            counts.put((Status) row[0], ((Number) row[1]).intValue());
        }
        int dueNow = (int) userWordRepository.countDue(userId, now, NOT_SCHEDULED);
        int newAvailable = Math.min(counts.getOrDefault(Status.NEW, 0), newRemainingToday(userId, dayStart));

        // Dự báo: số thẻ đến hạn mỗi ngày trong 7 ngày tới (hôm nay gồm cả thẻ đang đến hạn)
        Map<LocalDate, Integer> forecast = new LinkedHashMap<>();
        for (int i = 0; i < FORECAST_DAYS; i++) {
            forecast.put(today.plusDays(i), 0);
        }
        forecast.merge(today, dueNow, Integer::sum);
        Instant forecastEnd = today.plusDays(FORECAST_DAYS).atStartOfDay(zone).toInstant();
        for (Instant due : userWordRepository.findDueTimesBetween(userId, now, forecastEnd, NOT_SCHEDULED)) {
            forecast.merge(LocalDate.ofInstant(due, zone), 1, Integer::sum);
        }

        Instant nextDue = userWordRepository
                .findFirstByUserIdAndStatusNotInAndDueAtAfterOrderByDueAtAsc(userId, NOT_SCHEDULED, now)
                .map(UserWord::getDueAt).orElse(null);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new SummaryResponse(total, counts.getOrDefault(Status.NEW, 0), counts.getOrDefault(Status.LEARNING, 0),
                counts.getOrDefault(Status.REVIEW, 0), counts.getOrDefault(Status.MASTERED, 0),
                counts.getOrDefault(Status.SUSPENDED, 0), dueNow, newAvailable,
                appProperties.srs().newCardsPerDay(),
                (int) reviewRepository.countByUserIdAndReviewedAtGreaterThanEqual(userId, dayStart), nextDue,
                forecast.entrySet().stream().map(e -> new ForecastDay(e.getKey(), e.getValue())).toList());
    }

    @Transactional(readOnly = true)
    public QueueResponse queue(Long userId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_QUEUE));
        Instant now = clock.instant();
        ZoneId zone = zoneOf(userId);
        Instant dayStart = LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();

        List<UserWord> due = userWordRepository.findDue(userId, now, NOT_SCHEDULED, PageRequest.of(0, limit));
        int newRemaining = newRemainingToday(userId, dayStart);
        int newSlots = Math.min(newRemaining, limit - due.size());
        List<UserWord> fresh = newSlots <= 0 ? List.of()
                : userWordRepository.findByUserIdAndStatusOrderByCreatedAtAscIdAsc(userId, Status.NEW, PageRequest.of(0, newSlots));

        List<FlashcardResponse> cards = new ArrayList<>();
        due.forEach(uw -> cards.add(toResponse(uw)));
        fresh.forEach(uw -> cards.add(toResponse(uw)));
        return new QueueResponse(cards, due.size(), fresh.size(), Math.max(0, newRemaining - fresh.size()));
    }

    @Transactional
    public ReviewResponse review(Long userId, Long userWordId, ReviewRequest request) {
        UserWord card = userWordRepository.findForUpdate(userWordId, userId)
                .orElseThrow(() -> BusinessException.notFound("thẻ", userWordId));
        if (card.getStatus() == Status.SUSPENDED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Từ này đang tạm ẩn khỏi lịch ôn");
        }
        Instant now = clock.instant();
        boolean wasNew = card.getStatus() == Status.NEW;
        CardState before = stateOf(card);
        Schedule schedule = Sm2Scheduler.schedule(before, request.grade());
        CardState after = schedule.state();

        card.setEaseFactor(after.easeFactor());
        card.setIntervalDays(after.intervalDays());
        card.setRepetitions(after.repetitions());
        card.setLapseCount(after.lapseCount());
        card.setStatus(after.status());
        card.setDueAt(now.plus(schedule.delay()));
        card.setLastReviewedAt(now);

        FlashcardReview log = new FlashcardReview();
        log.setUserWordId(card.getId());
        log.setUserId(userId);
        log.setQuality(request.grade().quality());
        log.setPrevEaseFactor(before.easeFactor());
        log.setNewEaseFactor(after.easeFactor());
        log.setPrevIntervalDays(before.intervalDays());
        log.setNewIntervalDays(after.intervalDays());
        log.setResponseTimeMs(request.responseTimeMs());
        log.setReviewedAt(now);
        reviewRepository.save(log);

        // Thời gian học = thời gian suy nghĩ trên thẻ (giới hạn 60 giây để không tính lúc bỏ máy)
        int seconds = request.responseTimeMs() == null ? DEFAULT_SECONDS_PER_CARD
                : Math.max(1, Math.min(MAX_SECONDS_PER_CARD, Math.round(request.responseTimeMs() / 1000f)));
        boolean learnedNow = wasNew && request.grade() != Grade.AGAIN;
        activityService.record(userId, new DailyDelta(seconds, 0, learnedNow ? 1 : 0, 1, 0, BigDecimal.ZERO));

        return new ReviewResponse(toResponse(card), request.grade(), card.getDueAt(), request.grade() == Grade.AGAIN);
    }

    private int newRemainingToday(Long userId, Instant dayStart) {
        long introduced = reviewRepository.countNewIntroducedSince(userId, dayStart);
        return (int) Math.max(0, appProperties.srs().newCardsPerDay() - introduced);
    }

    static CardState stateOf(UserWord uw) {
        return new CardState(uw.getEaseFactor(), uw.getIntervalDays(), uw.getRepetitions(), uw.getLapseCount(), uw.getStatus());
    }

    private FlashcardResponse toResponse(UserWord uw) {
        Map<Grade, Long> intervals = new EnumMap<>(Grade.class);
        Sm2Scheduler.preview(stateOf(uw)).forEach((grade, delay) -> intervals.put(grade, delay.toSeconds()));
        return new FlashcardResponse(uw.getId(), uw.getStatus(), uw.getRepetitions(), uw.getIntervalDays(), uw.getDueAt(),
                uw.getContextSentence(), uw.getNote(), WordResponse.of(uw.getWord(), uw), intervals);
    }

    private ZoneId zoneOf(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("người dùng", userId));
        return ZoneId.of(user.getTimeZone());
    }

    /** Dùng khi bỏ tạm ẩn: tính lại trạng thái theo tiến độ, thẻ quá hạn thì ôn ngay. */
    static void restoreFromSuspended(UserWord uw, Instant now) {
        uw.setStatus(Sm2Scheduler.statusFor(uw.getIntervalDays(), uw.getRepetitions()));
        if (uw.getDueAt().isBefore(now)) {
            uw.setDueAt(now);
        }
    }
}
