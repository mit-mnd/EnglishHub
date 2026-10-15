package com.englishhub.modules.assessment.service;

import com.englishhub.modules.catalog.entity.Level;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementLevelResolverTest {

    /** Giống seed data V2. */
    private static final List<Level> LEVELS = List.of(
            level("EXAM_PREP", "86.00"),
            level("FOUNDATION", "0.00"),
            level("INTERMEDIATE", "46.00"),
            level("ELEMENTARY", "21.00"),
            level("ADVANCED", "71.00"));

    @ParameterizedTest
    @CsvSource({
            "0.00,   FOUNDATION",
            "20.99,  FOUNDATION",
            "20.995, FOUNDATION",
            "21.00,  ELEMENTARY",
            "45.99,  ELEMENTARY",
            "46.00,  INTERMEDIATE",
            "70.99,  INTERMEDIATE",
            "71.00,  ADVANCED",
            "85.99,  ADVANCED",
            "86.00,  EXAM_PREP",
            "100.00, EXAM_PREP"
    })
    void resolvesLevelByMinimumPercent(String percentage, String expectedCode) {
        assertThat(PlacementLevelResolver.resolve(new BigDecimal(percentage), LEVELS).getCode())
                .isEqualTo(expectedCode);
    }

    private static Level level(String code, String minPercent) {
        Level level = new Level();
        level.setCode(code);
        level.setPlacementMinPercent(new BigDecimal(minPercent));
        return level;
    }
}
