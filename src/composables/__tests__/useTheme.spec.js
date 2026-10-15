import { beforeEach, describe, expect, it, vi } from 'vitest'
import { initTheme, resetThemeForTests, THEME_KEY, useTheme } from '../useTheme'

/** matchMedia giả: đổi `dark` rồi gọi listener để mô phỏng người dùng đổi cài đặt hệ thống. */
function stubSystemTheme(initialDark) {
  const state = { dark: initialDark, listeners: [] }
  window.matchMedia = vi.fn(() => ({
    get matches() { return state.dark },
    addEventListener: (_, fn) => state.listeners.push(fn),
  }))
  state.set = (dark) => { state.dark = dark; state.listeners.forEach((fn) => fn({ matches: dark })) }
  return state
}

const isDarkClass = () => document.documentElement.classList.contains('dark')

beforeEach(() => {
  localStorage.clear()
  document.documentElement.className = ''
  document.head.innerHTML = '<meta name="theme-color" content="#4f46e5">'
  resetThemeForTests()
  vi.useRealTimers()
})

describe('useTheme', () => {
  it('mặc định theo hệ thống: máy đang dùng giao diện tối thì app tối', () => {
    stubSystemTheme(true)
    initTheme()
    expect(isDarkClass()).toBe(true)
    expect(useTheme().mode.value).toBe('system')
  })

  it('theo hệ thống: tự đổi khi cài đặt của máy thay đổi', () => {
    const system = stubSystemTheme(false)
    initTheme()
    expect(isDarkClass()).toBe(false)
    system.set(true)
    expect(isDarkClass()).toBe(true)
    system.set(false)
    expect(isDarkClass()).toBe(false)
  })

  it('chọn thủ công thì lưu lại, ghi đè cài đặt hệ thống và đổi màu thanh trình duyệt', () => {
    const system = stubSystemTheme(true)
    initTheme()
    const { setMode, isDark } = useTheme()

    setMode('light')
    expect(localStorage.getItem(THEME_KEY)).toBe('light')
    expect(isDarkClass()).toBe(false)
    expect(isDark.value).toBe(false)
    expect(document.querySelector('meta[name="theme-color"]').getAttribute('content')).toBe('#4f46e5')

    system.set(true)                       // hệ thống đổi nhưng người dùng đã chọn Sáng
    expect(isDarkClass()).toBe(false)

    setMode('dark')
    expect(isDarkClass()).toBe(true)
    expect(document.querySelector('meta[name="theme-color"]').getAttribute('content')).toBe('#0d0f1f')
  })

  it('khôi phục lựa chọn đã lưu khi mở lại; giá trị lạ trong localStorage bị bỏ qua', () => {
    stubSystemTheme(false)
    localStorage.setItem(THEME_KEY, 'dark')
    initTheme()
    expect(isDarkClass()).toBe(true)

    resetThemeForTests()
    document.documentElement.className = ''
    localStorage.setItem(THEME_KEY, 'neon')
    initTheme()
    expect(useTheme().mode.value).toBe('system')
    expect(isDarkClass()).toBe(false)
  })

  it('toggle đảo theo giao diện đang hiển thị và bỏ qua chế độ không hợp lệ', () => {
    stubSystemTheme(true)                  // đang "theo hệ thống" nhưng hiển thị tối
    initTheme()
    const { toggle, setMode, mode } = useTheme()
    toggle()
    expect(mode.value).toBe('light')
    toggle()
    expect(mode.value).toBe('dark')
    setMode('rainbow')
    expect(mode.value).toBe('dark')
  })

  it('bật class chuyển màu mượt rồi gỡ ra sau khi đổi', () => {
    vi.useFakeTimers()
    stubSystemTheme(false)
    initTheme()
    useTheme().setMode('dark')
    expect(document.documentElement.classList.contains('theme-anim')).toBe(true)
    vi.advanceTimersByTime(500)
    expect(document.documentElement.classList.contains('theme-anim')).toBe(false)
  })
})
