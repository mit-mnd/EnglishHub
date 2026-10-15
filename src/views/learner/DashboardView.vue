<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Collection, EditPen, Guide, Reading, Right, Trophy } from '@element-plus/icons-vue'
import EChart from '@/components/charts/EChart.vue'
import FlameIcon from '@/components/common/FlameIcon.vue'
import StatCard from '@/components/dashboard/StatCard.vue'
import { useProfileStore } from '@/stores/profile'
import { useDashboardStore } from '@/stores/dashboard'
import { firstName, formatDate, formatMinutes, formatPercent, greeting } from '@/utils/format'
import { GOAL_LABELS, levelColor, skillLabel } from '@/utils/labels'
import { useTheme } from '@/composables/useTheme'
import { cssVar, withAlpha } from '@/utils/cssVar'

const { isDark } = useTheme()
const router = useRouter()
const learner = useProfileStore()
const store = useDashboardStore()

const metric = ref('time')
const d = computed(() => store.data)

const ranges = [
  { value: '7d', label: '7 ngày' },
  { value: '30d', label: '30 ngày' },
  { value: '90d', label: '3 tháng' },
  { value: '365d', label: '12 tháng' },
]

const todayPercent = computed(() => {
  const t = d.value?.todayProgress
  return t ? Math.min(100, Math.round((t.studyMinutes / t.goalMinutes) * 100)) : 0
})

/** Nhãn trục X: ngày/tuần (ngày đầu tuần) "14/10", tháng "T10/26". */
function periodLabel(isoDate, granularity) {
  const [y, m, day] = isoDate.split('-')
  return granularity === 'MONTH' ? `T${Number(m)}/${y.slice(2)}` : `${day}/${m}`
}

const chartOption = computed(() => {
  // Canvas không hiểu var(): lấy màu thật của giao diện hiện hành (tính lại khi đổi sáng/tối)
  void isDark.value
  const brand = { primary: cssVar('--eh-primary'), success: cssVar('--eh-success') }
  const series = d.value?.series ?? []
  const labels = series.map((p) => periodLabel(p.periodStart, d.value.granularity))
  const base = {
    // ECharts 6 tự chừa chỗ cho nhãn trục (outerBounds), không cần containLabel
    grid: { left: 8, right: 8, top: 36, bottom: 4 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'category', data: labels, axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', minInterval: 1 },
  }
  if (metric.value === 'words') {
    return {
      ...base,
      legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, icon: 'roundRect' },
      series: [
        { name: 'Từ mới', type: 'bar', stack: 'w', data: series.map((p) => p.wordsLearned), itemStyle: { color: brand.primary }, barMaxWidth: 28 },
        { name: 'Từ ôn tập', type: 'bar', stack: 'w', data: series.map((p) => p.wordsReviewed),
          itemStyle: { color: withAlpha(brand.primary, 0.45), borderRadius: [6, 6, 0, 0] }, barMaxWidth: 28 },
      ],
    }
  }
  if (metric.value === 'score') {
    return {
      ...base,
      yAxis: { ...base.yAxis, max: 100, axisLabel: { formatter: '{value}%' } },
      tooltip: { trigger: 'axis', valueFormatter: (v) => (v == null ? 'Không làm quiz' : `${v}%`) },
      series: [{
        name: 'Điểm trung bình', type: 'line', smooth: true, connectNulls: true, symbolSize: 8,
        data: series.map((p) => (p.averageScore == null ? null : Number(p.averageScore))),
        itemStyle: { color: brand.success }, areaStyle: { color: withAlpha(brand.success, 0.14) },
      }],
    }
  }
  return {
    ...base,
    tooltip: { ...base.tooltip, valueFormatter: (v) => `${v} phút` },
    series: [{
      name: 'Thời gian học', type: 'bar', data: series.map((p) => p.studyMinutes), barMaxWidth: 28,
      itemStyle: { color: brand.primary, borderRadius: [6, 6, 0, 0] },
    }],
  }
})

