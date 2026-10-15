<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Clock, Search } from '@element-plus/icons-vue'
import LessonRow from '@/components/lesson/LessonRow.vue'
import { catalogApi, levelApi } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { GOAL_LABELS, GOAL_OPTIONS, levelColor } from '@/utils/labels'
import { SKILL_META, skillMeta } from '@/utils/skills'

const learner = useProfileStore()

const levels = ref([])
const courses = ref([])
const loading = ref(true)
const levelId = ref(null)       // null = tất cả
const goal = ref('')

const keyword = ref('')
const skill = ref('')
const searchResult = ref(null)
const searching = ref(false)
const isSearching = computed(() => !!keyword.value.trim() || !!skill.value)

async function loadCourses() {
  loading.value = true
  try {
    courses.value = await catalogApi.courses({ levelId: levelId.value ?? undefined, goal: goal.value || undefined })
  } finally {
    loading.value = false
  }
}

let timer = null
function onSearchInput() {
  clearTimeout(timer)
  timer = setTimeout(search, 300)
}

async function search() {
  if (!isSearching.value) {
    searchResult.value = null
    return
  }
  searching.value = true
  try {
    searchResult.value = await catalogApi.searchLessons({
      keyword: keyword.value.trim() || undefined, skill: skill.value || undefined, levelId: levelId.value ?? undefined, size: 50,
    })
  } finally {
    searching.value = false
  }
}

watch([levelId, goal], () => {
  loadCourses()
  if (isSearching.value) search()
})

onMounted(async () => {
  levels.value = await levelApi.list().catch(() => [])
  levelId.value = learner.user?.currentLevel?.id ?? null   // mặc định: đúng trình độ của học viên
  if (levelId.value == null) loadCourses()
})
</script>

<template>
  <div class="eh-page">
    <header class="eh-page__header">
      <h2 class="eh-page__title">Thư viện bài học</h2>
      <p class="eh-page__subtitle">Chọn khóa học phù hợp hoặc tìm nhanh một bài theo kỹ năng.</p>
    </header>

    <section class="filters eh-card">
      <div class="levels" role="tablist" aria-label="Lọc theo trình độ">
        <button type="button" class="lv" :class="{ 'lv--active': levelId == null }" role="tab"
                :aria-selected="levelId == null" @click="levelId = null">Tất cả</button>
        <button v-for="lv in levels" :key="lv.id" type="button" class="lv" role="tab" :aria-selected="levelId === lv.id"
                :class="{ 'lv--active': levelId === lv.id }" :style="{ '--lv': levelColor(lv.code) }" @click="levelId = lv.id">
          {{ lv.name }}
        </button>
      </div>
      <div class="search-row">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="Tìm bài học..." clearable @input="onSearchInput" @clear="search" />
        <el-select v-model="skill" placeholder="Kỹ năng" clearable @change="search">
          <el-option v-for="(m, key) in SKILL_META" :key="key" :label="m.label" :value="key" />
        </el-select>
        <el-select v-model="goal" placeholder="Mục tiêu" clearable>
          <el-option v-for="g in GOAL_OPTIONS" :key="g.value" :label="g.label" :value="g.value" />
        </el-select>
      </div>
    </section>

    <!-- Kết quả tìm kiếm bài học -->
    <section v-if="isSearching" v-loading="searching" class="eh-card results">
      <div class="eh-card__title">Kết quả ({{ searchResult?.totalElements ?? 0 }})</div>
      <el-empty v-if="searchResult && !searchResult.items.length" description="Không tìm thấy bài học phù hợp" :image-size="70" />
      <LessonRow v-for="lesson in searchResult?.items ?? []" :key="lesson.id" :lesson="lesson" show-course />
    </section>

    <!-- Danh sách khóa học -->
    <template v-else>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="!courses.length" description="Chưa có khóa học ở trình độ này" />
      <div v-else class="grid">
        <RouterLink v-for="c in courses" :key="c.id" :to="{ name: 'course', params: { courseId: c.id } }"
                    class="course eh-card" :style="{ '--lv': levelColor(c.level.code) }">
          <div class="course__band" />
          <div class="course__top">
            <span class="course__level">{{ c.level.name }}</span>
            <span v-if="c.goalType !== 'GENERAL'" class="course__goal">{{ GOAL_LABELS[c.goalType] }}</span>
          </div>
          <h3>{{ c.title }}</h3>
          <p class="course__desc">{{ c.description }}</p>
          <div class="course__skills">
            <span v-for="s in c.skills" :key="s" class="skill-dot" :title="skillMeta(s).label"
                  :style="{ background: skillMeta(s).soft, color: skillMeta(s).color }">
              <el-icon><component :is="skillMeta(s).icon" /></el-icon>
            </span>
          </div>
          <div class="course__foot">
            <span>{{ c.lessonCount }} bài · <el-icon><Clock /></el-icon> {{ c.totalMinutes }} phút</span>
            <span v-if="c.completedLessons" class="course__done">{{ c.completedLessons }}/{{ c.lessonCount }}</span>
          </div>
          <el-progress v-if="c.completedLessons" :percentage="Math.round(c.completedLessons / c.lessonCount * 100)"
                       :show-text="false" :stroke-width="6" />
        </RouterLink>
      </div>
    </template>
  </div>
</template>

<style scoped>
.filters { display: grid; gap: 14px; margin-bottom: 20px; }
.levels { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px; }
.lv {
  flex-shrink: 0; padding: 7px 14px; border-radius: 999px; border: 1.5px solid var(--eh-border); background: var(--eh-surface);
  font: inherit; font-size: 14px; font-weight: 500; color: var(--eh-text-2); cursor: pointer;
}
.lv:hover { border-color: var(--lv, var(--eh-primary)); }
.lv--active { background: var(--lv, var(--eh-primary)); border-color: var(--lv, var(--eh-primary)); color: #fff; }
.search-row { display: flex; gap: 10px; }
.search-row .el-select { width: 160px; flex-shrink: 0; }
.results { display: grid; gap: 2px; }

.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.course {
  position: relative; overflow: hidden; display: flex; flex-direction: column; gap: 10px; padding-top: 24px;
  color: var(--eh-text); text-decoration: none; transition: transform 0.15s, box-shadow 0.15s;
}
.course:hover { transform: translateY(-2px); box-shadow: 0 10px 28px rgba(16, 24, 40, 0.1); text-decoration: none; }
.course__band { position: absolute; inset: 0 0 auto 0; height: 6px; background: var(--lv); }
.course__top { display: flex; gap: 6px; }
.course__level, .course__goal { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 6px; }
.course__level { background: color-mix(in srgb, var(--lv) 15%, white); color: color-mix(in srgb, var(--lv) 80%, black); }
.course__goal { background: var(--eh-accent-soft); color: #b45309; }
.course h3 { font-size: 17px; }
.course__desc { font-size: 14px; color: var(--eh-text-2); flex: 1; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.course__skills { display: flex; gap: 6px; }
.skill-dot { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 8px; font-size: 14px; }
.course__foot { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--eh-text-2); }
.course__foot span { display: inline-flex; align-items: center; gap: 4px; }
.course__done { font-weight: 600; color: var(--eh-success); }

@media (max-width: 767.98px) {
  .search-row { flex-wrap: wrap; }
  .search-row .el-input { flex-basis: 100%; }
  .search-row .el-select { flex: 1; width: auto; }
}
</style>
