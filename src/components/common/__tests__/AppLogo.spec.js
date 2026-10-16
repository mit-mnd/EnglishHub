import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppLogo from '../AppLogo.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<i />' } }, { path: '/app/dashboard', name: 'dashboard', component: { template: '<i />' } }],
})

const mountLogo = (props) => mount(AppLogo, { props, global: { plugins: [router] } })

describe('AppLogo', () => {
  it('mặc định chỉ là hình + chữ (không bấm được)', () => {
    const w = mountLogo({})
    expect(w.element.tagName).toBe('SPAN')
    expect(w.find('a').exists()).toBe(false)
  })

  it('home: là liên kết về Tổng quan, có nhãn cho trình đọc màn hình', async () => {
    const w = mountLogo({ home: true })
    expect(w.element.tagName).toBe('A')
    expect(w.attributes('href')).toBe('/app/dashboard')
    expect(w.attributes('aria-label')).toContain('trang chủ')
    expect(w.text()).toContain('Hub')
  })

  it('compact vẫn là liên kết nhưng ẩn chữ', () => {
    const w = mountLogo({ home: true, compact: true })
    expect(w.attributes('href')).toBe('/app/dashboard')
    expect(w.text()).toBe('')
  })
})