const chartTotals = computed(() => {
  const s = d.value?.series ?? []
  return {
    minutes: s.reduce((a, p) => a + p.studyMinutes, 0),
    words: s.reduce((a, p) => a + p.wordsLearned, 0),
    activeDays: d.value?.granularity === 'DAY' ? s.filter((p) => p.studyMinutes > 0).length : null,
    hasActivity: s.some((p) => p.studyMinutes || p.wordsLearned || p.wordsReviewed || p.quizzesTaken),
  }
})

onMounted(() => store.load(store.range))
</script>

<template>
  <div class="eh-page">
    <header class="welcome">
      <div>
        <h2 class="welcome__title">{{ greeting() }}, {{ firstName(learner.user?.fullName) }}!</h2>
        <p class="eh-muted welcome__meta">
          <span v-if="learner.user?.currentLevel" class="level-pill" :style="{ '--lv': levelColor(learner.user.currentLevel.code) }">
            {{ learner.user.currentLevel.name }}
          </span>
          <span>Mục tiêu: <strong>{{ GOAL_LABELS[learner.user?.learningGoal] }}</strong></span>
          <span v-if="d">· Hôm nay {{ formatDate(d.today) }}</span>
        </p>
      </div>
    </header>

    <!-- Chưa làm bài xếp lớp -->
    <section v-if="d && !d.latestPlacement" class="cta">
      <div class="cta__icon"><el-icon><EditPen /></el-icon></div>
      <div class="cta__text">
        <strong>Bạn chưa làm bài kiểm tra xếp lớp</strong>
        <p>20 phút để biết chính xác trình độ và nhận lộ trình học phù hợp.</p>
      </div>
      <el-button type="primary" @click="router.push({ name: 'placement' })">Làm ngay</el-button>
    </section>

    <el-alert v-if="store.error && !d" type="error" :title="store.error.message" show-icon :closable="false">
      <el-button size="small" @click="store.load()">Thử lại</el-button>
    </el-alert>
    <el-skeleton v-else-if="!d" :rows="10" animated />

    <template v-else>
      <div class="stats">
        <StatCard label="Chuỗi ngày học" :value="d.streak.current" unit="ngày" tone="accent"
                  :hint="d.streak.activeToday ? 'Đã học hôm nay 🎉' : `Kỷ lục: ${d.streak.longest} ngày`">
          <template #icon><FlameIcon /></template>
        </StatCard>

        <div class="eh-card today">
          <el-progress type="circle" :percentage="todayPercent" :width="64" :stroke-width="7"
                       :color="d.todayProgress.goalReached ? 'var(--eh-success)' : 'var(--eh-primary)'">
            <template #default><span class="today__pct">{{ todayPercent }}%</span></template>
          </el-progress>
          <div>
            <span class="today__label">Mục tiêu hôm nay</span>
            <span class="today__value">{{ d.todayProgress.studyMinutes }}<small>/{{ d.todayProgress.goalMinutes }} phút</small></span>
            <span class="eh-subtle">{{ d.todayProgress.goalReached ? 'Hoàn thành!' : `Còn ${d.todayProgress.goalMinutes - d.todayProgress.studyMinutes} phút` }}</span>
          </div>
        </div>

        <StatCard label="Từ vựng đã thuộc" :value="d.totals.wordsMastered" unit="từ" tone="success"
                  :hint="`${d.totals.wordsLearning} đang học · ${d.totals.wordsNew} từ mới`">
          <template #icon><el-icon><Collection /></el-icon></template>
        </StatCard>

        <StatCard label="Bài học hoàn thành" :value="d.totals.lessonsCompleted" tone="info"
                  :hint="d.totals.averageQuizScore != null ? `Điểm quiz TB: ${formatPercent(d.totals.averageQuizScore)}` : 'Chưa làm quiz'">
          <template #icon><el-icon><Reading /></el-icon></template>
        </StatCard>
      </div>

      <div class="main-grid">
        <section class="eh-card chart-card">
          <div class="chart-head">
            <div>
              <h3>Tiến độ học tập</h3>
              <p class="eh-subtle">
                {{ formatMinutes(chartTotals.minutes) }} học · {{ chartTotals.words }} từ mới
                <template v-if="chartTotals.activeDays != null"> · {{ chartTotals.activeDays }} ngày có học</template>
              </p>
            </div>
            <el-segmented v-model="store.range" :options="ranges" size="small" :disabled="store.loading"
                          @change="(v) => store.load(v)" />
          </div>
          <el-radio-group v-model="metric" size="small" class="metric">
            <el-radio-button value="time">Thời gian</el-radio-button>
            <el-radio-button value="words">Từ vựng</el-radio-button>
            <el-radio-button value="score">Điểm số</el-radio-button>
          </el-radio-group>
          <div v-loading="store.loading" class="chart-wrap">
            <EChart :option="chartOption" height="280px" :aria-label="`Biểu đồ ${metric}`" />
            <div v-if="!chartTotals.hasActivity" class="chart-empty">
              <strong>Chưa có hoạt động trong khoảng thời gian này</strong>
              <span class="eh-subtle">Học một bài hoặc ôn vài từ vựng để bắt đầu theo dõi tiến độ nhé!</span>
            </div>
          </div>
        </section>

        <aside class="side">
          <section class="eh-card">
            <div class="eh-card__title">
              <span><el-icon><Guide /></el-icon> Lộ trình của bạn</span>
              <RouterLink v-if="d.learningPath" :to="{ name: 'learning-path' }" class="see-all">Xem tất cả</RouterLink>
            </div>
            <template v-if="d.learningPath">
              <strong>{{ d.learningPath.name }}</strong>
              <div class="path__progress">
                <el-progress :percentage="Number(d.learningPath.percent)" :stroke-width="10" />
                <span class="eh-subtle">{{ d.learningPath.completedItems }}/{{ d.learningPath.totalItems }} bài</span>
              </div>
              <div v-if="d.learningPath.nextLesson" class="next">
                <span class="eh-subtle">{{ d.learningPath.nextLesson.status === 'IN_PROGRESS' ? 'Đang học dở' : 'Bài tiếp theo' }}</span>
                <strong>{{ d.learningPath.nextLesson.title }}</strong>
                <span class="eh-subtle">{{ skillLabel(d.learningPath.nextLesson.skill) }} · {{ d.learningPath.nextLesson.estimatedMinutes }} phút</span>
                <el-button type="primary" class="next__btn"
                           @click="router.push({ name: 'lesson', params: { lessonId: d.learningPath.nextLesson.id } })">
                  Học tiếp <el-icon class="el-icon--right"><Right /></el-icon>
                </el-button>
              </div>
            </template>
            <div v-else class="no-path">
              <p class="eh-muted">Bạn chưa có lộ trình. Làm bài xếp lớp hoặc tự chọn mục tiêu để bắt đầu.</p>
              <el-button @click="router.push({ name: 'learning-path' })">Tạo lộ trình</el-button>
            </div>
          </section>

          <section class="eh-card review-card" :class="{ 'review-card--due': d.totals.wordsDue }">
            <div>
              <strong>{{ d.totals.wordsDue ? `${d.totals.wordsDue} từ cần ôn hôm nay` : 'Không có từ cần ôn' }}</strong>
              <p class="eh-subtle">Ôn đúng lúc giúp ghi nhớ lâu hơn.</p>
            </div>
            <el-button :type="d.totals.wordsDue ? 'warning' : 'default'"
                       @click="router.push({ name: d.totals.wordsDue ? 'flashcard-review' : 'vocabulary' })">
              {{ d.totals.wordsDue ? 'Ôn tập' : 'Từ vựng' }}
            </el-button>
          </section>

          <section v-if="d.latestPlacement" class="eh-card">
            <div class="eh-card__title"><span><el-icon><Trophy /></el-icon> Kết quả xếp lớp</span></div>
            <div class="placement" :style="{ '--lv': levelColor(d.latestPlacement.levelCode) }">
              <span class="placement__badge">{{ formatPercent(d.latestPlacement.percentage) }}</span>
              <div>
                <strong>{{ d.latestPlacement.levelName }}</strong>
                <p class="eh-subtle">{{ formatDate(d.latestPlacement.submittedAt) }}</p>
              </div>
              <RouterLink :to="{ name: 'placement-result', params: { attemptId: d.latestPlacement.attemptId } }" class="placement__link">
                Chi tiết
              </RouterLink>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.welcome { margin-bottom: 20px; }
