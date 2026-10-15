<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Right, Upload } from '@element-plus/icons-vue'
import { dataApi, levelApi, pathApi } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { GOAL_OPTIONS, levelColor } from '@/utils/labels'

/**
 * Lần đầu mở ứng dụng: người học khai báo vài thông tin (không cần tài khoản/mật khẩu).
 * Dữ liệu lưu trong trình duyệt; có thể khôi phục từ file sao lưu JSON.
 */
const router = useRouter()
const route = useRoute()
const learner = useProfileStore()

const step = ref(0)
const saving = ref(false)
const levels = ref([])
const nameInput = ref(null)
const form = reactive({
  fullName: '', learningGoal: 'COMMUNICATION', levelChoice: 'placement', dailyGoalMinutes: 15,
  reminderEnabled: true, reminderTime: '20:00',
})
const errors = reactive({})

const MINUTES = [10, 15, 30, 45, 60]
const canNext = computed(() => form.fullName.trim().length > 0)

function next() {
  errors.fullName = canNext.value ? '' : 'Bạn tên là gì nhỉ?'
  if (canNext.value) step.value = 1
}

async function finish() {
  saving.value = true
  try {
    const levelId = form.levelChoice === 'placement' ? null : Number(form.levelChoice)
    await learner.create({
      fullName: form.fullName, learningGoal: form.learningGoal, dailyGoalMinutes: form.dailyGoalMinutes, levelId,
      reminderEnabled: form.reminderEnabled, reminderTime: form.reminderEnabled ? form.reminderTime : null,
    })
    if (levelId) {
      // Tự chọn trình độ -> tạo lộ trình ngay; muốn chính xác hơn thì làm bài xếp lớp sau
      await pathApi.generate({}).catch(() => {})
      ElMessage.success('Đã tạo hồ sơ và lộ trình học. Chúc bạn học vui!')
      router.replace(route.query.redirect || { name: 'dashboard' })
    } else {
      ElMessage.success('Đã tạo hồ sơ. Làm bài kiểm tra xếp lớp để nhận lộ trình phù hợp nhé!')
      router.replace({ name: 'placement' })
    }
  } catch (e) {
    Object.assign(errors, e.fieldErrors ?? {})
    if (errors.fullName) step.value = 0
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function restore(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    await dataApi.importBackup(JSON.parse(await file.text()))
    learner.reset()
    await learner.load()
    if (!learner.hasProfile) throw new Error('File sao lưu không có hồ sơ người học')
    ElMessage.success(`Chào mừng trở lại, ${learner.user.fullName}!`)
    router.replace({ name: 'dashboard' })
  } catch (e) {
    ElMessage.error(e instanceof SyntaxError ? 'File không phải JSON hợp lệ' : e.message)
  }
}

onMounted(async () => {
  levels.value = await levelApi.list()
  nameInput.value?.focus()
})
</script>

<template>
  <div class="welcome">
    <header class="welcome__head">
      <h2>{{ step === 0 ? 'Chào mừng bạn đến với EnglishHub 👋' : `Rất vui được gặp ${form.fullName.trim().split(/\s+/).at(-1)}!` }}</h2>
      <p class="eh-muted">
        {{ step === 0 ? 'Không cần tài khoản. Cho chúng mình biết đôi chút để cá nhân hóa việc học.' : 'Một chút nữa thôi để lên lộ trình phù hợp với bạn.' }}
      </p>
    </header>

    <el-steps :active="step" finish-status="success" simple class="welcome__steps">
      <el-step title="Bạn là ai" />
      <el-step title="Cách học" />
    </el-steps>

    <!-- Bước 1: tên + mục tiêu -->
    <form v-if="step === 0" class="welcome__form" @submit.prevent="next">
      <label class="field">
        <span class="field__label">Tên của bạn</span>
        <el-input ref="nameInput" v-model="form.fullName" size="large" maxlength="150" placeholder="vd: Nguyễn Minh Anh"
                  autocomplete="name" @input="errors.fullName = ''" />
        <span v-if="errors.fullName" class="field__error">{{ errors.fullName }}</span>
      </label>

      <div class="field">
        <span class="field__label">Bạn học tiếng Anh để…</span>
        <div class="goals" role="radiogroup" aria-label="Mục tiêu học">
          <button v-for="g in GOAL_OPTIONS" :key="g.value" type="button" role="radio" :aria-checked="form.learningGoal === g.value"
                  class="goal" :class="{ 'goal--active': form.learningGoal === g.value }" @click="form.learningGoal = g.value">
            <strong>{{ g.label }}</strong>
            <span>{{ g.description }}</span>
          </button>
        </div>
      </div>

      <el-button type="primary" size="large" native-type="submit" class="welcome__cta" :disabled="!canNext">
        Tiếp tục <el-icon class="el-icon--right"><Right /></el-icon>
      </el-button>
    </form>

    <!-- Bước 2: trình độ + thời gian -->
    <form v-else class="welcome__form" @submit.prevent="finish">
      <div class="field">
        <span class="field__label">Trình độ hiện tại</span>
        <el-radio-group v-model="form.levelChoice" class="levels">
          <el-radio value="placement" border class="level level--test">
            <strong>Chưa rõ - làm bài kiểm tra xếp lớp (khuyên dùng)</strong>
            <span>20 câu, khoảng 20 phút, xếp đúng trình độ và tìm kỹ năng yếu</span>
          </el-radio>
          <el-radio v-for="lv in levels" :key="lv.id" :value="String(lv.id)" border class="level">
            <strong><span class="dot" :style="{ background: levelColor(lv.code) }" /> {{ lv.name }} <small>{{ lv.cefrRange }}</small></strong>
            <span>{{ lv.description }}</span>
          </el-radio>
        </el-radio-group>
      </div>

      <div class="field">
        <span class="field__label">Mỗi ngày bạn dành bao nhiêu phút?</span>
        <el-radio-group v-model="form.dailyGoalMinutes" size="large">
          <el-radio-button v-for="m in MINUTES" :key="m" :value="m">{{ m }} phút</el-radio-button>
        </el-radio-group>
      </div>

      <div class="field field--inline">
        <el-switch v-model="form.reminderEnabled" active-text="Nhắc tôi học mỗi ngày lúc" />
        <el-time-select v-model="form.reminderTime" :disabled="!form.reminderEnabled" start="06:00" step="00:30" end="23:00"
                        :clearable="false" style="width: 120px" />
      </div>
      <p class="eh-subtle hint">Thông báo hiển thị trong ứng dụng khi bạn mở EnglishHub sau giờ nhắc mà chưa đạt mục tiêu ngày.</p>

      <div class="welcome__actions">
        <el-button size="large" :icon="ArrowLeft" @click="step = 0">Quay lại</el-button>
        <el-button type="primary" size="large" native-type="submit" :loading="saving" class="welcome__cta">
          {{ form.levelChoice === 'placement' ? 'Bắt đầu & làm bài xếp lớp' : 'Bắt đầu học' }}
        </el-button>
      </div>
    </form>

    <div class="welcome__restore">
      <span class="eh-subtle">Đã từng học trên máy khác?</span>
      <label class="el-button is-link restore-btn">
        <el-icon><Upload /></el-icon> Khôi phục từ file sao lưu
        <input type="file" accept="application/json,.json" hidden @change="restore">
      </label>
    </div>
  </div>
</template>

<style scoped>
.welcome { display: grid; gap: 20px; }
.welcome__head h2 { font-size: 26px; margin-bottom: 6px; }
.welcome__steps { margin: 0; }
.welcome__form { display: grid; gap: 20px; }
.field { display: grid; gap: 8px; }
.field--inline { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.field__label { font-weight: 600; }
.field__error { color: var(--eh-danger-text); font-size: 13px; }
.hint { margin-top: -12px; font-size: 13px; }

.goals { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.goal {
  display: grid; gap: 4px; padding: 14px 16px; text-align: left; font: inherit; cursor: pointer;
  border: 2px solid var(--eh-border); border-radius: 12px; background: var(--eh-surface); color: var(--eh-text);
  transition: border-color 0.15s, background-color 0.15s;
}
.goal span { font-size: 13px; color: var(--eh-text-2); }
.goal:hover { border-color: var(--el-color-primary-light-5); }
.goal--active { border-color: var(--eh-primary); background: var(--eh-primary-soft); }

.levels { display: grid; gap: 8px; width: 100%; }
.level { height: auto; margin: 0 !important; padding: 10px 14px; width: 100%; align-items: flex-start; }
.level :deep(.el-radio__label) { display: grid; gap: 2px; white-space: normal; line-height: 1.4; }
.level span { font-size: 13px; color: var(--eh-text-2); font-weight: 400; }
.level small { color: var(--eh-text-3); font-weight: 500; }
.level--test { border-style: dashed; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }

.welcome__actions { display: flex; gap: 10px; }
.welcome__cta { flex: 1; }
.welcome__restore { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 6px; font-size: 14px; }
.restore-btn { display: inline-flex; align-items: center; gap: 4px; color: var(--eh-primary); cursor: pointer; }

@media (max-width: 479.98px) {
  .goals { grid-template-columns: 1fr; }
  .welcome__head h2 { font-size: 22px; }
}
</style>
