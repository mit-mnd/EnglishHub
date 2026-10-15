<script setup>
/**
 * Phụ đề tương tác: câu đang phát được tô sáng và tự cuộn vào giữa;
 * bấm vào câu để nghe lại đúng câu đó.
 */
import { computed, nextTick, ref, watch } from 'vue'
import { Headset } from '@element-plus/icons-vue'
import { formatClock } from '@/utils/format'

const props = defineProps({
  segments: { type: Array, required: true },
  currentMs: { type: Number, default: 0 },
  showTranslation: { type: Boolean, default: false },
  blurText: { type: Boolean, default: false },     // chế độ luyện nghe: làm mờ chữ
})
const emit = defineEmits(['select'])

const container = ref()
const activeIndex = computed(() => props.segments.findIndex(
  (s) => props.currentMs >= s.startMs && props.currentMs < s.endMs + 300))

watch(activeIndex, async (index) => {
  if (index < 0) return
  await nextTick()
  const el = container.value?.querySelector(`[data-index="${index}"]`)
  const box = container.value
  if (el && box) {
    // Chỉ cuộn bên trong khung phụ đề, không làm nhảy cả trang
    box.scrollTo({ top: el.offsetTop - box.clientHeight / 2 + el.clientHeight / 2, behavior: 'smooth' })
  }
})
</script>

<template>
  <ol ref="container" class="transcript" :class="{ 'transcript--blur': blurText }" aria-label="Phụ đề">
    <li v-for="(s, i) in segments" :key="s.id" :data-index="i">
      <button type="button" class="seg" :class="{ 'seg--active': i === activeIndex, 'seg--past': activeIndex > i }"
              :aria-current="i === activeIndex ? 'true' : undefined" @click="emit('select', s)">
        <span class="seg__time">{{ formatClock(s.startMs) }}</span>
        <span class="seg__body">
          <span class="seg__text">{{ s.text }}</span>
          <span v-if="showTranslation && s.translation" class="seg__vi">{{ s.translation }}</span>
        </span>
        <el-icon class="seg__icon"><Headset /></el-icon>
      </button>
    </li>
  </ol>
</template>

<style scoped>
.transcript {
  position: relative; list-style: none; margin: 0; padding: 4px; display: grid; gap: 4px;
  max-height: 420px; overflow-y: auto; scroll-behavior: smooth;
}
.seg {
  display: flex; align-items: flex-start; gap: 12px; width: 100%; padding: 10px 12px; text-align: left;
  border: 1px solid transparent; border-radius: 12px; background: transparent; cursor: pointer;
  font: inherit; color: var(--eh-text); transition: background-color 0.15s, border-color 0.15s;
}
.seg:hover { background: var(--eh-bg); }
.seg--past .seg__text { color: var(--eh-text-2); }
.seg--active { background: var(--eh-primary-soft); border-color: var(--el-color-primary-light-7); }
.seg--active .seg__text { color: var(--eh-primary); font-weight: 600; }
.seg__time { flex-shrink: 0; margin-top: 3px; font-size: 12px; color: var(--eh-text-3); font-variant-numeric: tabular-nums; }
.seg__body { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.seg__text { font-size: 16px; line-height: 1.55; }
.seg__vi { font-size: 14px; color: var(--eh-text-2); font-style: italic; }
.seg__icon { flex-shrink: 0; margin-top: 4px; color: var(--eh-text-3); opacity: 0; }
.seg:hover .seg__icon, .seg--active .seg__icon { opacity: 1; }
.transcript--blur .seg__text { filter: blur(5px); user-select: none; }
.transcript--blur .seg--past .seg__text { filter: none; }
</style>
