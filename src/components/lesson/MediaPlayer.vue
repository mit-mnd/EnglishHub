<script setup>
/**
 * Trình phát audio/video có điều khiển riêng: phát/dừng, tua ±5s, thanh tiến trình,
 * tốc độ 0.5x - 2x. Cha điều khiển qua expose: seek(ms), play(), pause(), playRange(start, end).
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CaretRight, RefreshLeft, RefreshRight, VideoPause, WarningFilled } from '@element-plus/icons-vue'
import { formatClock } from '@/utils/format'

const props = defineProps({
  src: { type: String, required: true },
  mediaType: { type: String, default: 'AUDIO' },
  startAtMs: { type: Number, default: 0 },
})
const emit = defineEmits(['time', 'playing', 'ended'])

const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2]

const media = ref()
const playing = ref(false)
const currentMs = ref(0)
const durationMs = ref(0)
const speed = ref(1)
const error = ref(false)
let rangeEndMs = null      // phát một đoạn (một câu) rồi tự dừng

const progress = computed(() => (durationMs.value ? (currentMs.value / durationMs.value) * 100 : 0))

function onLoaded() {
  durationMs.value = media.value.duration * 1000
  if (props.startAtMs > 0 && props.startAtMs < durationMs.value - 1000) {
    media.value.currentTime = props.startAtMs / 1000
  }
}

function onTime() {
  currentMs.value = media.value.currentTime * 1000
  if (rangeEndMs != null && currentMs.value >= rangeEndMs) {
    rangeEndMs = null
    media.value.pause()
  }
  emit('time', currentMs.value)
}

function toggle() {
  if (playing.value) media.value.pause()
  else play()
}

function play() {
  media.value.play().catch(() => { /* trình duyệt chặn autoplay: chờ người dùng bấm */ })
}

function pause() {
  media.value.pause()
}

function seek(ms) {
  rangeEndMs = null
  media.value.currentTime = Math.max(0, Math.min(ms, durationMs.value) / 1000)
  onTime()
}

/** Phát đúng một đoạn [start, end] - dùng cho "nghe lại câu này". */
function playRange(startMs, endMs) {
  seek(startMs)
  rangeEndMs = endMs
  play()
}

function skip(deltaMs) {
  seek(currentMs.value + deltaMs)
}

function onSlider(value) {
  seek((value / 100) * durationMs.value)
}

function setSpeed(value) {
  speed.value = value
  media.value.playbackRate = value
}

/** Phím tắt: Space = phát/dừng, ← → = tua 5 giây (bỏ qua khi đang gõ chữ). */
function onKeydown(e) {
  const tag = e.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'BUTTON' || e.ctrlKey || e.metaKey) return
  if (e.code === 'Space') { e.preventDefault(); toggle() }
  else if (e.key === 'ArrowLeft') skip(-5000)
  else if (e.key === 'ArrowRight') skip(5000)
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

defineExpose({ seek, play, pause, playRange, playing, currentMs, durationMs })
</script>

<template>
  <div class="player" :class="{ 'player--video': mediaType === 'VIDEO' }">
    <video v-if="mediaType === 'VIDEO'" ref="media" class="player__video" :src="src" preload="metadata" playsinline
           @loadedmetadata="onLoaded" @timeupdate="onTime" @error="error = true"
           @play="playing = true; emit('playing', true)" @pause="playing = false; emit('playing', false)"
           @ended="playing = false; emit('playing', false); emit('ended')" @click="toggle" />
    <audio v-else ref="media" :src="src" preload="metadata"
           @loadedmetadata="onLoaded" @timeupdate="onTime" @error="error = true"
           @play="playing = true; emit('playing', true)" @pause="playing = false; emit('playing', false)"
           @ended="playing = false; emit('playing', false); emit('ended')" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :icon="WarningFilled"
              title="Không tải được file âm thanh/video của bài học." />

    <div v-else class="controls">
      <el-button circle text :icon="RefreshLeft" aria-label="Lùi 5 giây" @click="skip(-5000)" />
      <button type="button" class="play" :aria-label="playing ? 'Tạm dừng' : 'Phát'" @click="toggle">
        <el-icon :size="26"><VideoPause v-if="playing" /><CaretRight v-else /></el-icon>
      </button>
      <el-button circle text :icon="RefreshRight" aria-label="Tua 5 giây" @click="skip(5000)" />

      <span class="time">{{ formatClock(currentMs) }}</span>
      <el-slider class="bar" :model-value="progress" :show-tooltip="false" :step="0.1" aria-label="Vị trí phát"
                 @update:model-value="onSlider" />
      <span class="time">{{ formatClock(durationMs) }}</span>

      <el-dropdown trigger="click" @command="setSpeed">
        <el-button size="small" round class="speed" aria-label="Tốc độ phát">{{ speed }}x</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="s in SPEEDS" :key="s" :command="s" :class="{ 'is-active': s === speed }">
              {{ s }}x{{ s === 1 ? ' (chuẩn)' : '' }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.player { display: grid; gap: 12px; }
.player__video { width: 100%; max-height: 420px; border-radius: 12px; background: #000; cursor: pointer; }
.controls {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 14px;
  background: linear-gradient(135deg, var(--eh-primary-soft), var(--eh-surface-2));
}
.controls .el-button + .el-button { margin-left: 0; }
.play {
  display: grid; place-items: center; flex-shrink: 0; width: 48px; height: 48px; border: 0; border-radius: 50%;
  background: var(--eh-primary); color: var(--eh-on-primary); cursor: pointer; box-shadow: 0 6px 16px rgba(79, 70, 229, 0.35);
}
.play:hover { background: var(--el-color-primary-dark-2); }
.time { font-size: 13px; font-variant-numeric: tabular-nums; color: var(--eh-text-2); min-width: 40px; text-align: center; }
.bar { flex: 1; min-width: 60px; }
.speed { font-weight: 700; min-width: 56px; }
:deep(.el-dropdown-menu__item.is-active) { color: var(--eh-primary); font-weight: 600; }
@media (max-width: 599.98px) {
  .controls { flex-wrap: wrap; justify-content: center; }
  .bar { order: 10; flex-basis: 100%; }
}
</style>
