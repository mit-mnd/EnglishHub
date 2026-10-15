<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Clock, Document, Finished, RefreshRight, VideoPlay } from '@element-plus/icons-vue'
import { levelApi, placementApi } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { formatDateTime, formatPercent, firstName } from '@/utils/format'
import { levelColor } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const learner = useProfileStore()

const info = ref(null)
const levels = ref([])
const history = ref({ items: [], totalElements: 0 })
const loading = ref(true)
const errorMessage = ref('')
const isWelcome = computed(() => route.query.welcome === '1')

const rules = [
  { icon: Clock, text: 'Thời gian được tính ngay khi bắt đầu và không thể tạm dừng.' },
  { icon: Document, text: 'Câu hỏi đi từ dễ đến khó — cứ làm hết sức, không cần đoán bừa.' },
  { icon: Finished, text: 'Câu trả lời được lưu tự động; mất mạng hay tải lại trang vẫn tiếp tục được.' },
]

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [testInfo, levelList, attempts] = await Promise.all([
      placementApi.info(), levelApi.list(), placementApi.history({ size: 5 }),
    ])
    info.value = testInfo
    levels.value = levelList
    history.value = attempts
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
    <el-alert v-if="isWelcome" type="success" show-icon :closable="false" class="welcome"
              :title="`Chào mừng ${firstName(learner.user?.fullName)} đến với EnglishHub!`"
              description="Hãy làm bài kiểm tra ngắn dưới đây để chúng tôi xếp đúng trình độ và gợi ý lộ trình phù hợp cho bạn." />

    <el-skeleton v-if="loading" :rows="8" animated />

    <el-result v-else-if="errorMessage" icon="error" title="Không tải được bài kiểm tra" :sub-title="errorMessage">
      <template #extra><el-button type="primary" @click="load">Thử lại</el-button></template>
    </el-result>

    <div v-else class="layout">
      <section class="eh-card hero">
        <span class="eh-chip">Bài kiểm tra đầu vào</span>
        <h2 class="hero__title">{{ info.title }}</h2>
        <p class="eh-muted">{{ info.description }}</p>

        <div class="hero__facts">
          <div><strong>{{ info.totalQuestions }}</strong><span>câu hỏi</span></div>
          <div><strong>{{ info.durationMinutes ?? '∞' }}</strong><span>phút</span></div>
          <div><strong>{{ levels.length }}</strong><span>cấp độ</span></div>
        </div>

        <ul class="rules">
          <li v-for="r in rules" :key="r.text">
            <el-icon><component :is="r.icon" /></el-icon><span>{{ r.text }}</span>
          </li>
        </ul>

        <div class="hero__actions">
          <el-button type="primary" size="large" :icon="VideoPlay" @click="router.push({ name: 'placement-test' })">
            {{ info.inProgressAttemptId ? 'Tiếp tục bài đang làm' : (info.latestResult ? 'Làm lại bài kiểm tra' : 'Bắt đầu làm bài') }}
          </el-button>
          <span v-if="info.inProgressAttemptId" class="eh-subtle">Bạn có một bài làm dở, thời gian vẫn đang chạy.</span>
        </div>
      </section>

      <aside class="side">
        <section v-if="info.latestResult" class="eh-card latest">
          <div class="eh-card__title">Kết quả gần nhất</div>
          <div class="latest__level" :style="{ '--lv': levelColor(info.latestResult.level?.code) }">
            <span class="latest__badge">{{ info.latestResult.level?.cefrRange }}</span>
            <div>
              <strong>{{ info.latestResult.level?.name }}</strong>
              <p class="eh-subtle">{{ formatPercent(info.latestResult.percentage, 1) }} · {{ formatDateTime(info.latestResult.submittedAt) }}</p>
            </div>
          </div>
          <RouterLink :to="{ name: 'placement-result', params: { attemptId: info.latestResult.attemptId } }">
            <el-button class="latest__btn">Xem chi tiết & lời giải</el-button>
          </RouterLink>
        </section>

        <section class="eh-card">
          <div class="eh-card__title">Các cấp độ</div>
          <ol class="levels">
            <li v-for="lv in levels" :key="lv.id" :style="{ '--lv': levelColor(lv.code) }"
                :class="{ 'levels__item--current': learner.user?.currentLevel?.code === lv.code }">
              <span class="levels__dot" />
              <div>
                <strong>{{ lv.name }}</strong> <span class="eh-subtle">{{ lv.cefrRange }}</span>
                <p class="eh-subtle">{{ lv.description }}</p>
              </div>
            </li>
          </ol>
        </section>
      </aside>

      <section v-if="history.totalElements" class="eh-card history">
        <div class="eh-card__title">
          Lịch sử làm bài
          <el-button text :icon="RefreshRight" @click="load">Làm mới</el-button>
        </div>
        <el-table :data="history.items" style="width: 100%">
          <el-table-column label="Ngày làm" min-width="150">
            <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="Điểm" min-width="100">
            <template #default="{ row }">{{ row.score }}/{{ row.maxScore }}</template>
          </el-table-column>
          <el-table-column label="Tỉ lệ" min-width="90">
            <template #default="{ row }">{{ formatPercent(row.percentage, 1) }}</template>
          </el-table-column>
          <el-table-column label="Trình độ" min-width="140">
            <template #default="{ row }">
              <el-tag :color="levelColor(row.level?.code)" effect="dark" style="border: 0">{{ row.level?.name }}</el-tag>
              <el-tag v-if="row.status === 'EXPIRED'" type="warning" size="small" style="margin-left: 6px">Hết giờ</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="" width="110" align="right">
            <template #default="{ row }">
              <RouterLink :to="{ name: 'placement-result', params: { attemptId: row.attemptId } }">Xem lại</RouterLink>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.welcome { margin-bottom: 20px; }
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 20px; align-items: start; }
.history { grid-column: 1 / -1; }

