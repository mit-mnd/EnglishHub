<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, Notebook, Plus, Right, Search, VideoPlay } from '@element-plus/icons-vue'
import { flashcardApi, vocabularyApi } from '@/api'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const summary = ref(null)
const sets = ref([])
const loading = ref(true)
const adding = ref(null)

// Lọc danh sách bộ từ (có gần 100 bộ): theo nhóm + tìm theo tên / mô tả / từ mẫu
const GROUPS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'basic', label: 'Cơ bản' },
  { key: 'intermediate', label: 'Trung cấp' },
  { key: 'toeic', label: 'TOEIC' },
  { key: 'phrases', label: 'Cụm từ' },
  { key: 'advanced', label: 'Nâng cao' },
]
function groupOf(set) {
  if (/^TOEIC/i.test(set.name)) return 'toeic'
  if (set.id >= 400 && set.id < 500) return 'phrases'
  if (set.id >= 500) return 'advanced'
  return (set.levelId ?? 1) >= 3 ? 'intermediate' : 'basic'
}
const group = ref('all')
const keyword = ref('')
const groupCounts = computed(() => {
  const counts = { all: sets.value.length }
  for (const set of sets.value) counts[groupOf(set)] = (counts[groupOf(set)] ?? 0) + 1
  return counts
})
const visibleSets = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return sets.value.filter((set) => (group.value === 'all' || groupOf(set) === group.value)
    && (!q || `${set.name} ${set.description ?? ''} ${set.previewWords.join(' ')}`.toLowerCase().includes(q)))
})

const toReview = computed(() => (summary.value ? summary.value.dueNow + summary.value.newAvailableToday : 0))
const breakdown = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    { key: 'new', label: 'Từ mới', value: s.newCards, color: '#94a3b8' },
    { key: 'learning', label: 'Đang học', value: s.learning, color: '#f59e0b' },
    { key: 'review', label: 'Đang ôn', value: s.review, color: '#0ea5e9' },
    { key: 'mastered', label: 'Đã thuộc', value: s.mastered, color: '#10b981' },
  ]
})
const activeTotal = computed(() => breakdown.value.reduce((sum, b) => sum + b.value, 0))
const forecastMax = computed(() => Math.max(1, ...(summary.value?.forecast ?? []).map((f) => f.count)))

const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']
function dayLabel(iso, index) {
  if (index === 0) return 'Hôm nay'
  const [y, m, d] = iso.split('-').map(Number)
  return WEEKDAYS[new Date(y, m - 1, d).getDay()]
}

