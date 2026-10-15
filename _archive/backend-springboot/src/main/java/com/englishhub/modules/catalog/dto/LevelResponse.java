package com.englishhub.modules.catalog.dto;

import com.englishhub.modules.catalog.entity.Level;

import java.math.BigDecimal;

public record LevelResponse(
        Integer id,
        String code,
        String name,
        String description,
        String cefrRange,
        BigDecimal placementMinPercent,
        BigDecimal placementMaxPercent,
        Integer sortOrder) {

    public static LevelResponse from(Level level) {
        if (level == null) {
            return null;
        }
        return new LevelResponse(level.getId(), level.getCode(), level.getName(), level.getDescription(),
                level.getCefrRange(), level.getPlacementMinPercent(), level.getPlacementMaxPercent(),
                level.getSortOrder());
    }
}
