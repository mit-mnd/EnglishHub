<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Headset, Hide, Search, VideoPlay, View } from '@element-plus/icons-vue'
import { vocabularyApi } from '@/api'
import { useSpeech } from '@/composables/useSpeech'
import { formatDate } from '@/utils/format'
import { PART_OF_SPEECH } from '@/utils/skills'

const { pronounce } = useSpeech()
const router = useRouter()

const STATUS = {
  NEW: { label: 'Từ mới', type: 'primary' },
  LEARNING: { label: 'Đang học', type: 'warning' },
  REVIEW: { label: 'Ôn tập', type: 'info' },
  MASTERED: { label: 'Đã thuộc', type: 'success' },
  SUSPENDED: { label: 'Tạm ẩn', type: 'info' },
}

const filters = reactive({ keyword: '', status: '' })
const page = reactive({ page: 1, size: 12 })
const result = ref({ items: [], totalElements: 0 })
const loading = ref(false)
const editing = reactive({ id: null, note: '' })

let timer = null
let seq = 0

async function load() {
  const current = ++seq
  loading.value = true
  try {
    const data = await vocabularyApi.list({
      page: page.page - 1, size: page.size, keyword: filters.keyword || undefined, status: filters.status || undefined,
    })
    if (current === seq) result.value = data
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    if (current === seq) loading.value = false
  }
}

function onKeyword() {
  clearTimeout(timer)
  timer = setTimeout(() => { page.page = 1; load() }, 300)
}

function startEdit(item) {
  editing.id = item.id
  editing.note = item.note ?? ''
}

