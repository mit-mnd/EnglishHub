<script setup>
/**
 * Bảng tra từ nổi cạnh từ được bấm (desktop) hoặc dạng bottom sheet (mobile).
 * Từ / cụm ngắn: phiên âm UK/US + phát âm, nghĩa tiếng Việt, định nghĩa, ví dụ; nút lưu vào sổ từ.
 * Đoạn dài (cả câu, có dấu câu): chỉ hiện bản dịch tiếng Việt.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, Close, Headset, Plus } from '@element-plus/icons-vue'
import { vocabularyApi } from '@/api'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useSpeech } from '@/composables/useSpeech'
import { isWordQuery } from '@/core/domain/lemmatize'
import { PART_OF_SPEECH } from '@/utils/skills'

const props = defineProps({
  query: { type: String, required: true },
  anchorRect: { type: Object, default: null },      // DOMRect của từ được bấm
  context: { type: String, default: '' },           // câu chứa từ
  lessonId: { type: Number, default: null },
})
const emit = defineEmits(['close', 'saved'])

const { isMobile } = useBreakpoint()
const { pronounce } = useSpeech()

const loading = ref(true)
const error = ref('')
const result = ref(null)
const passage = ref(null)             // { text, translation, offline } khi dịch đoạn dài
const activeIndex = ref(0)
const saving = ref(false)
const panel = ref()

const isPassage = computed(() => !isWordQuery(props.query))
const entry = computed(() => result.value?.entries?.[activeIndex.value])

const PANEL_WIDTH = 340
const GAP = 10
/** Đặt bảng ở phía (trên/dưới từ) còn nhiều chỗ hơn và giới hạn chiều cao theo khoảng trống đó. */
const position = computed(() => {
  if (isMobile.value || !props.anchorRect) return {}
  const r = props.anchorRect
  const left = Math.min(Math.max(12, r.left + r.width / 2 - PANEL_WIDTH / 2), window.innerWidth - PANEL_WIDTH - 12)
  const spaceBelow = window.innerHeight - r.bottom - GAP * 2
  const spaceAbove = r.top - GAP * 2
  const preferBelow = spaceBelow >= 320 || spaceBelow >= spaceAbove
  const maxHeight = `${Math.max(160, Math.min(460, preferBelow ? spaceBelow : spaceAbove))}px`
  return preferBelow
    ? { left: `${left}px`, top: `${r.bottom + GAP}px`, maxHeight }
    : { left: `${left}px`, bottom: `${window.innerHeight - r.top + GAP}px`, maxHeight }
})

