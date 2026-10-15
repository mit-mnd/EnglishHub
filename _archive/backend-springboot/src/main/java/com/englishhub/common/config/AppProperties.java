package com.englishhub.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** Cấu hình riêng của ứng dụng, prefix {@code englishhub.*} trong application.yml. */
@Validated
@ConfigurationProperties(prefix = "englishhub")
public record AppProperties(
        @NotBlank String frontendUrl,
        @NotNull Cors cors,
        @NotNull Jwt jwt,
        @NotNull Security security,
        @NotNull Placement placement,
        @NotNull Dictionary dictionary,
        @NotNull Storage storage,
        @NotNull Ai ai,
        @NotNull Srs srs) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Jwt(
            @NotBlank(message = "Thiếu biến môi trường JWT_SECRET") String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record Security(@NotNull Duration passwordResetTtl) {
    }

    public record Placement(@NotNull Duration gracePeriod) {
    }

    /** provider = free-dictionary | none (tắt tra từ điển ngoài, chỉ dùng kho từ nội bộ). */
    public record Dictionary(@NotBlank String provider, @NotBlank String baseUrl, @NotNull Duration timeout) {
    }

    /** Lặp lại ngắt quãng: số thẻ MỚI tối đa đưa vào học mỗi ngày (tránh quá tải khi thêm cả bộ từ). */
    public record Srs(@Min(1) int newCardsPerDay) {
    }

    /** Thư mục lưu file người dùng tải lên (bản ghi âm...). */
    public record Storage(@NotBlank String dir) {
    }

    public record Ai(@NotNull Speech speech, @NotNull Writing writing) {

        /** provider = mock | azure. azureKey/azureRegion chỉ cần khi provider = azure. */
        public record Speech(@NotBlank String provider, String azureKey, String azureRegion, @NotNull Duration timeout) {
        }

        /** provider = rule-based (mặc định, không cần API key). */
        public record Writing(@NotBlank String provider) {
        }
    }
}
