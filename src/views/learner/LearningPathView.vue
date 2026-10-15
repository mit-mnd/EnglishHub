<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown, ArrowUp, Calendar, CircleCheckFilled, Delete, EditPen, MoreFilled, Plus, Refresh, Search, Setting, VideoPlay,
} from '@element-plus/icons-vue'
import LessonRow from '@/components/lesson/LessonRow.vue'
import { catalogApi, levelApi, pathApi } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { useDashboardStore } from '@/stores/dashboard'
import { formatDate, formatMinutes, localIsoDate } from '@/utils/format'
import { GOAL_LABELS, GOAL_OPTIONS, levelColor } from '@/utils/labels'
import { SKILL_META } from '@/utils/skills'

const router = useRouter()
const learner = useProfileStore()
const dashboard = useDashboardStore()

const path = ref(null)
const loading = ref(true)
const busy = ref(false)
const levels = ref([])

const STATUS = {
  COMPLETED: { label: 'Đã xong', type: 'success' },
  SKIPPED: { label: 'Bỏ qua', type: 'info' },
  IN_PROGRESS: { label: 'Đang học', type: 'warning' },
  AVAILABLE: { label: 'Học tiếp', type: 'primary' },
  LOCKED: { label: 'Sắp tới', type: 'info' },
}

const isFinished = computed(() => path.value && path.value.items.length && path.value.items.every((i) => ['COMPLETED', 'SKIPPED'].includes(i.status)))
const currentItem = computed(() => path.value?.items.find((i) => i.id === path.value.currentItemId))

/** Nhóm bài chưa xong theo ngày dự kiến; bài đã xong gom riêng. */
const groups = computed(() => {
  if (!path.value) return []
  const now = new Date()
  const today = localIsoDate(now)
  const tomorrow = localIsoDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1))
  const map = new Map()
  for (const item of path.value.items) {
    const done = ['COMPLETED', 'SKIPPED'].includes(item.status)
    const key = done ? 'done' : (item.plannedDate ?? 'unscheduled')
    if (!map.has(key)) {
      const label = done ? 'Đã hoàn thành'
        : key === 'unscheduled' ? 'Các bài tiếp theo'
          : key === today ? 'Hôm nay' : key === tomorrow ? 'Ngày mai' : formatDate(key)
      map.set(key, { key, label, done, items: [] })
    }
    map.get(key).items.push(item)
  }
  const list = [...map.values()]
  return [...list.filter((g) => !g.done), ...list.filter((g) => g.done)]
})

