<script setup>
/** Wrapper ECharts tối giản: tự resize theo khung chứa, cập nhật khi option thay đổi. */
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
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

const el = ref()
const chart = shallowRef(null)
let observer = null

onMounted(() => {
  chart.value = init(el.value, null, { renderer: 'canvas' })
  chart.value.setOption(props.option)
  observer = new ResizeObserver(() => chart.value?.resize())
  observer.observe(el.value)
})

watch(() => props.option, (option) => chart.value?.setOption(option, true), { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  chart.value?.dispose()
})
</script>

<template>
  <div ref="el" :style="{ height, width: '100%' }" role="img" :aria-label="ariaLabel" />
</template>