.welcome__title { font-size: 26px; }
.welcome__meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 6px; }
.level-pill {
  padding: 2px 10px; border-radius: 999px; font-size: 13px; font-weight: 600;
  color: #fff; background: var(--lv);
}

.cta {
  display: flex; align-items: center; gap: 16px; padding: 18px 20px; margin-bottom: 20px;
  border-radius: var(--eh-radius); border: 1px solid var(--el-color-primary-light-7);
  background: linear-gradient(120deg, var(--eh-primary-soft), var(--eh-accent-soft));
}
.cta__icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 12px; background: var(--eh-primary); color: var(--eh-on-primary); font-size: 22px; }
.cta__text { flex: 1; }
.cta__text p { color: var(--eh-text-2); font-size: 14px; }

.stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.today { display: flex; align-items: center; gap: 14px; }
.today > div { display: flex; flex-direction: column; }
.today__pct { font-size: 14px; font-weight: 700; }
.today__label { font-size: 13px; color: var(--eh-text-2); }
.today__value { font-size: 26px; font-weight: 700; line-height: 1.25; }
.today__value small { font-size: 14px; font-weight: 500; color: var(--eh-text-2); }

.main-grid { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 20px; align-items: start; }
.chart-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.chart-head h3 { font-size: 16px; }
.metric { margin: 14px 0 6px; }
.chart-wrap { position: relative; }
.chart-empty {
  position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; padding: 16px; text-align: center; background: color-mix(in srgb, var(--eh-surface) 75%, transparent);
}

