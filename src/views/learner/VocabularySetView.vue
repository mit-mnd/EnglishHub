<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Headset, Plus, VideoPlay } from '@element-plus/icons-vue'
import { vocabularyApi } from '@/api'
import { useSpeech } from '@/composables/useSpeech'
import { PART_OF_SPEECH } from '@/utils/skills'

const props = defineProps({ setId: { type: String, required: true } })
const router = useRouter()
const { pronounce } = useSpeech()

const detail = ref(null)
const error = ref('')
const adding = ref(false)

async function load() {
  error.value = ''
  try {
    detail.value = await vocabularyApi.set(props.setId)
  } catch (e) {
    error.value = e.message
  }
}

async function addAll() {
  adding.value = true
  try {
    const res = await vocabularyApi.addSet(props.setId)
    ElMessage.success(`Đã thêm ${res.added} từ vào sổ. Ôn chúng trong mục Từ vựng nhé!`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    adding.value = false
  }
}

async function addOne(word) {
  try {
    const saved = await vocabularyApi.save({ wordId: word.id })
    word.saved = true
    word.userWordId = saved.id
    detail.value.set.addedCount += 1
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(() => props.setId, load, { immediate: true })
</script>

<template>
  <div class="eh-page">
    <el-result v-if="error" icon="error" :title="error">
      <template #extra><el-button @click="router.push({ name: 'vocabulary' })">Quay lại</el-button></template>
    </el-result>
    <el-skeleton v-else-if="!detail" :rows="8" animated />

    <template v-else>
      <el-breadcrumb separator="›" class="crumbs">
        <el-breadcrumb-item :to="{ name: 'vocabulary' }">Từ vựng</el-breadcrumb-item>
        <el-breadcrumb-item>{{ detail.set.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <section class="eh-card head">
        <div class="head__info">
          <h2>{{ detail.set.name }}</h2>
          <p class="eh-muted">{{ detail.set.description }}</p>
          <p class="eh-subtle">{{ detail.set.wordCount }} từ · đã có {{ detail.set.addedCount }} từ trong sổ</p>
        </div>
        <el-button v-if="detail.set.addedCount < detail.set.wordCount" type="primary" size="large" :icon="Plus"
                   :loading="adding" @click="addAll">Thêm cả bộ vào sổ</el-button>
        <el-button v-else type="primary" size="large" :icon="VideoPlay" @click="router.push({ name: 'flashcard-review' })">
          Ôn tập ngay
        </el-button>
      </section>

      <div class="words">
        <article v-for="w in detail.words" :key="w.id" class="eh-card word">
          <div class="word__head">
            <div>
              <h3>{{ w.headword }}</h3>
              <span class="eh-subtle">{{ PART_OF_SPEECH[w.partOfSpeech] }} · {{ w.phoneticUs || w.phoneticUk }}</span>
            </div>
            <el-button circle :icon="Headset" aria-label="Phát âm" @click="pronounce(w.headword, w.audioUrlUs || w.audioUrlUk)" />
          </div>
          <p class="word__meaning">{{ w.meaningVi }}</p>
          <p v-if="w.example" class="word__example">{{ w.example }}</p>
          <el-tag v-if="w.saved" type="success" effect="plain" class="word__tag"><el-icon><Check /></el-icon> Trong sổ từ</el-tag>
          <el-button v-else size="small" text type="primary" :icon="Plus" class="word__tag" @click="addOne(w)">Thêm từ này</el-button>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.crumbs { margin-bottom: 14px; }
.head { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; margin-bottom: 16px; }
.head__info { flex: 1; min-width: 220px; display: grid; gap: 4px; }
.head h2 { font-size: 24px; }
.words { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
.word { display: flex; flex-direction: column; gap: 6px; }
.word__head { display: flex; justify-content: space-between; gap: 8px; }
.word__head h3 { font-size: 20px; }
.word__meaning { font-weight: 600; color: var(--eh-primary); }
.word__example { font-size: 14px; font-style: italic; color: var(--eh-text-2); flex: 1; }
.word__tag { align-self: flex-start; }
</style>
