<script setup>
import { Check } from '@element-plus/icons-vue'
import { isAnswered } from '@/utils/question'

defineProps({
  questions: { type: Array, required: true },
  answers: { type: Object, required: true },
  flagged: { type: Set, required: true },
  currentIndex: { type: Number, required: true },
  saveStatus: { type: String, default: 'idle' },
  submitting: { type: Boolean, default: false },
  answeredCount: { type: Number, required: true },
})
defineEmits(['go', 'submit'])
</script>

<template>
  <div>
    <div class="nav-grid">
      <button
        v-for="(q, i) in questions"
        :key="q.id"
        type="button"
        class="nav-cell"
        :class="{
          'nav-cell--answered': isAnswered(q, answers[q.id]),
          'nav-cell--current': i === currentIndex,
          'nav-cell--flagged': flagged.has(q.id),
        }"
        :aria-label="`Câu ${q.number}${isAnswered(q, answers[q.id]) ? ', đã trả lời' : ''}${flagged.has(q.id) ? ', đánh dấu xem lại' : ''}`"
        :aria-current="i === currentIndex ? 'step' : undefined"
        @click="$emit('go', i)"
      >{{ q.number }}</button>
    </div>

    <ul class="legend">
      <li><span class="nav-cell nav-cell--answered" /> Đã trả lời</li>
      <li><span class="nav-cell" /> Chưa trả lời</li>
      <li><span class="nav-cell nav-cell--flagged" /> Xem lại sau</li>
    </ul>

    <p class="eh-subtle overall-save">
      <template v-if="saveStatus === 'saving'">Đang lưu câu trả lời…</template>
      <template v-else-if="saveStatus === 'error'">Một số câu chưa lưu được — sẽ được gửi lại khi nộp bài.</template>
      <template v-else>Câu trả lời được lưu tự động.</template>
    </p>

    <el-button type="success" class="submit-btn" :icon="Check" :loading="submitting" @click="$emit('submit')">
      Nộp bài ({{ answeredCount }}/{{ questions.length }})
    </el-button>
  </div>
</template>

<style scoped>
.nav-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
.nav-cell {
  position: relative; display: grid; place-items: center; aspect-ratio: 1; min-width: 36px; min-height: 36px;
  border: 1.5px solid var(--eh-border); border-radius: 10px; background: var(--eh-surface);
  font: inherit; font-weight: 600; color: var(--eh-text-2); cursor: pointer;
}
.nav-cell--answered { background: var(--eh-primary); border-color: var(--eh-primary); color: var(--eh-on-primary); }
.nav-cell--current { box-shadow: 0 0 0 3px var(--eh-primary-ring); border-color: var(--eh-primary); }
.nav-cell--flagged::after {
  content: ''; position: absolute; top: -4px; right: -4px; width: 10px; height: 10px;
  border-radius: 50%; background: var(--eh-accent); border: 2px solid var(--eh-surface);
}
.legend { list-style: none; padding: 0; margin: 16px 0 0; display: grid; gap: 8px; font-size: 13px; color: var(--eh-text-2); }
.legend li { display: flex; align-items: center; gap: 8px; }
.legend .nav-cell { width: 18px; min-width: 18px; height: 18px; min-height: 18px; border-radius: 5px; cursor: default; }
.overall-save { margin: 16px 0 12px; }
.submit-btn { width: 100%; }

@media (max-width: 767.98px) {
  .nav-grid { grid-template-columns: repeat(6, 1fr); }
}
</style>
