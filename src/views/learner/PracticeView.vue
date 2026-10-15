<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Collection, Medal, Right, VideoPlay } from '@element-plus/icons-vue'
import { GAMES, gameSlugByType } from '@/components/game/games'
import { flashcardApi, gameApi } from '@/api'
import { formatRelative } from '@/utils/format'

const stats = ref([])
const summary = ref(null)
const loading = ref(true)

const cards = computed(() => Object.entries(GAMES).map(([slug, g]) => ({
  slug, ...g, stats: stats.value.find((s) => gameSlugByType[s.gameType] === slug),
})))
const toReview = computed(() => (summary.value ? summary.value.dueNow + summary.value.newAvailableToday : 0))

onMounted(async () => {
  try {
    ;[stats.value, summary.value] = await Promise.all([gameApi.stats(), flashcardApi.summary()])
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="eh-page practice">
    <section class="practice__intro">
      <h2>Trò chơi từ vựng</h2>
      <p class="eh-subtle">Ôn lại các từ trong sổ của bạn qua trò chơi ngắn 1-3 phút. Thời gian chơi được tính vào mục tiêu hằng ngày.</p>
    </section>

    <div v-loading="loading" class="practice__grid">
      <RouterLink v-for="g in cards" :key="g.slug" :to="{ name: 'game', params: { gameType: g.slug } }" class="game-tile">
        <span class="game-tile__icon" :style="{ color: g.color, background: g.bg }">
          <el-icon :size="28"><component :is="g.icon" /></el-icon>
        </span>
        <h3>{{ g.title }}</h3>
        <p class="game-tile__desc">{{ g.description }}</p>
        <div class="game-tile__stats">
          <template v-if="g.stats?.plays">
            <span><el-icon><Medal /></el-icon> Kỷ lục <strong>{{ g.stats.bestScore }}</strong></span>
            <span>{{ g.stats.plays }} lượt · {{ formatRelative(g.stats.lastPlayedAt) }}</span>
          </template>
          <span v-else class="eh-subtle">Chưa chơi lần nào</span>
        </div>
        <span class="game-tile__cta" :style="{ color: g.color }">
          <el-icon><VideoPlay /></el-icon> Chơi ngay
        </span>
      </RouterLink>
    </div>

    <el-card v-if="summary" shadow="never" class="practice__srs">
      <el-icon :size="28" class="practice__srs-icon"><Collection /></el-icon>
      <div class="practice__srs-text">
        <strong>{{ toReview ? `${toReview} thẻ đang chờ ôn` : 'Không có thẻ nào đến hạn' }}</strong>
        <p class="eh-subtle">Trò chơi giúp làm quen từ; ôn flashcard theo lịch lặp lại ngắt quãng giúp nhớ lâu.</p>
      </div>
      <RouterLink :to="{ name: 'flashcard-review' }">
        <el-button :type="toReview ? 'primary' : 'default'" :icon="Right">Ôn flashcard</el-button>
      </RouterLink>
    </el-card>
  </div>
</template>

<style scoped>
.practice { display: grid; gap: 20px; }
.practice__intro { display: grid; gap: 4px; }
.practice__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 16px; min-height: 120px; }

.game-tile {
  display: flex; flex-direction: column; gap: 10px; padding: 20px;
  border-radius: 16px; border: 1px solid var(--eh-border); background: var(--eh-surface);
  color: var(--eh-text); text-decoration: none; transition: transform 0.15s, box-shadow 0.15s;
}
.game-tile:hover { text-decoration: none; transform: translateY(-2px); box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08); }
.game-tile__icon { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 14px; }
.game-tile__desc { color: var(--eh-text-2); font-size: 14px; flex: 1; }
.game-tile__stats { display: grid; gap: 2px; font-size: 13px; color: var(--eh-text-2); }
.game-tile__stats .el-icon { vertical-align: -2px; color: #d97706; }
.game-tile__cta { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; }

.practice__srs :deep(.el-card__body) { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.practice__srs-icon { color: var(--eh-primary); }
.practice__srs-text { flex: 1; min-width: 200px; }
</style>
