<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  unit: { type: String, default: '' },
  hint: { type: String, default: '' },
  tone: { type: String, default: 'primary' },   // primary | accent | success | info
})
</script>

<template>
  <div class="stat eh-card" :class="`stat--${tone}`">
    <div class="stat__icon"><slot name="icon" /></div>
    <div class="stat__body">
      <span class="stat__label">{{ label }}</span>
      <span class="stat__value">{{ value }}<small v-if="unit">{{ unit }}</small></span>
      <span v-if="hint || $slots.hint" class="stat__hint"><slot name="hint">{{ hint }}</slot></span>
    </div>
  </div>
</template>

<style scoped>
.stat { display: flex; align-items: flex-start; gap: 14px; }
.stat__icon {
  display: grid; place-items: center; flex-shrink: 0; width: 44px; height: 44px; border-radius: 12px; font-size: 22px;
}
.stat--primary .stat__icon { background: var(--eh-primary-soft); color: var(--eh-primary); }
.stat--accent .stat__icon { background: var(--eh-accent-soft); color: #d97706; }
.stat--success .stat__icon { background: var(--eh-success-soft); color: var(--eh-success); }
.stat--info .stat__icon { background: var(--eh-info-soft); color: #0284c7; }
.stat__body { display: flex; flex-direction: column; min-width: 0; }
.stat__label { font-size: 13px; color: var(--eh-text-2); }
.stat__value { font-size: 26px; font-weight: 700; line-height: 1.25; margin-top: 2px; }
.stat__value small { margin-left: 4px; font-size: 14px; font-weight: 500; color: var(--eh-text-2); }
.stat__hint { font-size: 12px; color: var(--eh-text-3); margin-top: 2px; }
</style>
