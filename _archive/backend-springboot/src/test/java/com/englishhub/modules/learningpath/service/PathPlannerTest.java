package com.englishhub.modules.learningpath.service;

import com.englishhub.common.domain.Skill;
import com.englishhub.modules.learningpath.entity.LearningPathItem;
import com.englishhub.modules.learningpath.entity.LearningPathItem.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.englishhub.common.domain.Skill.GRAMMAR;
import static com.englishhub.common.domain.Skill.LISTENING;
import static com.englishhub.common.domain.Skill.READING;
import static com.englishhub.common.domain.Skill.WRITING;
import static org.assertj.core.api.Assertions.assertThat;

class PathPlannerTest {

    record L(String name, Skill skill, int minutes) {
    }

    private static final Function<L, Skill> SKILL = L::skill;

    @Test
    void weakSkillsMoveUpOnlyWithinEachGroupOfFour() {
        List<L> ordered = List.of(
                new L("a1", LISTENING, 10), new L("a2", GRAMMAR, 10), new L("a3", READING, 10), new L("a4", WRITING, 10),
                new L("b1", LISTENING, 10), new L("b2", READING, 10));

        List<L> result = PathPlanner.prioritizeWeakSkills(ordered, SKILL, Set.of(READING));

        // Nhóm 1: a3 (READING) lên đầu, phần còn lại giữ thứ tự; nhóm 2 tương tự - không trộn giữa các nhóm
        assertThat(result).extracting(L::name).containsExactly("a3", "a1", "a2", "a4", "b2", "b1");
    }

    @Test
    void noWeakSkillsKeepsOriginalOrder() {
        List<L> ordered = List.of(new L("x", LISTENING, 5), new L("y", READING, 5));
        assertThat(PathPlanner.prioritizeWeakSkills(ordered, SKILL, Set.of())).isEqualTo(ordered);
    }

    @Test
    void planDatesRespectsDailyBudget() {
        LocalDate today = LocalDate.of(2026, 10, 14);
        List<Integer> minutes = List.of(10, 10, 15, 30, 5);

        List<LocalDate> dates = PathPlanner.planDates(minutes, Integer::intValue, 20, today);

        // ngày 1: 10+10 = 20 | ngày 2: 15 | ngày 3: 30 (dài hơn quỹ ngày nhưng vẫn xếp trọn) | ngày 4: 5
        assertThat(dates).containsExactly(today, today, today.plusDays(1), today.plusDays(2), today.plusDays(3));
    }

    @Test
    void refreshStatusesMarksFirstPendingAsAvailableAndKeepsInProgress() {
        List<LearningPathItem> items = List.of(
                item(Status.COMPLETED), item(Status.SKIPPED), item(Status.LOCKED),
                item(Status.LOCKED), item(Status.IN_PROGRESS), item(Status.AVAILABLE));

        PathPlanner.refreshStatuses(items);

        assertThat(items).extracting(LearningPathItem::getStatus).containsExactly(
                Status.COMPLETED, Status.SKIPPED, Status.AVAILABLE, Status.LOCKED, Status.IN_PROGRESS, Status.LOCKED);
    }

    private static LearningPathItem item(Status status) {
        LearningPathItem item = new LearningPathItem();
        item.setStatus(status);
        return item;
    }
}
