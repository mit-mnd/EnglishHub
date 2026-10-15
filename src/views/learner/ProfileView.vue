<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Monitor, Moon, Sunny, Upload } from '@element-plus/icons-vue'
import { dataApi } from '@/api'
import { useDashboardStore } from '@/stores/dashboard'
import { useNotificationStore } from '@/stores/notifications'
import { useProfileStore } from '@/stores/profile'
import { useTheme } from '@/composables/useTheme'
import { formatDate, localIsoDate } from '@/utils/format'
import { GOAL_OPTIONS } from '@/utils/labels'

const { mode: themeMode, setMode: setThemeMode } = useTheme()
const THEME_CHOICES = [
  { value: 'light', label: 'Sáng', icon: Sunny },
  { value: 'dark', label: 'Tối', icon: Moon },
  { value: 'system', label: 'Theo máy', icon: Monitor },
]
const learner = useProfileStore()
const dashboard = useDashboardStore()
const notifications = useNotificationStore()
const route = useRoute()
const router = useRouter()

const formRef = ref()
const form = reactive({})
const errors = reactive({})
const saving = ref(false)
const usage = ref(0)
const dataSection = ref(null)

const rules = { fullName: [{ required: true, message: 'Nhập tên của bạn', trigger: 'blur' }] }

function fill() {
  const u = learner.user
  Object.assign(form, {
    fullName: u.fullName, learningGoal: u.learningGoal, dailyGoalMinutes: u.dailyGoalMinutes,
    reminderEnabled: u.reminderEnabled, reminderTime: u.reminderTime ?? '20:00',
  })
}

async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  Object.keys(errors).forEach((k) => delete errors[k])
  try {
    await learner.update({ ...form, reminderTime: form.reminderEnabled ? form.reminderTime : null })
    dashboard.load()
    ElMessage.success('Đã lưu hồ sơ')
  } catch (e) {
    Object.assign(errors, e.fieldErrors)
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

// ------------------------------------------------------------------ dữ liệu trên máy

async function exportBackup() {
  const backup = await dataApi.exportBackup()
  const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = Object.assign(document.createElement('a'), { href: url, download: `englishhub-${localIsoDate()}.json` })
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  ElMessage.success('Đã tải file sao lưu')
}

async function afterDataChanged() {
  learner.reset()
  dashboard.reset()
  notifications.reset()
  await learner.load()
}

async function importBackup(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    await ElMessageBox.confirm('Dữ liệu hiện tại trên máy sẽ được thay bằng dữ liệu trong file sao lưu. Tiếp tục?', 'Khôi phục dữ liệu',
      { type: 'warning', confirmButtonText: 'Khôi phục', cancelButtonText: 'Hủy' })
  } catch {
    return
  }
  try {
    await dataApi.importBackup(JSON.parse(await file.text()))
    await afterDataChanged()
    if (!learner.hasProfile) return router.replace({ name: 'welcome' })
    fill()
    usage.value = await dataApi.usageBytes()
    notifications.startPolling()
    dashboard.load()
    ElMessage.success('Đã khôi phục dữ liệu')
  } catch (e) {
    ElMessage.error(e instanceof SyntaxError ? 'File không phải JSON hợp lệ' : e.message)
  }
}

async function resetAll() {
  try {
    await ElMessageBox.confirm('Toàn bộ hồ sơ, tiến độ, sổ từ và lịch sử luyện tập trên máy này sẽ bị xóa vĩnh viễn. '
      + 'Bạn nên tải file sao lưu trước. Xóa?', 'Xóa toàn bộ dữ liệu',
    { type: 'error', confirmButtonText: 'Xóa hết', cancelButtonText: 'Hủy', confirmButtonClass: 'el-button--danger' })
  } catch {
    return
  }
  notifications.stopPolling()
  await dataApi.reset()
  await afterDataChanged()
  router.replace({ name: 'welcome' })
}

onMounted(async () => {
  fill()
  usage.value = await dataApi.usageBytes()
  if (route.hash === '#data') {
    await nextTick()
    dataSection.value?.scrollIntoView({ behavior: 'smooth' })
  }
})
</script>

