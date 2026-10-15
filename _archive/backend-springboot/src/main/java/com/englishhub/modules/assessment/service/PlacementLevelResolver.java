package com.englishhub.modules.assessment.service;

import com.englishhub.modules.catalog.entity.Level;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;

/**
 * Xếp level từ % điểm Placement Test.
 * Quy tắc: chọn level có PlacementMinPercent LỚN NHẤT mà vẫn <= % của học viên.
 * Cách này không phụ thuộc PlacementMaxPercent nên không có "khe hở" do làm tròn giữa các khoảng.
 */
public final class PlacementLevelResolver {

    private PlacementLevelResolver() {
    }

    public static Level resolve(BigDecimal percentage, Collection<Level> levels) {
        if (levels.isEmpty()) {
            throw new IllegalStateException("Chưa cấu hình bảng Levels");
        }
        Comparator<Level> byMin = Comparator.comparing(Level::getPlacementMinPercent);
        return levels.stream()
                .filter(level -> level.getPlacementMinPercent().compareTo(percentage) <= 0)
                .max(byMin)
                .orElseGet(() -> levels.stream().min(byMin).orElseThrow());
    }
}