.hero { padding: 28px; }
.hero__title { font-size: 26px; margin: 12px 0 8px; }
.hero__facts { display: flex; gap: 12px; margin: 24px 0; }
.hero__facts div {
  flex: 1; display: flex; flex-direction: column; align-items: center; padding: 14px 8px;
  border-radius: 12px; background: var(--eh-bg);
}
.hero__facts strong { font-size: 26px; color: var(--eh-primary); }
.hero__facts span { font-size: 13px; color: var(--eh-text-2); }
.rules { list-style: none; padding: 0; margin: 0 0 24px; display: grid; gap: 12px; }
.rules li { display: flex; gap: 10px; color: var(--eh-text-2); }
.rules .el-icon { flex-shrink: 0; margin-top: 3px; color: var(--eh-primary); }
.hero__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }

.side { display: grid; gap: 20px; }
.latest__level { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.latest__badge {
  display: grid; place-items: center; width: 56px; height: 56px; border-radius: 14px;
  background: var(--lv); color: #fff; font-weight: 700; font-size: 13px; text-align: center;
}
.latest__level strong { font-size: 18px; }
.latest__btn { width: 100%; }

.levels { list-style: none; margin: 0; padding: 0; display: grid; gap: 14px; }
.levels li { display: flex; gap: 12px; }
.levels__dot { flex-shrink: 0; width: 12px; height: 12px; margin-top: 5px; border-radius: 50%; background: var(--lv); }
.levels__item--current strong { color: var(--eh-primary); }
.levels__item--current strong::after { content: ' · trình độ của bạn'; font-size: 12px; font-weight: 500; }

@media (max-width: 1023.98px) {
  .layout { grid-template-columns: 1fr; }
}
@media (max-width: 767.98px) {
  .hero { padding: 20px; }
  .hero__title { font-size: 22px; }
  .hero__actions .el-button { width: 100%; }
}
</style>
