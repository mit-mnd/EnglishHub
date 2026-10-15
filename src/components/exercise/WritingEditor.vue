<script setup>
/** Ô viết bài kèm bộ đếm từ theo yêu cầu đề (min-max). */
import { computed } from 'vue'
import { countWords } from '@/utils/feedback'

const props = defineProps({
  modelValue: { type: String, default: '' },
  minWords: { type: Number, default: null },
  maxWords: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
})
defineEmits(['update:modelValue'])

const words = computed(() => countWords(props.modelValue))
const status = computed(() => {
  if (props.minWords && words.value < props.minWords) return 'short'
  if (props.maxWords && words.value > props.maxWords) return 'long'
  return words.value ? 'ok' : 'empty'
})
const percent = computed(() => (props.minWords ? Math.min(100, Math.round((words.value / props.minWords) * 100)) : 0))
</script>

<template>
  <div class="editor">
    <el-input :model-value="modelValue" type="textarea" :autosize="{ minRows: 8, maxRows: 18 }" :disabled="disabled"
              maxlength="5000" placeholder="Viết bài của bạn bằng tiếng Anh tại đây…" spellcheck="false"
              aria-label="Bài viết" @update:model-value="(v) => $emit('update:modelValue', v)" />
    <div class="counter" :class="`counter--${status}`" aria-live="polite">
      <el-progress v-if="minWords" :percentage="percent" :show-text="false" :stroke-width="6" class="counter__bar"
                   :color="status === 'long' ? 'var(--eh-danger)' : status === 'ok' ? 'var(--eh-success)' : 'var(--eh-accent)'" />
      <span><strong>{{ words }}</strong> từ</span>
      <span v-if="minWords || maxWords" class="eh-subtle">
        · yêu cầu {{ minWords ?? 0 }}{{ maxWords ? `–${maxWords}` : '+' }} từ
      </span>
      <span v-if="status === 'long'" class="warn">· hơi dài</span>
    </div>
  </div>
</template>

<style scoped>
.editor { display: grid; gap: 8px; }
.editor :deep(textarea) { font-size: 16px; line-height: 1.7; font-family: var(--eh-font); }
.counter { display: flex; align-items: center; gap: 6px; font-size: 14px; flex-wrap: wrap; }
.counter__bar { width: 120px; }
.counter--ok strong { color: var(--eh-success); }
.counter--short strong { color: var(--eh-accent-text); }
.counter--long strong, .warn { color: var(--eh-danger); }
</style>