async function saveNote(item) {
  try {
    const updated = await vocabularyApi.updateNote(item.id, editing.note)
    item.note = updated.note
    editing.id = null
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function toggleSuspend(item) {
  try {
    const updated = await vocabularyApi.suspend(item.id, item.status !== 'SUSPENDED')
    item.status = updated.status
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(`Xóa "${item.word.headword}" khỏi sổ từ?`, 'Xóa từ',
      { type: 'warning', confirmButtonText: 'Xóa', cancelButtonText: 'Hủy' })
  } catch {
    return
  }
  await vocabularyApi.remove(item.id)
  ElMessage.success('Đã xóa')
  load()
}

onMounted(load)
</script>

<template>
  <div class="eh-page">
    <header class="eh-page__header head">
      <div>
        <h2 class="eh-page__title">Sổ từ của tôi</h2>
        <p class="eh-page__subtitle">Từ bạn lưu khi đọc bài hoặc thêm từ bộ từ vựng.</p>
      </div>
      <div class="head__actions">
        <el-button @click="router.push({ name: 'vocabulary' })">Tổng quan từ vựng</el-button>
        <el-button type="primary" :icon="VideoPlay" @click="router.push({ name: 'flashcard-review' })">Ôn tập flashcard</el-button>
      </div>
    </header>

    <section class="eh-card toolbar">
      <el-input v-model="filters.keyword" :prefix-icon="Search" placeholder="Tìm từ, nghĩa, ghi chú..." clearable
                @input="onKeyword" @clear="load" />
      <el-select v-model="filters.status" placeholder="Trạng thái" clearable @change="page.page = 1; load()">
        <el-option v-for="(s, key) in STATUS" :key="key" :label="s.label" :value="key" />
      </el-select>
    </section>

    <div v-loading="loading" class="grid">
      <el-empty v-if="!loading && !result.items.length" class="empty"
                description="Chưa có từ nào. Mở một bài đọc và bấm vào từ để tra & lưu nhé!" />
      <article v-for="item in result.items" :key="item.id" class="eh-card word">
        <header class="word__head">
          <div>
            <h3>{{ item.word.headword }}</h3>
            <span class="eh-subtle">{{ PART_OF_SPEECH[item.word.partOfSpeech] }}
              <template v-if="item.word.phoneticUs || item.word.phoneticUk"> · {{ item.word.phoneticUs || item.word.phoneticUk }}</template>
            </span>
          </div>
          <el-button circle :icon="Headset" aria-label="Phát âm"
                     @click="pronounce(item.word.headword, item.word.audioUrlUs || item.word.audioUrlUk)" />
        </header>

        <p v-if="item.word.meaningVi" class="word__meaning">{{ item.word.meaningVi }}</p>
        <p v-else-if="item.word.definitionEn" class="word__def">{{ item.word.definitionEn }}</p>

        <blockquote v-if="item.contextSentence" class="word__context">
          “{{ item.contextSentence }}”
          <RouterLink v-if="item.sourceLessonId" :to="{ name: 'lesson', params: { lessonId: item.sourceLessonId } }">
            — {{ item.sourceLessonTitle }}
          </RouterLink>
        </blockquote>

        <div v-if="editing.id === item.id" class="word__edit">
          <el-input v-model="editing.note" type="textarea" :rows="2" maxlength="500" placeholder="Nghĩa riêng, mẹo nhớ..." />
          <div class="word__edit-actions">
            <el-button size="small" @click="editing.id = null">Hủy</el-button>
            <el-button size="small" type="primary" @click="saveNote(item)">Lưu</el-button>
          </div>
        </div>
        <p v-else-if="item.note" class="word__note">📝 {{ item.note }}</p>

        <footer class="word__foot">
          <div class="word__meta">
            <el-tag :type="STATUS[item.status].type" size="small">{{ STATUS[item.status].label }}</el-tag>
            <span class="eh-subtle">{{ formatDate(item.createdAt) }}</span>
          </div>
          <div class="word__actions">
          <el-tooltip content="Ghi chú">
            <el-button text size="small" :icon="EditPen" aria-label="Ghi chú" @click="startEdit(item)" />
          </el-tooltip>
          <el-tooltip :content="item.status === 'SUSPENDED' ? 'Đưa lại vào lịch ôn' : 'Tạm ẩn khỏi lịch ôn (đã biết rõ)'">
            <el-button text size="small" :icon="item.status === 'SUSPENDED' ? View : Hide"
                       :aria-label="item.status === 'SUSPENDED' ? 'Bỏ tạm ẩn' : 'Tạm ẩn'" @click="toggleSuspend(item)" />
          </el-tooltip>
          <el-button text size="small" type="danger" :icon="Delete" aria-label="Xóa" @click="remove(item)" />
          </div>
        </footer>
      </article>
    </div>

    <el-pagination v-if="result.totalElements > page.size" v-model:current-page="page.page" class="pagination"
                   :page-size="page.size" :total="result.totalElements" layout="prev, pager, next" background @current-change="load" />
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
.head__actions { display: flex; gap: 8px; flex-wrap: wrap; }
.head__actions .el-button + .el-button { margin-left: 0; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.toolbar .el-select { width: 170px; flex-shrink: 0; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; min-height: 120px; }
.empty { grid-column: 1 / -1; }
.word { display: flex; flex-direction: column; gap: 8px; }
.word__head { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.word__head h3 { font-size: 20px; }
.word__meaning { font-weight: 600; color: var(--eh-primary); }
.word__def { font-size: 14px; color: var(--eh-text-2); }
.word__context { margin: 0; padding: 8px 10px; border-radius: 8px; background: var(--eh-bg); font-size: 14px; font-style: italic; }
.word__context a { font-style: normal; font-size: 13px; }
.word__note { font-size: 14px; padding: 6px 10px; border-radius: 8px; background: var(--eh-accent-soft); }
.word__edit { display: grid; gap: 6px; }
.word__edit-actions { display: flex; justify-content: flex-end; }
/* Chân thẻ: thông tin bên trái, thao tác bên phải; hẹp quá thì thao tác xuống dòng thay vì tràn ra ngoài thẻ */
.word__foot { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 4px 8px; margin-top: auto; padding-top: 6px; border-top: 1px solid var(--eh-border); }
.word__meta { display: flex; align-items: center; gap: 8px; min-width: 0; }
.word__actions { display: flex; align-items: center; margin-left: auto; }
.word__actions .el-button { margin: 0; padding: 5px 8px; }
.pagination { margin-top: 20px; justify-content: center; }
@media (max-width: 599.98px) {
  .toolbar { flex-direction: column; }
  .toolbar .el-select { width: 100%; }
}
</style>
