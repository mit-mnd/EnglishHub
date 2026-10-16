<script setup>
import { RouterLink } from 'vue-router'

/** home: logo bấm được, đưa người dùng về trang Tổng quan (dùng ở các màn toàn màn hình không có thanh điều hướng). */
defineProps({
  compact: { type: Boolean, default: false },
  light: { type: Boolean, default: false },
  home: { type: Boolean, default: false },
})
</script>

<template>
  <component :is="home ? RouterLink : 'span'" class="logo" :class="{ 'logo--light': light, 'logo--link': home }"
             v-bind="home ? { to: { name: 'dashboard' }, 'aria-label': 'EnglishHub - về trang chủ', title: 'Về trang chủ' } : {}">
    <svg class="logo__mark" viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="currentColor" />
      <path d="M18 18h22v6H25v5h13v6H25v5h15v6H18z" fill="#fff" />
      <circle cx="46" cy="44" r="5" fill="#fbbf24" />
    </svg>
    <span v-if="!compact" class="logo__text">English<strong>Hub</strong></span>
  </component>
</template>

<style scoped>
.logo { display: inline-flex; align-items: center; gap: 10px; color: var(--eh-primary); user-select: none; -webkit-user-select: none; }
.logo--link { text-decoration: none; border-radius: 10px; cursor: pointer; transition: opacity 0.15s, transform 0.2s var(--eh-ease); }
.logo--link:hover { text-decoration: none; opacity: 0.85; }
.logo--link:active { transform: scale(0.97); }
.logo__mark { width: 34px; height: 34px; flex-shrink: 0; }
.logo__text { font-size: 19px; font-weight: 500; color: var(--eh-text); letter-spacing: -0.2px; }
.logo__text strong { font-weight: 700; color: var(--eh-primary); }
.logo--light .logo__text { color: #fff; }
.logo--light .logo__text strong { color: #c7d2fe; }
</style>