.side { display: grid; gap: 16px; }
.side .eh-card__title span { display: inline-flex; align-items: center; gap: 6px; }
.path__progress { display: flex; align-items: center; gap: 10px; margin: 10px 0 14px; }
.path__progress .el-progress { flex: 1; }
.next { display: flex; flex-direction: column; gap: 2px; padding: 14px; border-radius: 12px; background: var(--eh-bg); }
.next__btn { margin-top: 10px; }
.see-all { font-size: 13px; font-weight: 500; }
.no-path { display: grid; gap: 10px; justify-items: start; }

.review-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.review-card--due { background: var(--eh-accent-soft); border-color: var(--eh-accent-border); }

.placement { display: flex; align-items: center; gap: 12px; }
.placement__badge {
  display: grid; place-items: center; width: 52px; height: 52px; border-radius: 12px;
  background: var(--lv); color: #fff; font-weight: 700; font-size: 14px;
}
.placement > div { flex: 1; }

@media (max-width: 1199.98px) {
  .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .main-grid { grid-template-columns: 1fr; }
  .side { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
}
@media (max-width: 767.98px) {
  .welcome__title { font-size: 21px; }
  .stats { gap: 12px; }
  .stats :deep(.stat__value), .today__value { font-size: 22px; }
  .cta { flex-wrap: wrap; }
  .cta .el-button { width: 100%; }
}
/* Điện thoại: giữ 2 cột (đỡ phải cuộn dài), xếp biểu tượng phía trên chữ */
@media (max-width: 479.98px) {
  .stats > * { padding: 14px; }
  .stats :deep(.stat), .stats .today { flex-direction: column; align-items: flex-start; gap: 10px; }
}
</style>
