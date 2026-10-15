<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, CircleCloseFilled, Guide, House, RefreshRight, Timer } from '@element-plus/icons-vue'
import { pathApi, placementApi } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { useDashboardStore } from '@/stores/dashboard'
import { formatDateTime, formatDuration, formatPercent } from '@/utils/format'
import { levelColor, skillLabel } from '@/utils/labels'
import { describeAnswer, describeCorrectAnswer } from '@/utils/question'

const props = defineProps({ attemptId: { type: String, required: true } })

const router = useRouter()
const learner = useProfileStore()
const dashboard = useDashboardStore()

const result = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const filter = ref('all')

const reviewItems = computed(() => {
  const items = result.value?.review ?? []
  if (filter.value === 'wrong') return items.filter((r) => r.correct !== true)
  if (filter.value === 'right') return items.filter((r) => r.correct === true)
  return items
})
const wrongCount = computed(() => (result.value?.review ?? []).filter((r) => r.correct !== true).length)

/** Kỹ năng yếu nhất (dưới 60%) -> gợi ý tập trung. */
const weakSkills = computed(() => (result.value?.skillScores ?? [])
  .filter((s) => Number(s.percentage) < 60)
  .map((s) => skillLabel(s.skill)))

const skillColor = (p) => (p >= 80 ? '#10b981' : p >= 60 ? '#0ea5e9' : p >= 40 ? '#f59e0b' : '#ef4444')

// Lộ trình đang chạy có được sinh từ chính bài làm này không (để gợi ý cập nhật)
const path = ref(null)
const regenerating = ref(false)

async function loadPath() {
  const active = await pathApi.get().catch(() => null)
  path.value = active
    ? { placementBased: active.source === 'PLACEMENT' && new Date(active.createdAt) >= new Date(result.value.submittedAt) }
    : null
}

