<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Microphone, RefreshRight, Star, StarFilled, View } from '@element-plus/icons-vue'
import { sentenceApi } from '@/api'
import { useSpeech } from '@/composables/useSpeech'
import { shuffle } from '@/utils/game'

/**
 * Một chủ đề mẫu câu.
 *  - Học: xem mẫu câu, ví dụ, mẹo dùng; nghe giọng đọc; lưu; đánh dấu đã luyện.
 *  - Tự kiểm tra: nhìn nghĩa tiếng Việt, tự nói/viết câu tiếng Anh rồi lật xem đáp án.
 */
const props = defineProps({ topicId: { type: String, required: true } })
const router = useRouter()
const { speak } = useSpeech()

const topic = ref(null)
const mode = ref('learn')
const quiz = ref({ order: [], index: 0, shown: false, remembered: 0 })
const FORMALITY = { formal: { label: 'Trang trọng', type: 'primary' }, neutral: { label: 'Trung tính', type: 'info' }, informal: { label: 'Thân mật', type: 'warning' } }

const card = computed(() => quiz.value.order[quiz.value.index])
const quizDone = computed(() => quiz.value.index >= quiz.value.order.length)

async function load() {
  try {
    topic.value = await sentenceApi.topic(props.topicId)
  } catch (e) {
    ElMessage.error(e.message)
    router.replace({ name: 'sentences' })
  }
}

async function toggleSave(p) {
  const res = await sentenceApi.setSaved(p.id, !p.saved)
  p.saved = res.saved
  ElMessage.success(p.saved ? 'Đã lưu mẫu câu' : 'Đã bỏ lưu')
}

async function practiced(p) {
  const res = await sentenceApi.practiced(p.id)
  p.practiceCount = res.practiceCount
}

function startQuiz() {
  quiz.value = { order: shuffle(topic.value.patterns), index: 0, shown: false, remembered: 0 }
  mode.value = 'quiz'
}

async function answerQuiz(remembered) {
  if (remembered) {
    quiz.value.remembered++
    await practiced(card.value)
  }
  quiz.value.index++
  quiz.value.shown = false
}

onMounted(load)
</script>

<template>
  <div class="eh-page st">
    <RouterLink :to="{ name: 'sentences' }" class="st__back"><el-icon><ArrowLeft /></el-icon> Mẫu câu</RouterLink>

    <template v-if="topic">
      <header class="st__head">
        <div>
          <h2>{{ topic.name }}</h2>
          <p class="eh-muted">{{ topic.description }}</p>
        </div>
        <el-radio-group v-model="mode" @change="(m) => m === 'quiz' && startQuiz()">
          <el-radio-button value="learn">Học</el-radio-button>
          <el-radio-button value="quiz">Tự kiểm tra</el-radio-button>
        </el-radio-group>
      </header>

      <!-- Học -->
      <section v-if="mode === 'learn'" class="st__list">
        <article v-for="p in topic.patterns" :key="p.id" class="eh-card pat">
          <div class="pat__top">
            <strong class="pat__pattern">{{ p.pattern }}</strong>
            <el-tag v-if="p.formality" size="small" :type="FORMALITY[p.formality]?.type" effect="plain">{{ FORMALITY[p.formality]?.label }}</el-tag>
          </div>
          <p class="pat__meaning">{{ p.meaning }}</p>
          <div class="pat__example">
            <el-button :icon="Microphone" circle size="small" aria-label="Nghe ví dụ" @click="speak(p.example)" />
            <div>
              <p>{{ p.example }}</p>
              <p class="eh-subtle">{{ p.exampleTranslation }}</p>
            </div>
          </div>
          <p v-if="p.note" class="pat__note">💡 {{ p.note }}</p>
          <div class="pat__actions">
            <el-button size="small" text :type="p.saved ? 'warning' : 'default'" :icon="p.saved ? StarFilled : Star" @click="toggleSave(p)">
              {{ p.saved ? 'Đã lưu' : 'Lưu' }}
            </el-button>
            <el-button size="small" text :icon="Check" :type="p.practiceCount ? 'success' : 'default'" @click="practiced(p)">
              {{ p.practiceCount ? `Đã luyện ${p.practiceCount} lần` : 'Đánh dấu đã luyện' }}
            </el-button>
          </div>
        </article>
      </section>

      <!-- Tự kiểm tra -->
      <section v-else class="eh-card quiz">
        <template v-if="!quizDone">
          <p class="eh-subtle">Câu {{ quiz.index + 1 }}/{{ quiz.order.length }} · Nói to câu tiếng Anh rồi lật để kiểm tra</p>
          <p class="quiz__vi">{{ card.exampleTranslation }}</p>
          <p class="eh-subtle">Gợi ý mẫu: {{ card.meaning }}</p>
          <div v-if="quiz.shown" class="quiz__answer">
            <p class="quiz__en">{{ card.example }}</p>
            <p class="eh-subtle">Mẫu câu: <strong>{{ card.pattern }}</strong></p>
            <el-button :icon="Microphone" round @click="speak(card.example)">Nghe</el-button>
          </div>
          <div class="quiz__actions">
            <el-button v-if="!quiz.shown" type="primary" size="large" :icon="View" @click="quiz.shown = true; speak(card.example)">Lật đáp án</el-button>
            <template v-else>
              <el-button size="large" @click="answerQuiz(false)">Chưa nhớ</el-button>
              <el-button type="success" size="large" @click="answerQuiz(true)">Nhớ rồi</el-button>
            </template>
          </div>
        </template>
        <div v-else class="quiz__done">
          <h3>Hoàn thành! 🎉</h3>
          <p>Bạn nhớ {{ quiz.remembered }}/{{ quiz.order.length }} mẫu câu.</p>
          <div class="quiz__actions">
            <el-button :icon="RefreshRight" @click="startQuiz">Làm lại</el-button>
            <el-button type="primary" @click="mode = 'learn'">Về chế độ học</el-button>
          </div>
        </div>
      </section>
    </template>
    <div v-else v-loading="true" style="min-height: 240px" />
  </div>
</template>

<style scoped>
.st { display: grid; gap: 16px; max-width: 900px; }
.st__back { display: inline-flex; align-items: center; gap: 4px; color: var(--eh-text-2); }
.st__head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: 12px; }
.st__head h2 { margin-bottom: 4px; }
.st__list { display: grid; gap: 12px; }
.pat { display: grid; gap: 8px; }
.pat__top { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.pat__pattern { font-size: 18px; line-height: 1.4; }
.pat__meaning { margin: 0; color: var(--eh-text-2); }
.pat__example { display: flex; gap: 10px; align-items: flex-start; padding: 10px 12px; border-radius: 10px; background: var(--eh-bg); }
.pat__example p { margin: 0; line-height: 1.5; }
.pat__note { margin: 0; font-size: 14px; color: var(--eh-accent-text); background: var(--eh-accent-soft); padding: 8px 12px; border-radius: 10px; }
.pat__actions { display: flex; gap: 4px; }
.quiz { display: grid; justify-items: center; gap: 12px; text-align: center; padding: 32px 20px; }
.quiz__vi { font-size: 22px; font-weight: 600; margin: 8px 0 0; line-height: 1.4; }
.quiz__answer { display: grid; gap: 6px; justify-items: center; padding: 14px 18px; border-radius: 12px; background: var(--eh-success-soft); width: 100%; }
.quiz__en { font-size: 20px; font-weight: 600; color: var(--eh-success-text); margin: 0; }
.quiz__actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.quiz__done { display: grid; gap: 8px; justify-items: center; }
</style>