async function load() {
  loading.value = true
  try {
    ;[summary.value, sets.value] = await Promise.all([flashcardApi.summary(), vocabularyApi.sets()])
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function addSet(set) {
  adding.value = set.id
  try {
    const res = await vocabularyApi.addSet(set.id)
    ElMessage.success(res.added ? `Đã thêm ${res.added} từ vào sổ` : 'Bạn đã có đủ các từ của bộ này')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    adding.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="eh-page">
    <el-skeleton v-if="loading && !summary" :rows="8" animated />

    <template v-else-if="summary">
      <section class="hero">
        <div class="hero__text">
          <span class="hero__eyebrow">Ôn tập hôm nay</span>
          <h2 v-if="toReview">{{ toReview }} thẻ đang chờ bạn</h2>
          <h2 v-else-if="summary.total">Bạn đã ôn xong hôm nay! 🎉</h2>
          <h2 v-else>Bắt đầu xây dựng vốn từ của bạn</h2>
          <p>
            <template v-if="toReview">{{ summary.dueNow }} thẻ cần ôn · {{ summary.newAvailableToday }} thẻ mới</template>
            <template v-else-if="summary.nextDueAt">Thẻ tiếp theo đến hạn lúc {{ formatDateTime(summary.nextDueAt) }}</template>
            <template v-else>Thêm một bộ từ bên dưới hoặc lưu từ khi đọc bài.</template>
          </p>
          <p class="hero__sub">Đã ôn {{ summary.reviewedToday }} lượt hôm nay</p>
        </div>
        <el-button size="large" class="hero__btn" :icon="VideoPlay" :disabled="!toReview"
                   @click="router.push({ name: 'flashcard-review' })">
          {{ toReview ? 'Bắt đầu ôn tập' : 'Chưa có thẻ cần ôn' }}
        </el-button>
      </section>

      <div class="grid">
        <section class="eh-card">
          <div class="eh-card__title">
            Kho từ của bạn ({{ summary.total }})
            <RouterLink :to="{ name: 'vocabulary-notebook' }" class="link"><el-icon><Notebook /></el-icon> Sổ từ</RouterLink>
          </div>
          <div v-if="activeTotal" class="stack" aria-hidden="true">
            <span v-for="b in breakdown" :key="b.key" :style="{ flex: b.value, background: b.color }" />
          </div>
          <div class="legend">
            <div v-for="b in breakdown" :key="b.key">
              <span class="dot" :style="{ background: b.color }" />
              <span class="eh-muted">{{ b.label }}</span>
              <strong>{{ b.value }}</strong>
            </div>
          </div>
          <p v-if="summary.suspended" class="eh-subtle">{{ summary.suspended }} từ đang tạm ẩn</p>
          <p class="eh-subtle">"Đã thuộc" = từ có khoảng ôn từ 21 ngày trở lên.</p>
        </section>

        <section class="eh-card">
          <div class="eh-card__title">Dự báo 7 ngày tới</div>
          <div class="forecast">
            <div v-for="(f, i) in summary.forecast" :key="f.date" class="forecast__col" :title="`${f.count} thẻ`">
              <span class="forecast__count">{{ f.count || '' }}</span>
              <span class="forecast__bar" :style="{ height: `${Math.max(4, (f.count / forecastMax) * 100)}%` }"
                    :class="{ 'forecast__bar--today': i === 0 }" />
              <span class="forecast__day">{{ dayLabel(f.date, i) }}</span>
            </div>
          </div>
        </section>
      </div>

      <section class="sets">
        <h3 class="sets__title"><el-icon><Collection /></el-icon> Bộ từ vựng theo chủ đề
          <span class="eh-subtle">{{ visibleSets.length }}/{{ sets.length }} bộ</span></h3>
        <div class="sets__filters">
          <div class="sets__groups" role="tablist" aria-label="Nhóm bộ từ">
            <button v-for="g in GROUPS" :key="g.key" type="button" role="tab" class="sets__group"
                    :class="{ 'sets__group--active': group === g.key }" :aria-selected="group === g.key" @click="group = g.key">
              {{ g.label }} <small>{{ groupCounts[g.key] ?? 0 }}</small>
            </button>
          </div>
          <el-input v-model="keyword" :prefix-icon="Search" placeholder="Tìm chủ đề hoặc từ..." clearable class="sets__search" />
        </div>
        <el-empty v-if="!visibleSets.length" description="Không có bộ từ nào khớp" />
        <div class="sets__grid">
          <article v-for="set in visibleSets" :key="set.id" class="eh-card set">
            <RouterLink :to="{ name: 'vocabulary-set', params: { setId: set.id } }" class="set__name">{{ set.name }}</RouterLink>
            <p class="eh-muted set__desc">{{ set.description }}</p>
            <div class="set__words">
              <span v-for="w in set.previewWords" :key="w" class="chip">{{ w }}</span>
              <span v-if="set.wordCount > set.previewWords.length" class="eh-subtle">+{{ set.wordCount - set.previewWords.length }}</span>
            </div>
            <div class="set__foot">
              <el-progress :percentage="Math.round(set.addedCount / set.wordCount * 100)" :stroke-width="6"
                           :format="() => `${set.addedCount}/${set.wordCount}`" class="set__progress" />
              <el-button v-if="set.addedCount < set.wordCount" size="small" type="primary" plain :icon="Plus"
                         :loading="adding === set.id" @click="addSet(set)">Thêm vào sổ</el-button>
              <RouterLink v-else :to="{ name: 'vocabulary-set', params: { setId: set.id } }" class="link">
                Xem <el-icon><Right /></el-icon>
              </RouterLink>
            </div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.hero {
  display: flex; align-items: center; justify-content: space-between; gap: 20px; flex-wrap: wrap;
  padding: 28px; margin-bottom: 20px; border-radius: 20px; color: #fff;
  background: radial-gradient(circle at 90% 10%, rgba(255, 255, 255, 0.25), transparent 45%),
    linear-gradient(135deg, #db2777, #7c3aed);
}
.hero__eyebrow { font-size: 13px; text-transform: uppercase; letter-spacing: 0.6px; opacity: 0.85; }
.hero h2 { font-size: 28px; margin: 4px 0 6px; }
.hero__sub { font-size: 13px; opacity: 0.8; margin-top: 4px; }
.hero__btn { background: #fff; color: #7c3aed; border: 0; font-weight: 700; }
.hero__btn:hover { background: #f5f3ff; color: #6d28d9; }

.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 24px; }
.link { display: inline-flex; align-items: center; gap: 4px; font-size: 14px; font-weight: 500; }
.stack { display: flex; height: 12px; border-radius: 999px; overflow: hidden; margin-bottom: 14px; gap: 2px; }
.legend { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; margin-bottom: 8px; }
.legend div { display: flex; align-items: center; gap: 8px; }
.legend strong { margin-left: auto; }
.dot { width: 10px; height: 10px; border-radius: 50%; }

.forecast { display: flex; align-items: flex-end; gap: 8px; height: 150px; }
.forecast__col { flex: 1; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 4px; }
.forecast__count { font-size: 12px; font-weight: 600; color: var(--eh-text-2); min-height: 16px; }
.forecast__bar { width: 100%; max-width: 36px; border-radius: 6px 6px 2px 2px; background: var(--el-color-primary-light-5); }
.forecast__bar--today { background: #db2777; }
.forecast__day { font-size: 12px; color: var(--eh-text-3); white-space: nowrap; }

.sets__title { display: flex; align-items: center; gap: 8px; font-size: 18px; margin-bottom: 12px; }
.sets__title .eh-subtle { margin-left: auto; font-weight: 400; }
.sets__filters { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 16px; margin-bottom: 16px; }
.sets__groups { display: flex; flex-wrap: wrap; gap: 6px; }
.sets__group {
  display: inline-flex; align-items: center; gap: 6px; min-height: 36px; padding: 0 14px; border-radius: 999px; cursor: pointer;
  border: 1px solid var(--eh-border); background: var(--eh-surface); color: var(--eh-text-2); font: inherit; font-size: 14px; font-weight: 600;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}
.sets__group:hover { border-color: var(--eh-primary); color: var(--eh-primary); }
.sets__group small { font-weight: 500; opacity: 0.75; }
.sets__group--active, .sets__group--active:hover { background: var(--eh-primary); border-color: var(--eh-primary); color: var(--eh-on-primary); }
.sets__search { width: 260px; max-width: 100%; }
@media (max-width: 599.98px) { .sets__search { width: 100%; } }
.sets__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.set { display: flex; flex-direction: column; gap: 8px; }
.set__name { font-size: 17px; font-weight: 700; color: var(--eh-text); }
.set__desc { font-size: 14px; flex: 1; }
.set__words { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.chip { padding: 2px 10px; border-radius: 999px; background: color-mix(in srgb, #ec4899 15%, transparent); color: color-mix(in srgb, #be185d 70%, var(--eh-text)); font-size: 13px; font-weight: 500; }
.set__foot { display: flex; align-items: center; gap: 12px; margin-top: 6px; }
.set__progress { flex: 1; }

@media (max-width: 899.98px) {
  .grid { grid-template-columns: 1fr; }
}
@media (max-width: 599.98px) {
  .hero { padding: 22px; }
  .hero h2 { font-size: 22px; }
  .hero__btn { width: 100%; }
}
</style>
