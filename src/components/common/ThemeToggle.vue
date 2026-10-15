<script setup>
import { computed } from 'vue'
import { Check, Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'

/** Nút đổi giao diện: bấm mở menu Sáng / Tối / Theo hệ thống. */
defineProps({ size: { type: String, default: 'default' } })

const { mode, isDark, setMode } = useTheme()

const options = [
  { value: 'light', label: 'Sáng', icon: Sunny },
  { value: 'dark', label: 'Tối', icon: Moon },
  { value: 'system', label: 'Theo hệ thống', icon: Monitor },
]
const label = computed(() => `Giao diện: ${options.find((o) => o.value === mode.value)?.label}`)
</script>

<template>
  <el-dropdown trigger="click" placement="bottom-end" @command="setMode">
    <button type="button" class="theme-btn" :class="`theme-btn--${size}`" :aria-label="label" :title="label">
      <el-icon :size="size === 'small' ? 16 : 19"><component :is="isDark ? Moon : Sunny" /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="o in options" :key="o.value" :command="o.value" :icon="o.icon"
                          :class="{ 'is-current': mode === o.value }">
          <span class="theme-opt">{{ o.label }}<el-icon v-if="mode === o.value" class="theme-opt__check"><Check /></el-icon></span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.theme-btn {
  display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid transparent; border-radius: 999px;
  background: transparent; color: var(--eh-text-2); cursor: pointer; transition: background-color 0.15s, color 0.15s, transform 0.2s;
}
.theme-btn:hover { background: var(--eh-surface-2); color: var(--eh-primary); }
.theme-btn:active { transform: rotate(18deg) scale(0.94); }
.theme-btn--small { width: 32px; height: 32px; }
.theme-opt { display: inline-flex; align-items: center; justify-content: space-between; gap: 18px; min-width: 120px; }
.theme-opt__check { color: var(--eh-primary); }
</style>
