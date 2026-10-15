<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Headset, Reading, Timer, TrendCharts } from '@element-plus/icons-vue'
import { toeicApi } from '@/api'
import { formatPercent, formatRelative } from '@/utils/format'
import { PART_LABELS } from '@/utils/toeic'

const data = ref(null)
const loading = ref(true)

const listening = computed(() => data.value?.parts.filter((p) => p.section === 'LISTENING') ?? [])
const reading = computed(() => data.value?.parts.filter((p) => p.section === 'READING') ?? [])
const totalQuestions = computed(() => data.value?.parts.reduce((s, p) => s + p.questionCount, 0) ?? 0)

const resultLabel = (r) => (r.mode === 'TEST' ? 'Thi thử rút gọn' : PART_LABELS[r.part])

onMounted(async () => {
  try {
    data.value = await toeicApi.overview()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="eh-page toeic">
    <template v-if="data">
      <section class="hero">
        <div class="hero__text">
          <h2>Luyện thi TOEIC Listening & Reading</h2>
          <p>{{ totalQuestions }} câu hỏi theo đúng định dạng đề thi, có giải thích tiếng Việt và bản dịch.
            Phần Nghe được đọc bằng giọng tổng hợp của trình duyệt (giọng nam / nữ theo người nói).</p>
        </div>
        <div class="hero__test">
          <el-icon :size="28"><Timer /></el-icon>
          <strong>Đề thi thử rút gọn</strong>
          <span>~40 câu · {{ data.miniTest.durationMinutes }} phút · đủ Part 2-7</span>
          <span v-if="data.miniTest.lastEstimate" class="hero__score">Lần gần nhất: {{ data.miniTest.lastEstimate.total }} điểm (ước lượng)</span>
          <RouterLink :to="{ name: 'toeic-test' }"><el-button type="warning" size="large">Bắt đầu thi thử</el-button></RouterLink>
        </div>
      </section>

      <section v-for="block in [{ key: 'L', title: 'Listening', icon: Headset, parts: listening }, { key: 'R', title: 'Reading', icon: Reading, parts: reading }]"
               :key="block.key" class="block">
        <h3 class="block__title"><el-icon><component :is="block.icon" /></el-icon> {{ block.title }}</h3>
        <div class="parts">
          <article v-for="p in block.parts" :key="p.part" class="eh-card part">
            <div class="part__head">
              <span class="part__badge">Part {{ p.part }}</span>
              <strong>{{ p.name }}</strong>
            </div>
            <p class="eh-subtle part__desc">{{ p.description }}</p>
            <div class="part__stats">
              <span>{{ p.questionCount }} câu</span>
              <span v-if="p.bestPercent != null">Tốt nhất {{ formatPercent(p.bestPercent) }}</span>
            </div>
            <el-progress :percentage="Math.round((p.completedGroups / p.groupCount) * 100)" :stroke-width="6"
                         :format="() => `${p.completedGroups}/${p.groupCount}`" />
            <RouterLink :to="{ name: 'toeic-practice', params: { part: p.part } }">
              <el-button type="primary" plain class="part__btn">{{ p.sessions ? 'Luyện tiếp' : 'Bắt đầu luyện' }}</el-button>
            </RouterLink>
          </article>
        </div>
      </section>

      <section v-if="data.recent.length" class="eh-card">
        <div class="eh-card__title"><el-icon><TrendCharts /></el-icon> Kết quả gần đây</div>
        <ul class="recent">
          <li v-for="r in data.recent" :key="r.id">
            <span class="recent__name">{{ resultLabel(r) }}</span>
            <span class="eh-subtle">{{ formatRelative(r.createdAt) }}</span>
            <span>{{ r.correct }}/{{ r.total }}</span>
            <strong :class="r.percent >= 70 ? 'good' : r.percent >= 50 ? 'ok' : 'bad'">{{ formatPercent(r.percent) }}</strong>
          </li>
        </ul>
      </section>

      <p class="eh-subtle disclaimer">
        Nội dung do EnglishHub tự biên soạn theo định dạng TOEIC, không phải đề thi chính thức của ETS. Chưa có Part 1 (mô tả tranh) vì cần hình ảnh.
      </p>
    </template>
  </div>
</template>

<style scoped>
.toeic { display: grid; gap: 24px; }
.hero {
  display: grid; grid-template-columns: 1.4fr 1fr; gap: 20px; padding: 24px; border-radius: 18px; color: #fff;
  background: radial-gradient(circle at 90% 10%, rgba(251, 191, 36, 0.35), transparent 45%), linear-gradient(135deg, #312e81, #4f46e5);
}
.hero__text h2 { font-size: 24px; margin-bottom: 8px; }
.hero__text p { color: #e0e7ff; line-height: 1.6; }
.hero__test { display: grid; gap: 6px; justify-items: start; padding: 18px; border-radius: 14px; background: rgba(255, 255, 255, 0.12); }
.hero__test span { color: #e0e7ff; font-size: 14px; }
.hero__score { font-weight: 600; color: #fde68a !important; }
.block { display: grid; gap: 12px; }
.block__title { display: flex; align-items: center; gap: 8px; font-size: 18px; margin: 0; }
.parts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.part { display: grid; gap: 10px; align-content: start; }
.part__head { display: flex; align-items: center; gap: 10px; }
.part__badge { padding: 2px 10px; border-radius: 999px; background: var(--eh-primary-soft); color: var(--eh-primary); font-weight: 700; font-size: 13px; }
.part__desc { font-size: 14px; min-height: 40px; }
.part__stats { display: flex; justify-content: space-between; font-size: 13px; color: var(--eh-text-2); }
.part__btn { width: 100%; }
.recent { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.recent li { display: grid; grid-template-columns: 1fr auto 60px 60px; gap: 12px; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--eh-border); }
.recent li:last-child { border-bottom: 0; }
.recent__name { font-weight: 500; }
.recent strong { text-align: right; }
.good { color: #059669; }
.ok { color: #d97706; }
.bad { color: #dc2626; }
.disclaimer { font-size: 13px; text-align: center; }
@media (max-width: 1023.98px) {
  .hero { grid-template-columns: 1fr; }
  .parts { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 599.98px) {
  .parts { grid-template-columns: 1fr; }
  .recent li { grid-template-columns: 1fr auto; }
}
</style>
