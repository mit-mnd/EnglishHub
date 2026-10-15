<script setup>
/**
 * Bài nghe: trình phát + phụ đề tương tác.
 * - Bấm câu: nghe lại đúng câu đó (chế độ "từng câu") hoặc tua tới câu đó và phát tiếp.
 * - Luyện nghe: làm mờ phụ đề, câu nào nghe xong mới hiện chữ.
 * Theo dõi % đã nghe (vị trí xa nhất) để tính tiến độ.
 */
import { computed, ref } from 'vue'
import InteractiveTranscript from './InteractiveTranscript.vue'
import MediaPlayer from './MediaPlayer.vue'

const props = defineProps({
  lesson: { type: Object, required: true },
  startAtMs: { type: Number, default: 0 },
})
const emit = defineEmits(['finished'])

const player = ref()
const currentMs = ref(props.startAtMs)
const furthestMs = ref(props.startAtMs)
const playing = ref(false)
const showTranslation = ref(false)
const practiceMode = ref(false)
const sentenceMode = ref(false)

const durationMs = computed(() => player.value?.durationMs || (props.lesson.durationSeconds ?? 0) * 1000)
const listenedPercent = computed(() => (durationMs.value ? Math.min(100, (furthestMs.value / durationMs.value) * 100) : 0))

function onTime(ms) {
  currentMs.value = ms
  furthestMs.value = Math.max(furthestMs.value, ms)
}

function onSelect(segment) {
  if (sentenceMode.value) player.value.playRange(segment.startMs, segment.endMs)
  else {
    player.value.seek(segment.startMs)
    player.value.play()
  }
}

function onEnded() {
  furthestMs.value = durationMs.value
  emit('finished')
}

defineExpose({
  snapshot: () => ({ positionMs: currentMs.value, progressPercent: listenedPercent.value, mediaPlaying: playing.value }),
})
</script>

<template>
  <div class="listening">
    <MediaPlayer ref="player" :src="lesson.mediaUrl" :media-type="lesson.mediaType" :start-at-ms="startAtMs"
                 @time="onTime" @playing="(v) => (playing = v)" @ended="onEnded" />

    <div v-if="lesson.transcript.length" class="eh-card transcript-card">
      <div class="transcript-head">
        <strong>Phụ đề</strong>
        <div class="toggles">
          <el-switch v-model="showTranslation" active-text="Dịch nghĩa" size="small" />
          <el-switch v-model="sentenceMode" active-text="Nghe từng câu" size="small" />
          <el-switch v-model="practiceMode" active-text="Luyện nghe (ẩn chữ)" size="small" />
        </div>
      </div>
      <p class="eh-subtle hint">
        Bấm vào một câu để {{ sentenceMode ? 'nghe lại riêng câu đó' : 'tua tới câu đó' }}.
        <span class="kb-hint">Phím tắt: Space phát/dừng, ← → tua 5 giây.</span>
      </p>
      <InteractiveTranscript :segments="lesson.transcript" :current-ms="currentMs" :show-translation="showTranslation"
                             :blur-text="practiceMode" @select="onSelect" />
    </div>
  </div>
</template>

<style scoped>
.listening { display: grid; gap: 16px; }
.transcript-card { padding: 16px 12px 8px; }
.transcript-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 0 8px; }
.toggles { display: flex; gap: 16px; flex-wrap: wrap; }
.hint { padding: 4px 8px 8px; }
@media (hover: none), (max-width: 767.98px) {
  .kb-hint { display: none; }
}
</style>
