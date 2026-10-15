<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Headset, VideoPause } from '@element-plus/icons-vue'
import { useSpeech } from '@/composables/useSpeech'
import { speechQueue } from '@/utils/toeic'

/**
 * Phát phần Nghe TOEIC bằng giọng đọc của trình duyệt (giọng nam / nữ theo người nói).
 * showScript: hiện kịch bản (sau khi đã trả lời) và tô sáng câu đang đọc.
 */
const props = defineProps({
  group: { type: Object, required: true },
  showScript: { type: Boolean, default: false },
})

const { supported, speakQueue, stop } = useSpeech()
const playing = ref(false)
const current = ref(-1)
const rate = ref(0.95)
const plays = ref(0)
const queue = computed(() => speechQueue(props.group))

async function play() {
  if (playing.value) {
    stop()
    playing.value = false
    return
  }
  playing.value = true
  plays.value++
  await speakQueue(queue.value, { rate: rate.value, onIndex: (i) => { current.value = i } })
  playing.value = false
}

watch(() => props.group.id, () => {
  stop()
  playing.value = false
  plays.value = 0
})
onBeforeUnmount(stop)
</script>

<template>
  <div class="lp">
    <div class="lp__bar">
      <el-button type="primary" round :icon="playing ? VideoPause : Headset" :disabled="!supported" @click="play">
        {{ playing ? 'Dừng' : plays ? 'Nghe lại' : 'Nghe' }}
      </el-button>
      <el-select v-model="rate" size="small" class="lp__rate" aria-label="Tốc độ đọc">
        <el-option :value="0.8" label="Chậm" />
        <el-option :value="0.95" label="Bình thường" />
        <el-option :value="1.1" label="Nhanh" />
      </el-select>
      <span v-if="!supported" class="eh-subtle">Trình duyệt không hỗ trợ đọc văn bản - hãy dùng Chrome hoặc Edge.</span>
      <span v-else-if="!showScript" class="eh-subtle">Kịch bản sẽ hiện sau khi bạn trả lời.</span>
    </div>

    <ol v-if="showScript" class="lp__script">
      <li v-for="(line, i) in queue" :key="i" :class="{ 'lp__line--now': current === i }">
        <strong v-if="line.speaker">{{ line.speaker }}:</strong>
        <strong v-if="line.lead">({{ line.lead }})</strong> {{ line.text }}
      </li>
    </ol>
  </div>
</template>

<style scoped>
.lp { display: grid; gap: 12px; }
.lp__bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.lp__rate { width: 130px; }
.lp__script {
  list-style: none; margin: 0; padding: 12px 14px; display: grid; gap: 6px;
  border-radius: 10px; background: var(--eh-bg); line-height: 1.6;
}
.lp__script li { padding: 2px 6px; border-radius: 6px; transition: background-color 0.2s; }
.lp__line--now { background: #fef3c7; }
</style>
