<script setup>
import { computed, onMounted, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, CircleCheck, CircleClose, Medal, RefreshRight } from '@element-plus/icons-vue'
import ScoreRing from '@/components/exercise/ScoreRing.vue'
import MatchingGame from '@/components/game/MatchingGame.vue'
import ScrambleGame from '@/components/game/ScrambleGame.vue'
import DictationGame from '@/components/game/DictationGame.vue'
import { GAMES } from '@/components/game/games'
import { gameApi } from '@/api'
import { useDashboardStore } from '@/stores/dashboard'
import { formatDuration } from '@/utils/format'

const props = defineProps({ gameType: { type: String, required: true } })
const dashboard = useDashboardStore()

const COMPONENTS = { matching: MatchingGame, scramble: ScrambleGame, dictation: DictationGame }
const MIN_WORDS = 4

const game = computed(() => GAMES[props.gameType])
const phase = ref('loading')     // loading | ready | playing | finished | empty | error
const words = shallowRef([])
const round = ref(0)             // đổi key để tạo lại component khi chơi lại
const outcome = ref(null)        // kết quả từ trò chơi
const saved = ref(null)          // phản hồi server (kỷ lục)
const saving = ref(false)
let startedAt = 0

const fromNotebook = computed(() => words.value.filter((w) => w.fromNotebook).length)

async function load() {
  phase.value = 'loading'
  outcome.value = saved.value = null
  try {
    words.value = await gameApi.words(game.value.type, game.value.count)
    phase.value = words.value.length >= MIN_WORDS ? 'ready' : 'empty'
  } catch (e) {
    ElMessage.error(e.message)
    phase.value = 'error'
  }
}

function start() {
  round.value++
  startedAt = Date.now()
  phase.value = 'playing'
}

async function onFinish(result) {
  outcome.value = { ...result, durationSeconds: Math.round((Date.now() - startedAt) / 1000) }
  phase.value = 'finished'
  saving.value = true
  try {
    saved.value = await gameApi.submit({
      gameType: game.value.type,
      score: result.score,
      correctCount: result.correctCount,
      totalCount: result.totalCount,
      durationSeconds: outcome.value.durationSeconds,
    })
    dashboard.load()   // thời gian chơi được tính vào mục tiêu hôm nay
  } catch (e) {
    ElMessage.error(`Không lưu được kết quả: ${e.message}`)
  } finally {
    saving.value = false
  }
}

const verdictText = computed(() => {
  const s = outcome.value?.score ?? 0
  if (s >= 90) return 'Xuất sắc!'
  if (s >= 70) return 'Làm tốt lắm!'
  if (s >= 50) return 'Khá ổn, cố thêm chút nữa nhé!'
  return 'Đừng nản - chơi lại để nhớ từ lâu hơn!'
})

onMounted(load)
</script>

<template>
  <div class="eh-page game-page">
    <div class="game-page__top">
      <RouterLink :to="{ name: 'practice' }" class="game-page__back">
        <el-icon><ArrowLeft /></el-icon> Luyện tập
      </RouterLink>
      <span class="game-page__name" :style="{ color: game.color }">
        <el-icon><component :is="game.icon" /></el-icon> {{ game.title }}
      </span>
    </div>

    <el-card shadow="never" class="game-card">
      <div v-if="phase === 'loading'" v-loading="true" class="game-card__placeholder" />

      <div v-else-if="phase === 'ready'" class="intro">
        <span class="intro__icon" :style="{ color: game.color, background: game.bg }">
          <el-icon :size="36"><component :is="game.icon" /></el-icon>
        </span>
        <h2>{{ game.title }}</h2>
        <p class="intro__desc">{{ game.description }}</p>
        <p class="eh-subtle">
          {{ words.length }} từ ·
          <template v-if="fromNotebook">{{ fromNotebook }} từ trong sổ của bạn</template>
          <template v-else>từ gợi ý theo trình độ (sổ từ của bạn còn ít)</template>
        </p>
        <el-button type="primary" size="large" @click="start">Bắt đầu</el-button>
      </div>

      <component :is="COMPONENTS[gameType]" v-else-if="phase === 'playing'" :key="round" :words="words" @finish="onFinish" />

      <div v-else-if="phase === 'finished'" class="result">
        <ScoreRing :score="outcome.score" :size="130" label="điểm" />
        <h2>{{ verdictText }}</h2>
        <el-tag v-if="saved?.personalBest" type="success" effect="dark" round size="large">
          <el-icon><Medal /></el-icon> Kỷ lục mới{{ saved.previousBest != null ? ` (cũ: ${saved.previousBest})` : '' }}
        </el-tag>
        <p v-else-if="saved" class="eh-subtle">Kỷ lục của bạn: {{ saved.bestScore }} điểm</p>
        <p class="result__meta">
          Đúng {{ outcome.correctCount }}/{{ outcome.totalCount }} · {{ formatDuration(outcome.durationSeconds) }} · {{ outcome.note }}
        </p>

        <ul class="review">
          <li v-for="w in outcome.details" :key="w.wordId" class="review__item">
            <el-icon :class="w.ok ? 'review__ok' : 'review__bad'"><CircleCheck v-if="w.ok" /><CircleClose v-else /></el-icon>
            <strong>{{ w.headword }}</strong>
            <span class="eh-subtle review__meaning">{{ w.meaning }}</span>
            <span v-if="w.answer && !w.ok" class="review__answer">bạn gõ: {{ w.answer }}</span>
          </li>
        </ul>

        <div class="result__actions">
          <el-button type="primary" :icon="RefreshRight" :loading="saving" @click="load">Chơi lượt mới</el-button>
          <RouterLink :to="{ name: 'practice' }"><el-button>Chọn trò khác</el-button></RouterLink>
        </div>
      </div>

      <el-empty v-else-if="phase === 'empty'" description="Chưa đủ từ để chơi (cần ít nhất 4 từ).">
        <RouterLink :to="{ name: 'vocabulary' }"><el-button type="primary">Thêm từ vào sổ</el-button></RouterLink>
      </el-empty>

      <el-result v-else icon="error" title="Không tải được trò chơi">
        <template #extra><el-button @click="load">Thử lại</el-button></template>
      </el-result>
    </el-card>
  </div>
</template>

<style scoped>
.game-page { display: grid; gap: 16px; max-width: 860px; }
.game-page__top { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.game-page__back { display: inline-flex; align-items: center; gap: 4px; color: var(--eh-text-2); }
.game-page__name { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; }
.game-card :deep(.el-card__body) { padding: 24px; }
.game-card__placeholder { min-height: 260px; }

.intro, .result { display: grid; justify-items: center; gap: 12px; text-align: center; }
.intro__icon { display: grid; place-items: center; width: 76px; height: 76px; border-radius: 20px; }
.intro__desc { max-width: 460px; color: var(--eh-text-2); }
.result__meta { color: var(--eh-text-2); }
.result__actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }

.review { list-style: none; padding: 0; margin: 4px 0; width: 100%; max-width: 560px; display: grid; gap: 6px; text-align: left; }
.review__item {
  display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap;
  padding: 8px 12px; border-radius: 10px; background: var(--eh-bg);
}
.review__ok { color: #10b981; align-self: center; }
.review__bad { color: #ef4444; align-self: center; }
.review__meaning { flex: 1; min-width: 120px; }
.review__answer { font-size: 12px; color: #dc2626; }

@media (max-width: 480px) {
  .game-card :deep(.el-card__body) { padding: 16px 12px; }
}
</style>
