<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Microphone, Star, StarFilled } from '@element-plus/icons-vue'
import { sentenceApi } from '@/api'
import { useSpeech } from '@/composables/useSpeech'
import { levelColor } from '@/utils/labels'

const CATEGORIES = [
  { value: 'all', label: 'Tất cả' },
  { value: 'daily', label: 'Đời sống' },
  { value: 'work', label: 'Công việc' },
  { value: 'toeic', label: 'TOEIC' },
  { value: 'saved', label: 'Đã lưu' },
]
const LEVEL_CODES = { 1: 'FOUNDATION', 2: 'ELEMENTARY', 3: 'INTERMEDIATE', 4: 'ADVANCED', 5: 'EXAM_PREP' }

const { speak } = useSpeech()
const topics = ref([])
const saved = ref([])
const category = ref('all')
const loading = ref(true)

const visible = computed(() => (category.value === 'all' ? topics.value : topics.value.filter((t) => t.category === category.value)))
const totals = computed(() => ({
  patterns: topics.value.reduce((s, t) => s + t.patternCount, 0),
  practiced: topics.value.reduce((s, t) => s + t.practicedCount, 0),
}))

async function unsave(p) {
  await sentenceApi.setSaved(p.id, false)
  saved.value = saved.value.filter((x) => x.id !== p.id)
  topics.value.find((t) => t.id === p.topicId).savedCount--
}

onMounted(async () => {
  try {
    ;[topics.value, saved.value] = await Promise.all([sentenceApi.topics(), sentenceApi.saved()])
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="eh-page sentences">
    <section class="intro">
      <div>
        <h2>Mẫu câu theo tình huống</h2>
        <p class="eh-muted">{{ totals.patterns }} mẫu câu thông dụng cho đời sống, công việc và TOEIC Speaking/Writing - kèm ví dụ, bản dịch và mẹo dùng.</p>
      </div>
      <el-progress type="circle" :width="76" :percentage="totals.patterns ? Math.round((totals.practiced / totals.patterns) * 100) : 0">
        <template #default="{ percentage }"><span class="ring">{{ percentage }}%<small>đã luyện</small></span></template>
      </el-progress>
    </section>

    <el-radio-group v-model="category" class="filters">
      <el-radio-button v-for="c in CATEGORIES" :key="c.value" :value="c.value">
        {{ c.label }}<template v-if="c.value === 'saved'"> ({{ saved.length }})</template>
      </el-radio-button>
    </el-radio-group>

    <!-- Mẫu câu đã lưu -->
    <section v-if="category === 'saved'" class="saved">
      <el-empty v-if="!saved.length" description="Bấm ☆ ở một mẫu câu để lưu lại ôn sau." />
      <article v-for="p in saved" :key="p.id" class="eh-card saved__item">
        <div class="saved__main">
          <strong class="saved__pattern">{{ p.pattern }}</strong>
          <span>{{ p.meaning }}</span>
          <span class="eh-subtle">{{ p.example }}</span>
          <RouterLink :to="{ name: 'sentence-topic', params: { topicId: p.topicId } }" class="saved__topic">{{ p.topicName }}</RouterLink>
        </div>
        <el-button :icon="Microphone" circle text aria-label="Nghe" @click="speak(p.example)" />
        <el-button :icon="StarFilled" circle text type="warning" aria-label="Bỏ lưu" @click="unsave(p)" />
      </article>
    </section>

    <!-- Chủ đề -->
    <section v-else class="topics">
      <RouterLink v-for="t in visible" :key="t.id" :to="{ name: 'sentence-topic', params: { topicId: t.id } }" class="topic">
        <div class="topic__head">
          <strong>{{ t.name }}</strong>
          <span class="topic__level" :style="{ '--lv': levelColor(LEVEL_CODES[t.levelId]) }">Lv {{ t.levelId }}</span>
        </div>
        <p class="eh-subtle">{{ t.description }}</p>
        <div class="topic__foot">
          <span>{{ t.patternCount }} mẫu câu</span>
          <span v-if="t.savedCount"><el-icon><Star /></el-icon> {{ t.savedCount }}</span>
        </div>
        <el-progress :percentage="Math.round((t.practicedCount / t.patternCount) * 100)" :show-text="false" :stroke-width="5" />
      </RouterLink>
    </section>
  </div>
</template>

<style scoped>
.sentences { display: grid; gap: 20px; }
.intro { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.intro h2 { margin-bottom: 4px; }
.ring { display: grid; font-weight: 700; font-size: 16px; line-height: 1.1; }
.ring small { font-size: 10px; font-weight: 500; color: var(--eh-text-3); }
.filters { flex-wrap: wrap; }
.topics { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
.topic {
  display: grid; gap: 8px; padding: 16px; border-radius: 14px; border: 1px solid var(--eh-border);
  background: var(--eh-surface); color: var(--eh-text); text-decoration: none; transition: transform 0.15s, box-shadow 0.15s;
}
.topic:hover { text-decoration: none; transform: translateY(-2px); box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08); }
.topic__head { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
.topic__level { flex-shrink: 0; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; color: #fff; background: var(--lv); }
.topic p { font-size: 13px; line-height: 1.5; min-height: 38px; }
.topic__foot { display: flex; justify-content: space-between; font-size: 13px; color: var(--eh-text-2); }
.topic__foot .el-icon { vertical-align: -2px; color: #f59e0b; }
.saved { display: grid; gap: 10px; }
.saved__item { display: flex; align-items: center; gap: 6px; }
.saved__main { flex: 1; min-width: 0; display: grid; gap: 2px; }
.saved__pattern { font-size: 16px; }
.saved__topic { font-size: 12px; }
@media (max-width: 599.98px) {
  .intro { align-items: flex-start; }
  .intro :deep(.el-progress) { display: none; }
}
</style>
