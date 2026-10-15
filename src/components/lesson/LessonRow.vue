<script setup>
/** Một dòng bài học dùng chung (khóa học, lộ trình, kết quả tìm kiếm, mục lục trong bài). */
import { computed } from 'vue'
import { CircleCheckFilled, Clock } from '@element-plus/icons-vue'
import { skillMeta } from '@/utils/skills'

const props = defineProps({
  lesson: { type: Object, required: true },
  index: { type: Number, default: null },
  active: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  showCourse: { type: Boolean, default: false },
})

const meta = computed(() => skillMeta(props.lesson.skill))
const done = computed(() => props.lesson.progressStatus === 'COMPLETED')
const started = computed(() => props.lesson.progressStatus === 'IN_PROGRESS')
</script>

<template>
  <RouterLink :to="{ name: 'lesson', params: { lessonId: lesson.id } }" class="row"
              :class="{ 'row--active': active, 'row--compact': compact }" :aria-current="active ? 'page' : undefined">
    <span class="row__icon" :style="{ background: meta.soft, color: meta.color }">
      <el-icon><component :is="meta.icon" /></el-icon>
    </span>
    <span class="row__body">
      <span class="row__title">
        <span v-if="index != null" class="row__index">{{ index }}.</span> {{ lesson.title }}
      </span>
      <span v-if="!compact" class="row__meta">
        {{ meta.label }} · <el-icon><Clock /></el-icon> {{ lesson.estimatedMinutes }} phút
        <template v-if="showCourse"> · {{ lesson.courseTitle }}</template>
      </span>
      <span v-if="!compact && lesson.summary" class="row__summary">{{ lesson.summary }}</span>
    </span>
    <span class="row__status">
      <slot name="status">
        <el-icon v-if="done" class="done" :size="20" aria-label="Đã hoàn thành"><CircleCheckFilled /></el-icon>
        <el-progress v-else-if="started" type="circle" :percentage="Number(lesson.progressPercent ?? 0)" :width="26"
                     :stroke-width="3" :show-text="false" aria-label="Đang học" />
      </slot>
    </span>
  </RouterLink>
</template>

<style scoped>
.row {
  display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 12px;
  color: var(--eh-text); text-decoration: none; border: 1px solid transparent; transition: background-color 0.15s;
}
.row:hover { background: var(--eh-bg); text-decoration: none; }
.row--active { background: var(--eh-primary-soft); border-color: var(--el-color-primary-light-7); }
.row__icon { display: grid; place-items: center; flex-shrink: 0; width: 40px; height: 40px; border-radius: 10px; font-size: 18px; }
.row--compact .row__icon { width: 32px; height: 32px; font-size: 15px; border-radius: 8px; }
.row__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.row__title { font-weight: 600; line-height: 1.35; }
.row--compact .row__title { font-weight: 500; font-size: 14px; }
.row__index { color: var(--eh-text-3); font-weight: 500; }
.row__meta { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: var(--eh-text-2); flex-wrap: wrap; }
.row__summary { font-size: 13px; color: var(--eh-text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row__status { flex-shrink: 0; display: flex; align-items: center; }
.done { color: var(--eh-success); }
</style>
