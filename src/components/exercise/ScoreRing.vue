<script setup>
import { scoreColor } from '@/utils/feedback'

defineProps({
  score: { type: Number, default: null },
  size: { type: Number, default: 96 },
  label: { type: String, default: '' },
})
</script>

<template>
  <el-progress type="circle" :percentage="Math.round(score ?? 0)" :width="size" :stroke-width="Math.max(6, size / 11)"
               :color="scoreColor(score)">
    <template #default>
      <span class="ring__value" :style="{ fontSize: `${size / 4}px` }">{{ score == null ? '—' : Math.round(score) }}</span>
      <span v-if="label" class="ring__label">{{ label }}</span>
    </template>
  </el-progress>
</template>

<style scoped>
.ring__value { display: block; font-weight: 700; color: var(--eh-text); line-height: 1.1; }
.ring__label { display: block; font-size: 11px; color: var(--eh-text-3); margin-top: 2px; }
</style>