async function regeneratePath() {
  regenerating.value = true
  try {
    await pathApi.generate({})
    ElMessage.success('Đã cập nhật lộ trình theo kết quả mới')
    router.push({ name: 'learning-path' })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    regenerating.value = false
  }
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await placementApi.result(props.attemptId)
    loadPath()
    // Level của học viên vừa thay đổi -> làm mới hồ sơ & dashboard
    learner.refresh().catch(() => {})
    dashboard.load().catch(() => {})
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="eh-page">
    <el-skeleton v-if="loading" :rows="10" animated />

    <el-result v-else-if="errorMessage" icon="error" title="Không tải được kết quả" :sub-title="errorMessage">
      <template #extra><el-button type="primary" @click="router.push({ name: 'placement' })">Quay lại</el-button></template>
    </el-result>

    <template v-else>
      <section class="hero" :style="{ '--lv': levelColor(result.level?.code) }">
        <div class="hero__text">
          <span class="hero__eyebrow">Trình độ của bạn</span>
          <h2 class="hero__level">{{ result.level?.name }}</h2>
          <p class="hero__cefr">CEFR {{ result.level?.cefrRange }}</p>
          <p class="hero__desc">{{ result.level?.description }}</p>
          <el-alert v-if="result.status === 'EXPIRED'" type="warning" :closable="false" show-icon
                    title="Bài làm hết giờ và được nộp tự động với các câu đã lưu." class="hero__alert" />
        </div>
        <div class="hero__score">
          <el-progress type="circle" :percentage="Number(result.percentage)" :width="148" :stroke-width="12"
                       color="#ffffff">
            <template #default>
              <span class="ring__value">{{ formatPercent(result.percentage) }}</span>
              <span class="ring__label">{{ result.score }}/{{ result.maxScore }} điểm</span>
            </template>
          </el-progress>
        </div>
      </section>

      <div class="grid">
        <section class="eh-card">
          <div class="eh-card__title">Điểm theo kỹ năng</div>
          <div class="skills">
            <div v-for="s in result.skillScores" :key="s.skill" class="skill">
              <div class="skill__head">
                <span>{{ skillLabel(s.skill) }}</span>
                <strong>{{ formatPercent(s.percentage) }}</strong>
              </div>
              <el-progress :percentage="Number(s.percentage)" :show-text="false" :stroke-width="10"
                           :color="skillColor(Number(s.percentage))" />
            </div>
          </div>
          <p v-if="weakSkills.length" class="tip">
            💡 Nên tập trung thêm vào: <strong>{{ weakSkills.join(', ') }}</strong>. Lộ trình học sẽ ưu tiên các kỹ năng này.
          </p>
        </section>

        <section class="eh-card stats">
          <div class="eh-card__title">Thống kê</div>
          <dl>
            <div><dt>Số câu đúng</dt><dd>{{ result.correctCount }}/{{ result.totalQuestions }}</dd></div>
            <div><dt><el-icon><Timer /></el-icon> Thời gian làm</dt><dd>{{ formatDuration(result.durationSeconds) }}</dd></div>
            <div><dt>Nộp lúc</dt><dd>{{ formatDateTime(result.submittedAt) }}</dd></div>
          </dl>
          <div class="actions">
            <el-button type="primary" :icon="Guide" @click="router.push({ name: 'learning-path' })">Xem lộ trình học</el-button>
            <el-button :icon="House" @click="router.push({ name: 'dashboard' })">Trang tổng quan</el-button>
            <el-button :icon="RefreshRight" @click="router.push({ name: 'placement' })">Làm lại</el-button>
          </div>
          <div v-if="path && path.placementBased === false" class="path-hint">
            <p class="eh-subtle">Lộ trình hiện tại của bạn chưa dựa trên kết quả này.</p>
            <el-button link type="primary" :loading="regenerating" @click="regeneratePath">Cập nhật lộ trình theo kết quả mới</el-button>
          </div>
        </section>
      </div>

      <section class="eh-card review">
        <div class="eh-card__title review__title">
          Xem lại bài làm
          <el-radio-group v-model="filter" size="small">
            <el-radio-button value="all">Tất cả ({{ result.review.length }})</el-radio-button>
            <el-radio-button value="wrong">Sai ({{ wrongCount }})</el-radio-button>
            <el-radio-button value="right">Đúng ({{ result.review.length - wrongCount }})</el-radio-button>
          </el-radio-group>
        </div>

        <el-empty v-if="!reviewItems.length" description="Không có câu nào trong mục này" />
        <el-collapse v-else>
          <el-collapse-item v-for="item in reviewItems" :key="item.questionId" :name="item.questionId">
            <template #title>
              <span class="review__row">
                <el-icon :class="item.correct ? 'ok' : 'bad'" :size="18">
                  <CircleCheckFilled v-if="item.correct" /><CircleCloseFilled v-else />
                </el-icon>
                <span class="review__num">Câu {{ item.number }}</span>
                <span class="review__q">{{ item.content }}</span>
                <span class="review__pts">{{ item.score }}/{{ item.points }}</span>
              </span>
            </template>
            <blockquote v-if="item.passage" class="passage">{{ item.passage }}</blockquote>
            <p><span class="eh-subtle">Câu trả lời của bạn:</span>
              <strong :class="item.correct ? 'ok' : 'bad'"> {{ describeAnswer(item, item.yourAnswer) }}</strong></p>
            <p v-if="!item.correct"><span class="eh-subtle">Đáp án đúng:</span>
              <strong class="ok"> {{ describeCorrectAnswer(item) }}</strong></p>
            <p v-if="item.explanation" class="explain">{{ item.explanation }}</p>
          </el-collapse-item>
        </el-collapse>
      </section>
    </template>
  </div>
</template>

<style scoped>
.hero {
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  padding: 32px; margin-bottom: 20px; border-radius: 20px; color: #fff;
  background:
    radial-gradient(circle at 90% 10%, rgba(255, 255, 255, 0.25), transparent 40%),
    linear-gradient(135deg, var(--lv), color-mix(in srgb, var(--lv) 60%, #1e1b4b));
}
.hero__eyebrow { font-size: 13px; text-transform: uppercase; letter-spacing: 1px; opacity: 0.85; }
.hero__level { font-size: 36px; margin: 6px 0 2px; }
.hero__cefr { font-weight: 600; opacity: 0.9; }
.hero__desc { margin-top: 12px; max-width: 520px; opacity: 0.95; }
.hero__alert { margin-top: 16px; max-width: 520px; }
.hero__score :deep(.el-progress-circle__track) { stroke: rgba(255, 255, 255, 0.25); }
.ring__value { display: block; font-size: 30px; font-weight: 700; color: #fff; }
.ring__label { display: block; font-size: 13px; color: rgba(255, 255, 255, 0.85); margin-top: 2px; }

.grid { display: grid; grid-template-columns: 3fr 2fr; gap: 20px; margin-bottom: 20px; }
.skills { display: grid; gap: 14px; }
.skill__head { display: flex; justify-content: space-between; margin-bottom: 6px; }
.tip { margin-top: 18px; padding: 12px 14px; border-radius: 10px; background: var(--eh-accent-soft); color: var(--eh-accent-text); }

.stats dl { margin: 0; display: grid; gap: 12px; }
.stats dl div { display: flex; justify-content: space-between; padding-bottom: 12px; border-bottom: 1px solid var(--eh-border); }
.stats dt { display: inline-flex; align-items: center; gap: 6px; color: var(--eh-text-2); }
.stats dd { margin: 0; font-weight: 600; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.actions .el-button + .el-button { margin-left: 0; }
.path-hint { margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--eh-border); display: grid; gap: 4px; justify-items: start; }

.review__title { flex-wrap: wrap; }
.review__row { display: flex; align-items: center; gap: 10px; width: 100%; min-width: 0; padding-right: 8px; }
.review__num { font-weight: 600; white-space: nowrap; }
.review__q { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--eh-text-2); }
.review__pts { font-size: 13px; color: var(--eh-text-3); white-space: nowrap; }
.review p { margin: 6px 0; }
.passage { margin: 0 0 10px; padding: 10px 14px; border-left: 3px solid var(--eh-border); background: var(--eh-bg); border-radius: 0 8px 8px 0; }
.explain { padding: 10px 14px; border-radius: 10px; background: var(--eh-info-soft); color: var(--eh-info-text); }
.ok { color: var(--eh-success); }
.bad { color: var(--eh-danger); }

@media (max-width: 899.98px) {
  .grid { grid-template-columns: 1fr; }
}
@media (max-width: 767.98px) {
  .hero { flex-direction: column-reverse; align-items: flex-start; padding: 24px; }
  .hero__level { font-size: 28px; }
}
</style>
