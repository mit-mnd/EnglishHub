package com.englishhub.modules.progress.dto;

import com.englishhub.modules.catalog.dto.CatalogDtos.LessonNav;
import com.englishhub.modules.catalog.dto.CatalogDtos.ProgressInfo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class LessonProgressDtos {

    private LessonProgressDtos() {
    }

    /**
     * Client gửi định kỳ (~30 giây) khi học viên đang học bài.
     *
     * @param seconds         số giây học THỰC SỰ kể từ lần gửi trước (tối đa 120 để chống gian lận/lỗi đồng hồ)
     * @param positionMs      vị trí audio/video hiện tại (để lần sau tiếp tục)
     * @param progressPercent % đã xem/đọc (chỉ tăng, không giảm)
     */
    public record HeartbeatRequest(
            @NotNull @Min(0) @Max(value = 120, message = "Mỗi lần ghi nhận tối đa 120 giây") Integer seconds,
            @Min(0) Integer positionMs,
            @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent) {
    }

    public record CompleteRequest(@DecimalMin("0") @DecimalMax("100") BigDecimal score) {
    }

    public record CompleteResponse(ProgressInfo progress, boolean firstCompletion, LessonNav nextInCourse) {
    }
}
