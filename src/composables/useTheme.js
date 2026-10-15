/**
 * Giao diện Sáng / Tối / Theo hệ thống.
 * - Lựa chọn lưu ở localStorage (khóa riêng, không nằm trong file sao lưu học liệu vì là tùy chọn của thiết bị).
 * - Chế độ tối = class `dark` trên <html> (Element Plus và token --eh-* cùng dựa vào class này).
 * - index.html có script nhỏ áp class trước khi vẽ để không bị nháy sáng khi tải trang.
 */
import { computed, ref } from 'vue'

export const THEME_KEY = 'eh:theme'
export const THEME_MODES = ['system', 'light', 'dark']
const META_COLORS = { light: '#4f46e5', dark: '#0d0f1f' }

const mode = ref('system')
const systemDark = ref(false)
const isDark = computed(() => mode.value === 'dark' || (mode.value === 'system' && systemDark.value))

let started = false
let animTimer = 0

function storedMode() {
  try {
    const value = localStorage.getItem(THEME_KEY)
    return THEME_MODES.includes(value) ? value : 'system'
  } catch {
    return 'system'
  }
}

function apply({ animate = false } = {}) {
  const root = document.documentElement
  if (animate) {
    root.classList.add('theme-anim')
    clearTimeout(animTimer)
    animTimer = setTimeout(() => root.classList.remove('theme-anim'), 400)
  }
  root.classList.toggle('dark', isDark.value)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', META_COLORS[isDark.value ? 'dark' : 'light'])
}

/** Gọi một lần khi khởi động ứng dụng (main.js). */
export function initTheme() {
  if (started) return
  started = true
  mode.value = storedMode()
  const query = window.matchMedia?.('(prefers-color-scheme: dark)')
  systemDark.value = !!query?.matches
  query?.addEventListener?.('change', (e) => {
    systemDark.value = e.matches
    apply({ animate: true })
  })
  apply()
}

export function useTheme() {
  function setMode(next) {
    if (!THEME_MODES.includes(next)) return
    mode.value = next
    try {
      localStorage.setItem(THEME_KEY, next)
    } catch { /* chế độ riêng tư: vẫn đổi được trong phiên này */ }
    apply({ animate: true })
  }
  /** Đảo nhanh sáng <-> tối (theo giao diện đang hiển thị). */
  const toggle = () => setMode(isDark.value ? 'light' : 'dark')
  return { mode, isDark, setMode, toggle }
}

/** Chỉ dùng cho test. */
export function resetThemeForTests() {
  started = false
  mode.value = 'system'
  systemDark.value = false
}