async function load() {
  loading.value = true
  try {
    path.value = await pathApi.get()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function run(action, successMessage) {
  busy.value = true
  try {
    path.value = await action()
    if (successMessage) ElMessage.success(successMessage)
    dashboard.load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

// ---------- Thao tác trên từng bài ----------
function move(item, delta) {
  const ids = path.value.items.map((i) => i.id)
  const from = ids.indexOf(item.id)
  const to = from + delta
  if (to < 0 || to >= ids.length) return
  ;[ids[from], ids[to]] = [ids[to], ids[from]]
  run(() => pathApi.reorder(ids))
}

async function onItemCommand(command, item) {
  if (command === 'open') return router.push({ name: 'lesson', params: { lessonId: item.lesson.id } })
  if (command === 'skip') return run(() => pathApi.skip(item.id), 'Đã bỏ qua bài học')
  if (command === 'unskip') return run(() => pathApi.unskip(item.id))
  if (command === 'up') return move(item, -1)
  if (command === 'down') return move(item, 1)
  if (command === 'remove') {
    try {
      await ElMessageBox.confirm(`Xóa "${item.lesson.title}" khỏi lộ trình?`, 'Xóa bài học',
        { type: 'warning', confirmButtonText: 'Xóa', cancelButtonText: 'Hủy' })
    } catch {
      return
    }
    return run(() => pathApi.removeItem(item.id), 'Đã xóa khỏi lộ trình')
  }
}

// ---------- Tạo / điều chỉnh lộ trình ----------
const setup = reactive({ open: false, goalType: 'GENERAL', levelId: null, dailyMinutes: 15, usePlacement: true })

async function openSetup() {
  if (!levels.value.length) levels.value = await levelApi.list().catch(() => [])
  Object.assign(setup, {
    open: true,
    goalType: path.value?.goalType ?? learner.user?.learningGoal ?? 'GENERAL',
    levelId: path.value?.level?.id ?? learner.user?.currentLevel?.id ?? levels.value[0]?.id,
    dailyMinutes: path.value?.dailyMinutes ?? learner.user?.dailyGoalMinutes ?? 15,
    usePlacement: !!learner.user?.placementCompletedAt,
  })
}

async function generate() {
  if (path.value) {
    try {
      await ElMessageBox.confirm('Lộ trình hiện tại sẽ được lưu trữ và thay bằng lộ trình mới. Tiến độ các bài đã học vẫn được giữ.',
        'Tạo lại lộ trình?', { type: 'warning', confirmButtonText: 'Tạo lộ trình mới', cancelButtonText: 'Hủy' })
    } catch {
      return
    }
  }
  setup.open = false
  await run(() => pathApi.generate({
    goalType: setup.goalType,
    // Dùng kết quả xếp lớp => không gửi levelId để backend lấy level + kỹ năng yếu từ bài xếp lớp
    levelId: setup.usePlacement ? undefined : setup.levelId,
    dailyMinutes: setup.dailyMinutes,
  }), 'Đã tạo lộ trình mới')
  learner.refresh().catch(() => {})
}

// ---------- Thêm bài ----------
const picker = reactive({ open: false, keyword: '', skill: '', loading: false, items: [] })
let searchTimer = null

async function searchLessons() {
  picker.loading = true
  try {
    const res = await catalogApi.searchLessons({ keyword: picker.keyword || undefined, skill: picker.skill || undefined, size: 30 })
    const inPath = new Set(path.value.items.map((i) => i.lesson.id))
    picker.items = res.items.map((l) => ({ ...l, inPath: inPath.has(l.id) }))
  } finally {
    picker.loading = false
  }
}

function openPicker() {
  picker.open = true
  searchLessons()
}

function onPickerInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(searchLessons, 300)
}

async function addLesson(lesson) {
  await run(() => pathApi.addLesson(lesson.id), `Đã thêm "${lesson.title}"`)
  lesson.inPath = true
}

onMounted(load)
</script>

<template>
  <div class="eh-page">
    <el-skeleton v-if="loading" :rows="10" animated />

    <!-- Chưa có lộ trình -->
    <section v-else-if="!path" class="eh-card empty">
      <div class="empty__art" aria-hidden="true">🧭</div>
      <h2>Bạn chưa có lộ trình học</h2>
      <p class="eh-muted">Làm bài kiểm tra xếp lớp để lộ trình dựa trên trình độ thật và kỹ năng còn yếu của bạn,
        hoặc tự chọn trình độ và mục tiêu.</p>
      <div class="empty__actions">
        <el-button type="primary" size="large" :icon="EditPen" @click="router.push({ name: 'placement' })">Làm bài xếp lớp</el-button>
        <el-button size="large" :icon="Setting" @click="openSetup">Tự chọn mục tiêu</el-button>
      </div>
    </section>

    <template v-else>
      <section class="hero" :style="{ '--lv': levelColor(path.level.code) }">
        <div class="hero__info">
          <span class="hero__eyebrow">{{ path.source === 'PLACEMENT' ? 'Theo kết quả xếp lớp' : 'Theo mục tiêu bạn chọn' }}</span>
          <h2>{{ path.name }}</h2>
          <div class="hero__chips">
            <span>{{ GOAL_LABELS[path.goalType] }}</span>
            <span>{{ path.level.name }}</span>
            <span>{{ path.dailyMinutes }} phút/ngày</span>
          </div>
        </div>
        <div class="hero__progress">
          <el-progress type="circle" :percentage="Number(path.percent)" :width="104" :stroke-width="10" color="#fff">
            <template #default><span class="hero__pct">{{ Math.round(path.percent) }}%</span></template>
          </el-progress>
          <dl>
            <div><dt>Đã xong</dt><dd>{{ path.completedItems + path.skippedItems }}/{{ path.totalItems }} bài</dd></div>
            <div><dt>Còn lại</dt><dd>{{ formatMinutes(path.remainingMinutes) }}</dd></div>
            <div v-if="path.estimatedFinishDate"><dt>Dự kiến xong</dt><dd>{{ formatDate(path.estimatedFinishDate) }}</dd></div>
          </dl>
        </div>
      </section>

      <div class="toolbar">
        <el-button v-if="currentItem" type="primary" :icon="VideoPlay"
                   @click="router.push({ name: 'lesson', params: { lessonId: currentItem.lesson.id } })">
          {{ currentItem.status === 'IN_PROGRESS' ? 'Học tiếp' : 'Bắt đầu' }}: {{ currentItem.lesson.title }}
        </el-button>
        <span class="spacer" />
        <el-button :icon="Plus" @click="openPicker">Thêm bài</el-button>
        <el-button :icon="Setting" @click="openSetup">Điều chỉnh lộ trình</el-button>
      </div>

      <section v-if="isFinished" class="eh-card finished">
        <span class="finished__icon">🏆</span>
        <div>
          <strong>Chúc mừng! Bạn đã hoàn thành lộ trình này.</strong>
          <p class="eh-muted">Làm lại bài xếp lớp để đo tiến bộ, hoặc tạo lộ trình mới ở trình độ cao hơn.</p>
        </div>
        <el-button type="primary" :icon="Refresh" @click="openSetup">Tạo lộ trình mới</el-button>
      </section>

      <div v-loading="busy" class="groups">
        <section v-for="group in groups" :key="group.key" class="group">
          <h3 class="group__title">
            <el-icon v-if="group.done" class="ok"><CircleCheckFilled /></el-icon>
            <el-icon v-else><Calendar /></el-icon>
            {{ group.label }}
            <span class="eh-subtle">· {{ group.items.length }} bài · {{ formatMinutes(group.items.reduce((s, i) => s + i.lesson.estimatedMinutes, 0)) }}</span>
          </h3>
          <div class="eh-card group__list">
            <div v-for="item in group.items" :key="item.id" class="item"
                 :class="{ 'item--current': item.id === path.currentItemId, 'item--muted': item.status === 'SKIPPED' }">
              <LessonRow :lesson="item.lesson" :index="item.sortOrder" :active="item.id === path.currentItemId" class="item__row">
                <template #status>
                  <el-tag v-if="item.custom" size="small" effect="plain" class="item__tag">Tự thêm</el-tag>
                  <el-tag :type="STATUS[item.status].type" size="small" :effect="item.status === 'AVAILABLE' ? 'dark' : 'light'">
                    {{ STATUS[item.status].label }}
                  </el-tag>
                </template>
              </LessonRow>
              <el-dropdown trigger="click" @command="(c) => onItemCommand(c, item)">
                <el-button circle text :icon="MoreFilled" :aria-label="`Tùy chọn cho ${item.lesson.title}`" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="open" :icon="VideoPlay">Mở bài học</el-dropdown-item>
                    <el-dropdown-item v-if="item.status === 'SKIPPED'" command="unskip" :icon="Refresh">Học lại bài này</el-dropdown-item>
                    <el-dropdown-item v-else-if="item.status !== 'COMPLETED'" command="skip" :icon="CircleCheckFilled">Bỏ qua (đã biết)</el-dropdown-item>
                    <el-dropdown-item command="up" :icon="ArrowUp" :disabled="item.sortOrder === 1">Chuyển lên</el-dropdown-item>
                    <el-dropdown-item command="down" :icon="ArrowDown" :disabled="item.sortOrder === path.items.length">Chuyển xuống</el-dropdown-item>
                    <el-dropdown-item command="remove" :icon="Delete" divided>Xóa khỏi lộ trình</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </section>
      </div>
    </template>

    <!-- Dialog tạo / điều chỉnh -->
    <el-dialog v-model="setup.open" :title="path ? 'Điều chỉnh lộ trình' : 'Tạo lộ trình học'" width="520px">
      <el-form label-position="top">
        <el-form-item label="Mục tiêu học">
          <el-radio-group v-model="setup.goalType">
            <el-radio-button v-for="g in GOAL_OPTIONS" :key="g.value" :value="g.value">{{ g.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Trình độ">
          <el-switch v-if="learner.user?.placementCompletedAt" v-model="setup.usePlacement"
                     active-text="Theo kết quả xếp lớp (ưu tiên kỹ năng yếu)" class="placement-switch" />
          <el-select v-if="!setup.usePlacement || !learner.user?.placementCompletedAt" v-model="setup.levelId">
            <el-option v-for="lv in levels" :key="lv.id" :label="`${lv.name} (${lv.cefrRange})`" :value="lv.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="`Thời gian học mỗi ngày: ${setup.dailyMinutes} phút`">
          <el-slider v-model="setup.dailyMinutes" :min="5" :max="120" :step="5" :marks="{ 15: '15', 30: '30', 60: '60' }" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="setup.open = false">Hủy</el-button>
        <el-button type="primary" :loading="busy" @click="generate">{{ path ? 'Tạo lại lộ trình' : 'Tạo lộ trình' }}</el-button>
      </template>
    </el-dialog>

    <!-- Dialog thêm bài -->
    <el-dialog v-model="picker.open" title="Thêm bài học vào lộ trình" width="640px">
      <div class="picker-filters">
        <el-input v-model="picker.keyword" :prefix-icon="Search" placeholder="Tìm theo tên bài" clearable @input="onPickerInput" />
        <el-select v-model="picker.skill" placeholder="Kỹ năng" clearable @change="searchLessons">
          <el-option v-for="(m, key) in SKILL_META" :key="key" :label="m.label" :value="key" />
        </el-select>
      </div>
      <div v-loading="picker.loading" class="picker-list">
        <el-empty v-if="!picker.items.length && !picker.loading" description="Không có bài học phù hợp" :image-size="60" />
        <div v-for="lesson in picker.items" :key="lesson.id" class="picker-item">
          <LessonRow :lesson="lesson" show-course class="item__row" />
          <el-button v-if="!lesson.inPath" size="small" type="primary" plain :icon="Plus" @click="addLesson(lesson)">Thêm</el-button>
          <el-tag v-else size="small" type="success">Đã có</el-tag>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.empty { max-width: 640px; margin: 24px auto; text-align: center; padding: 40px 28px; display: grid; gap: 12px; justify-items: center; }
.empty__art { font-size: 56px; }
.empty h2 { font-size: 22px; }
.empty__actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; margin-top: 8px; }
.empty__actions .el-button + .el-button { margin-left: 0; }

.hero {
  display: flex; align-items: center; justify-content: space-between; gap: 24px; flex-wrap: wrap;
  padding: 28px; border-radius: 20px; color: #fff; margin-bottom: 16px;
  background: radial-gradient(circle at 90% 0%, rgba(255, 255, 255, 0.22), transparent 45%),
    linear-gradient(135deg, var(--eh-primary), color-mix(in srgb, var(--lv) 55%, var(--eh-primary)));
}
.hero__eyebrow { font-size: 13px; opacity: 0.85; text-transform: uppercase; letter-spacing: 0.6px; }
.hero h2 { font-size: 26px; margin: 4px 0 12px; }
.hero__chips { display: flex; gap: 8px; flex-wrap: wrap; }
.hero__chips span { padding: 4px 12px; border-radius: 999px; background: rgba(255, 255, 255, 0.18); font-size: 13px; font-weight: 600; }
.hero__progress { display: flex; align-items: center; gap: 20px; }
.hero__progress :deep(.el-progress-circle__track) { stroke: rgba(255, 255, 255, 0.25); }
.hero__pct { color: #fff; font-size: 24px; font-weight: 700; }
.hero dl { margin: 0; display: grid; gap: 6px; font-size: 14px; }
.hero dl div { display: flex; gap: 10px; justify-content: space-between; }
.hero dt { opacity: 0.8; }
.hero dd { margin: 0; font-weight: 600; }

.toolbar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.toolbar .el-button + .el-button { margin-left: 0; }
.spacer { flex: 1; }

.finished { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; background: var(--eh-accent-soft); border-color: var(--eh-accent-border); }
.finished__icon { font-size: 36px; }
.finished > div { flex: 1; min-width: 200px; }

.groups { display: grid; gap: 20px; }
.group__title { display: flex; align-items: center; gap: 8px; font-size: 16px; margin-bottom: 10px; }
.group__list { padding: 6px; display: grid; gap: 2px; }
.item { display: flex; align-items: center; gap: 4px; padding-right: 6px; border-radius: 12px; }
.item__row { flex: 1; min-width: 0; }
.item__tag { margin-right: 6px; }
.item--muted { opacity: 0.6; }
.ok { color: var(--eh-success); }

.placement-switch { margin-bottom: 8px; width: 100%; }
.picker-filters { display: flex; gap: 10px; margin-bottom: 12px; }
.picker-filters .el-select { width: 160px; }
.picker-list { max-height: 55vh; overflow-y: auto; min-height: 120px; }
.picker-item { display: flex; align-items: center; gap: 8px; padding-right: 6px; }

@media (max-width: 767.98px) {
  .hero { padding: 20px; }
  .hero h2 { font-size: 21px; }
  .toolbar .el-button { flex: 1; }
  .picker-filters { flex-direction: column; }
  .picker-filters .el-select { width: 100%; }
}
</style>