<template>
  <div class="eh-page">
    <div class="grid">
      <section class="eh-card">
        <div class="eh-card__title">Hồ sơ & mục tiêu học</div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="Tên của bạn" prop="fullName" :error="errors.fullName">
            <el-input v-model="form.fullName" maxlength="150" />
          </el-form-item>
          <el-form-item label="Mục tiêu học">
            <el-radio-group v-model="form.learningGoal">
              <el-radio-button v-for="g in GOAL_OPTIONS" :key="g.value" :value="g.value">{{ g.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="`Thời gian học mỗi ngày: ${form.dailyGoalMinutes} phút`" :error="errors.dailyGoalMinutes">
            <el-slider v-model="form.dailyGoalMinutes" :min="5" :max="120" :step="5" :marks="{ 15: '15', 30: '30', 60: '60', 120: '120' }" />
          </el-form-item>
          <el-form-item label="Nhắc học hằng ngày" :error="errors.reminderTime">
            <div class="reminder">
              <el-switch v-model="form.reminderEnabled" />
              <el-time-select v-model="form.reminderTime" :disabled="!form.reminderEnabled" start="06:00" step="00:30" end="23:00"
                              :clearable="false" style="width: 130px" />
            </div>
          </el-form-item>
          <p class="eh-subtle note">Nhắc học và cảnh báo mất chuỗi hiển thị ở chuông thông báo khi bạn mở EnglishHub.</p>
          <el-button type="primary" :loading="saving" @click="save">Lưu thay đổi</el-button>
        </el-form>
      </section>

      <div class="side">
        <section class="eh-card">
          <div class="eh-card__title">Giao diện</div>
          <div class="themes" role="radiogroup" aria-label="Giao diện">
            <button v-for="t in THEME_CHOICES" :key="t.value" type="button" role="radio" class="theme-card"
                    :class="{ 'theme-card--active': themeMode === t.value }" :aria-checked="themeMode === t.value"
                    @click="setThemeMode(t.value)">
              <span class="theme-card__preview" :class="`theme-card__preview--${t.value}`" aria-hidden="true">
                <i /><i /><i />
              </span>
              <span class="theme-card__label"><el-icon><component :is="t.icon" /></el-icon>{{ t.label }}</span>
            </button>
          </div>
        </section>

        <section class="eh-card">
          <div class="eh-card__title">Thông tin học tập</div>
          <dl class="info">
            <div><dt>Trình độ</dt><dd>{{ learner.user?.currentLevel?.name ?? 'Chưa xếp lớp' }}</dd></div>
            <div><dt>Bắt đầu học</dt><dd>{{ formatDate(learner.user?.createdAt) }}</dd></div>
            <div><dt>Xếp lớp gần nhất</dt><dd>{{ formatDate(learner.user?.placementCompletedAt) }}</dd></div>
          </dl>
          <RouterLink :to="{ name: 'placement' }"><el-button class="full" plain>Làm bài kiểm tra xếp lớp</el-button></RouterLink>
        </section>

        <section id="data" ref="dataSection" class="eh-card">
          <div class="eh-card__title">Dữ liệu của bạn</div>
          <p class="eh-subtle">
            Mọi dữ liệu học tập được lưu ngay trong trình duyệt này ({{ (usage / 1024).toFixed(1) }} KB), không gửi lên máy chủ nào.
            Hãy tải file sao lưu định kỳ - xóa dữ liệu trình duyệt hoặc đổi máy sẽ mất tiến độ nếu không có bản sao lưu.
          </p>
          <div class="data-actions">
            <el-button type="primary" :icon="Download" @click="exportBackup">Tải file sao lưu (.json)</el-button>
            <label class="el-button">
              <el-icon><Upload /></el-icon><span>Khôi phục từ file</span>
              <input type="file" accept="application/json,.json" hidden @change="importBackup">
            </label>
            <el-button type="danger" plain :icon="Delete" @click="resetAll">Xóa toàn bộ dữ liệu</el-button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 20px; align-items: start; }
.side { display: grid; gap: 20px; }
.reminder { display: flex; align-items: center; gap: 12px; }
.info { margin: 0 0 14px; display: grid; gap: 10px; }
.info div { display: flex; justify-content: space-between; gap: 12px; }
.info dt { color: var(--eh-text-2); }
.info dd { margin: 0; font-weight: 600; text-align: right; }
.note { margin: -6px 0 14px; font-size: 13px; }
.full { width: 100%; }
.data-actions { display: grid; gap: 8px; margin-top: 14px; }
.data-actions .el-button { margin: 0; width: 100%; }
.data-actions label.el-button { gap: 6px; }
:deep(.el-slider) { padding: 0 8px 18px; }
@media (max-width: 1023.98px) {
  .grid { grid-template-columns: 1fr; }
}

/* Chọn giao diện */
.themes { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.theme-card {
  display: grid; gap: 8px; padding: 8px; font: inherit; color: var(--eh-text); cursor: pointer;
  border: 2px solid var(--eh-border); border-radius: 14px; background: var(--eh-surface);
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.2s var(--eh-ease);
}
.theme-card:hover { transform: translateY(-2px); border-color: var(--el-color-primary-light-5); }
.theme-card--active { border-color: var(--eh-primary); box-shadow: 0 0 0 3px var(--eh-primary-ring); }
.theme-card__label { display: flex; align-items: center; justify-content: center; gap: 5px; font-size: 13px; font-weight: 600; padding-bottom: 2px; }
.theme-card__preview {
  display: grid; grid-template-columns: 1fr 2fr; grid-template-rows: 1fr 1fr; gap: 4px; height: 56px; padding: 6px;
  border-radius: 8px; border: 1px solid rgba(128, 128, 150, 0.25); background: #f4f5fb;
}
.theme-card__preview i { border-radius: 4px; background: #fff; }
.theme-card__preview i:first-child { grid-row: span 2; background: #e4e7f1; }
.theme-card__preview i:last-child { background: #c7caf8; }
.theme-card__preview--dark { background: #0d0f1f; }
.theme-card__preview--dark i { background: #1c2039; }
.theme-card__preview--dark i:first-child { background: #272c47; }
.theme-card__preview--dark i:last-child { background: #4a4f8c; }
.theme-card__preview--system { background: linear-gradient(105deg, #f4f5fb 50%, #0d0f1f 50%); }
.theme-card__preview--system i { background: rgba(255, 255, 255, 0.55); }
.theme-card__preview--system i:first-child { background: rgba(160, 165, 190, 0.5); }
.theme-card__preview--system i:last-child { background: rgba(110, 115, 200, 0.55); }
</style>