async function lookup() {
  loading.value = true
  error.value = ''
  activeIndex.value = 0
  result.value = null
  passage.value = null
  try {
    if (isPassage.value) passage.value = await vocabularyApi.translate(props.query)
    else result.value = await vocabularyApi.lookup(props.query)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!entry.value || entry.value.saved) return
  saving.value = true
  try {
    const saved = await vocabularyApi.save({
      wordId: entry.value.id,
      sourceLessonId: props.lessonId ?? undefined,
      contextSentence: props.context || undefined,
    })
    entry.value.saved = true
    entry.value.userWordId = saved.id
    emit('saved', entry.value.headword)
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Escape') emit('close')
}
function onPointerDown(e) {
  if (panel.value && !panel.value.contains(e.target) && !e.target.closest?.('.rw')) emit('close')
}
// Bảng đặt cố định theo vị trí từ lúc bấm -> trang cuộn đi xa thì đóng để không "trôi" lệch khỏi từ
const initialScrollY = window.scrollY
function onScroll() {
  if (!isMobile.value && Math.abs(window.scrollY - initialScrollY) > 40) emit('close')
}

watch(() => props.query, lookup, { immediate: true })
onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('pointerdown', onPointerDown)
  window.addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pointerdown', onPointerDown)
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <Teleport to="body">
    <div ref="panel" class="lookup" :class="{ 'lookup--sheet': isMobile }" :style="position"
         role="dialog" :aria-label="`Tra từ ${query}`">
      <header class="lookup__head">
        <span class="eh-subtle">Tra từ</span>
        <el-button circle text size="small" :icon="Close" aria-label="Đóng" @click="emit('close')" />
      </header>

      <el-skeleton v-if="loading" :rows="3" animated />

      <p v-else-if="error" class="lookup__error">{{ error }}</p>

      <div v-else-if="passage" class="passage">
        <blockquote class="example">{{ passage.text }}</blockquote>
        <template v-if="passage.translation">
          <p class="meaning">{{ passage.translation }}</p>
          <p class="auto">Bản dịch tự động, chỉ mang tính tham khảo.</p>
        </template>
        <template v-else>
          <p class="eh-muted">
            {{ passage.offline ? 'Không kết nối được dịch vụ dịch (mất mạng hoặc hết hạn mức hôm nay).' : 'Chưa dịch được đoạn này.' }}
          </p>
          <el-button v-if="passage.offline" size="small" @click="lookup">Thử lại</el-button>
        </template>
        <button type="button" class="phon" @click="pronounce(passage.text, null, 'US')">
          <el-icon><Headset /></el-icon> Nghe đọc
        </button>
      </div>

      <div v-else-if="!entry" class="lookup__empty">
        <strong>"{{ query }}"</strong>
        <p v-if="result?.offline" class="eh-muted">Không kết nối được dịch vụ từ điển. Kiểm tra mạng rồi thử lại.</p>
        <p v-else class="eh-muted">Chưa tìm thấy từ này trong từ điển.</p>
        <el-button v-if="result?.offline" size="small" @click="lookup">Thử lại</el-button>
      </div>

      <template v-else>
        <div class="word">
          <h3 class="word__head">{{ entry.headword }}</h3>
          <span class="word__pos">{{ PART_OF_SPEECH[entry.partOfSpeech] ?? entry.partOfSpeech }}</span>
        </div>
        <p v-if="result.matchedHeadword && result.matchedHeadword !== result.query" class="eh-subtle">
          Dạng gốc của "{{ result.query }}"
        </p>

        <div class="phonetics">
          <button type="button" class="phon" @click="pronounce(entry.headword, entry.audioUrlUk, 'UK')">
            <el-icon><Headset /></el-icon> UK <span v-if="entry.phoneticUk">{{ entry.phoneticUk }}</span>
          </button>
          <button type="button" class="phon" @click="pronounce(entry.headword, entry.audioUrlUs, 'US')">
            <el-icon><Headset /></el-icon> US <span v-if="entry.phoneticUs">{{ entry.phoneticUs }}</span>
          </button>
        </div>

        <el-segmented v-if="result.entries.length > 1" v-model="activeIndex" size="small" class="pos-tabs"
                      :options="result.entries.map((e, i) => ({ value: i, label: PART_OF_SPEECH[e.partOfSpeech] ?? e.partOfSpeech }))" />

        <p v-if="entry.meaningVi" class="meaning">
          {{ entry.meaningVi }}
          <span v-if="entry.autoTranslated" class="auto">dịch tự động</span>
        </p>
        <p v-else class="eh-subtle">Chưa có nghĩa tiếng Việt — định nghĩa tiếng Anh:</p>
        <p v-if="entry.definitionEn" class="definition">{{ entry.definitionEn }}</p>
        <blockquote v-if="entry.example" class="example">
          {{ entry.example }}
          <span v-if="entry.exampleTranslation">{{ entry.exampleTranslation }}</span>
        </blockquote>

        <el-button v-if="!entry.saved" type="primary" :icon="Plus" :loading="saving" class="save" @click="save">
          Lưu vào sổ từ
        </el-button>
        <div v-else class="saved">
          <el-icon><Check /></el-icon> Đã có trong sổ từ
          <RouterLink :to="{ name: 'vocabulary' }">Xem sổ từ</RouterLink>
        </div>
      </template>
    </div>
  </Teleport>
</template>

<style scoped>
.lookup {
  position: fixed; z-index: 2100; width: 340px; max-height: min(460px, 80vh); overflow-y: auto;
  padding: 14px 16px 16px; border-radius: 14px; background: var(--eh-surface);
  border: 1px solid var(--eh-border); box-shadow: var(--eh-shadow-lg);
  display: grid; gap: 8px; animation: pop 0.12s ease-out;
}
@keyframes pop { from { opacity: 0; transform: translateY(4px); } }
.lookup--sheet {
  left: 0 !important; right: 0; bottom: 0 !important; top: auto !important; width: auto;
  border-radius: 18px 18px 0 0; padding-bottom: calc(16px + env(safe-area-inset-bottom)); max-height: 70vh;
}
.lookup__head { display: flex; align-items: center; justify-content: space-between; margin: -4px -4px 0 0; }
.lookup__error { color: var(--eh-danger); }
.word { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.word__head { font-size: 24px; }
.word__pos { font-size: 13px; font-style: italic; color: var(--eh-text-2); }
.phonetics { display: flex; gap: 8px; flex-wrap: wrap; }
.phon {
  display: inline-flex; align-items: center; gap: 6px; padding: 5px 10px; border-radius: 999px;
  border: 1px solid var(--eh-border); background: var(--eh-bg); font: inherit; font-size: 13px; cursor: pointer; color: var(--eh-text);
}
.phon:hover { border-color: var(--eh-primary); color: var(--eh-primary); }
.phon span { color: var(--eh-text-2); }
.pos-tabs { justify-self: start; }
.meaning { font-size: 17px; font-weight: 600; color: var(--eh-primary); }
.auto { font-size: 12px; font-weight: 400; color: var(--eh-text-2); }
.passage { display: grid; gap: 8px; }
.definition { color: var(--eh-text-2); font-size: 14px; }
.example {
  margin: 0; padding: 8px 12px; border-left: 3px solid var(--el-color-primary-light-5); border-radius: 0 8px 8px 0;
  background: var(--eh-surface-2); font-style: italic; font-size: 14px; display: grid; gap: 2px;
}
.example span { font-style: normal; color: var(--eh-text-2); font-size: 13px; }
.save { width: 100%; margin-top: 4px; }
.saved { display: flex; align-items: center; gap: 6px; color: var(--eh-success); font-weight: 600; font-size: 14px; }
.saved a { margin-left: auto; font-weight: 500; }
</style>
