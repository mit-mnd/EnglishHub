<script setup>
/**
 * Wrapper ECharts tối giản: tự resize theo khung chứa, cập nhật khi option thay đổi,
 * và dựng lại với bảng màu mới khi người dùng đổi giao diện sáng / tối.
 */
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useTheme } from '@/composables/useTheme'
import { cssVar } from '@/utils/cssVar'
import { use, init } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '300px' },
  ariaLabel: { type: String, default: 'Biểu đồ' },
})

const { isDark } = useTheme()
const el = ref()
const chart = shallowRef(null)
let observer = null

/** Màu nền trục, chữ, tooltip lấy từ token hiện hành. */
function themeFromTokens() {
  const text = cssVar('--eh-text-3')
  const line = cssVar('--eh-border')
  const axis = { axisLine: { lineStyle: { color: line } }, axisLabel: { color: text }, splitLine: { lineStyle: { color: line } }, axisTick: { show: false } }
  return {
    textStyle: { fontFamily: 'Be Vietnam Pro, sans-serif', color: text },
    categoryAxis: axis,
    valueAxis: { ...axis, axisLine: { show: false } },
    legend: { textStyle: { color: cssVar('--eh-text-2') } },
    tooltip: {
      backgroundColor: cssVar('--eh-surface'), borderColor: line, textStyle: { color: cssVar('--eh-text') },
      extraCssText: 'box-shadow: 0 8px 24px rgba(0,0,0,0.18); border-radius: 10px;',
    },
  }
}

function mount() {
  chart.value = init(el.value, themeFromTokens(), { renderer: 'canvas' })
  chart.value.setOption(props.option)
}

onMounted(() => {
  mount()
  observer = new ResizeObserver(() => chart.value?.resize())
  observer.observe(el.value)
})

watch(() => props.option, (option) => chart.value?.setOption(option, true), { deep: true })
// Đổi giao diện: ECharts không tự đổi theme đã khởi tạo nên dựng lại biểu đồ
watch(isDark, () => {
  chart.value?.dispose()
  mount()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart.value?.dispose()
})
</script>

<template>
  <div ref="el" :style="{ height, width: '100%' }" role="img" :aria-label="ariaLabel" />
</template>
