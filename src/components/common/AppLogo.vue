<script setup>
defineProps({
  compact: { type: Boolean, default: false },
  light: { type: Boolean, default: false },
})
</script>

<template>
  <span class="logo" :class="{ 'logo--light': light }">
    <svg class="logo__mark" viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="currentColor" />
      <path d="M18 18h22v6H25v5h13v6H25v5h15v6H18z" fill="#fff" />
      <circle cx="46" cy="44" r="5" fill="#fbbf24" />
    </svg>
    <span v-if="!compact" class="logo__text">English<strong>Hub</strong></span>
  </span>
</template>

<style scoped>
.logo { display: inline-flex; align-items: center; gap: 10px; color: var(--eh-primary); }
.logo__mark { width: 34px; height: 34px; flex-shrink: 0; }
.logo__text { font-size: 19px; font-weight: 500; color: var(--eh-text); letter-spacing: -0.2px; }
.logo__text strong { font-weight: 700; color: var(--eh-primary); }
.logo--light .logo__text { color: #fff; }
.logo--light .logo__text strong { color: #c7d2fe; }
</style>
