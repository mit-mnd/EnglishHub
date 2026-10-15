package com.englishhub.modules.learningpath.service;

import com.englishhub.common.domain.Skill;
import com.englishhub.modules.learningpath.entity.LearningPathItem;
import com.englishhub.modules.learningpath.entity.LearningPathItem.Status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Các thuật toán thuần (không truy cập DB) của lộ trình học, tách riêng để unit test.
 */
public final class PathPlanner {

    /** Số bài trong một "nhóm" (~1 tuần học nhẹ); ưu tiên kỹ năng yếu được áp dụng trong từng nhóm. */
    static final int GROUP_SIZE = 4;

    private PathPlanner() {
    }

    /**
     * Đẩy bài của kỹ năng yếu lên đầu TRONG MỖI NHÓM {@value #GROUP_SIZE} bài, giữ nguyên thứ tự giữa các nhóm.
     * Nhờ vậy lộ trình vẫn đi từ dễ đến khó theo khóa học, nhưng kỹ năng yếu được luyện sớm hơn trong từng chặng.
     */
    public static <T> List<T> prioritizeWeakSkills(List<T> ordered, Function<T, Skill> skillOf, Set<Skill> weakSkills) {
        if (weakSkills == null || weakSkills.isEmpty()) {
            return new ArrayList<>(ordered);
        }
        Comparator<T> weakFirst = Comparator.comparingInt(item -> weakSkills.contains(skillOf.apply(item)) ? 0 : 1);
        List<T> result = new ArrayList<>(ordered.size());
        for (int start = 0; start < ordered.size(); start += GROUP_SIZE) {
            List<T> group = new ArrayList<>(ordered.subList(start, Math.min(start + GROUP_SIZE, ordered.size())));
            group.sort(weakFirst);   // List.sort là stable -> giữ thứ tự gốc trong cùng mức ưu tiên
            result.addAll(group);
        }
        return result;
    }

    /**
     * Lên lịch ngày học cho các bài chưa xong theo quỹ thời gian mỗi ngày.
     * Một bài dài hơn quỹ ngày vẫn được xếp trọn vào 1 ngày riêng.
     */
    public static <T> List<LocalDate> planDates(List<T> pending, ToIntFunction<T> minutesOf, int dailyMinutes,
                                                LocalDate startDate) {
        int budget = Math.max(1, dailyMinutes);
        List<LocalDate> dates = new ArrayList<>(pending.size());
        LocalDate day = startDate;
        int used = 0;
        for (T item : pending) {
            int minutes = Math.max(1, minutesOf.applyAsInt(item));
            if (used > 0 && used + minutes > budget) {
                day = day.plusDays(1);
                used = 0;
            }
            dates.add(day);
            used += minutes;
        }
        return dates;
    }

    /**
     * Tính lại trạng thái các bài chưa xong: bài chưa xong đầu tiên là AVAILABLE ("học tiếp"),
     * các bài sau là LOCKED ("sắp tới"); bài đang học dở giữ IN_PROGRESS dù nằm ở đâu.
     *
     * @param items danh sách đã sắp theo SortOrder
     */
    public static void refreshStatuses(List<LearningPathItem> items) {
        boolean currentAssigned = false;
        for (LearningPathItem item : items) {
            if (item.isDone()) {
                continue;
            }
            if (item.getStatus() == Status.IN_PROGRESS) {
                currentAssigned = true;
                continue;
            }
            item.setStatus(currentAssigned ? Status.LOCKED : Status.AVAILABLE);
            currentAssigned = true;
        }
    }
}
