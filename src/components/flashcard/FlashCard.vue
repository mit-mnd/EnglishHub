<script setup>
/**
 * Thẻ lật 2 mặt. direction: 'EN_VI' (mặt trước là từ tiếng Anh) | 'VI_EN' (mặt trước là nghĩa).
 * Bấm thẻ (hoặc Space ở component cha) để lật.
 */
import { computed } from 'vue'
import { Headset } from '@element-plus/icons-vue'
import { useSpeech } from '@/composables/useSpeech'
import { PART_OF_SPEECH } from '@/utils/skills'

const props = defineProps({
  card: { type: Object, required: true },
  flipped: { type: Boolean, default: false },
  direction: { type: String, default: 'EN_VI' },
})
const emit = defineEmits(['flip'])

const { pronounce } = useSpeech()
const word = computed(() => props.card.word)
const meaning = computed(() => word.value.meaningVi || word.value.definitionEn || '(chưa có nghĩa)')
const phonetic = computed(() => word.value.phoneticUs || word.value.phoneticUk)

function speak(event) {
  event.stopPropagation()
  pronounce(word.value.headword, word.value.audioUrlUs || word.value.audioUrlUk)
}

/** Che từ trong câu ngữ cảnh ở mặt trước (chiều Việt -> Anh) để không lộ đáp án. */
const maskedContext = computed(() => {
  const ctx = props.card.contextSentence
  if (!ctx) return null
  const pattern = new RegExp(`\\b${word.value.headword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\w*`, 'gi')
  return ctx.replace(pattern, '_____')
})
</script>

<template>
  <div class="scene">
    <button type="button" class="card" :class="{ 'card--flipped': flipped }"
            :aria-label="flipped ? 'Mặt sau thẻ' : 'Mặt trước thẻ, bấm để lật'" @click="emit('flip')">
      <!-- Mặt trước -->
      <div class="face face--front" :aria-hidden="flipped">
        <span v-if="card.status === 'NEW'" class="badge">Từ mới</span>
        <template v-if="direction === 'EN_VI'">
          <h2 class="headword">{{ word.headword }}</h2>
          <p class="pos">{{ PART_OF_SPEECH[word.partOfSpeech] ?? word.partOfSpeech }}<template v-if="phonetic"> · {{ phonetic }}</template></p>
          <span class="speak" role="button" tabindex="-1" aria-label="Phát âm" @click="speak"><el-icon><Headset /></el-icon></span>
        </template>
        <template v-else>
          <h2 class="meaning meaning--front">{{ meaning }}</h2>
          <p class="pos">{{ PART_OF_SPEECH[word.partOfSpeech] ?? word.partOfSpeech }}</p>
          <p v-if="maskedContext" class="context">“{{ maskedContext }}”</p>
        </template>
        <span class="hint">Bấm thẻ hoặc phím Space để xem đáp án</span>
      </div>

      <!-- Mặt sau -->
      <div class="face face--back" :aria-hidden="!flipped">
        <h2 class="headword headword--small">{{ word.headword }}</h2>
        <p class="pos">{{ PART_OF_SPEECH[word.partOfSpeech] ?? word.partOfSpeech }}<template v-if="phonetic"> · {{ phonetic }}</template>
          <span class="speak speak--inline" role="button" tabindex="-1" aria-label="Phát âm" @click="speak"><el-icon><Headset /></el-icon></span>
        </p>
        <p class="meaning">{{ meaning }}</p>
        <p v-if="word.meaningVi && word.definitionEn" class="definition">{{ word.definitionEn }}</p>
        <blockquote v-if="word.example" class="example">
          {{ word.example }}
          <span v-if="word.exampleTranslation">{{ word.exampleTranslation }}</span>
        </blockquote>
        <p v-if="card.contextSentence" class="context">Gặp trong bài: “{{ card.contextSentence }}”</p>
        <p v-if="card.note" class="note">📝 {{ card.note }}</p>
      </div>
    </button>
  </div>
</template>

<style scoped>
.scene { perspective: 1400px; width: 100%; }
.card {
  position: relative; display: grid; width: 100%; min-height: 340px; padding: 0; border: 0; background: none;
  font: inherit; color: inherit; text-align: center; cursor: pointer;
  transform-style: preserve-3d; transition: transform 0.45s cubic-bezier(0.3, 0.7, 0.3, 1);
}
.card--flipped { transform: rotateY(180deg); }
.face {
  grid-area: 1 / 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  padding: 32px 28px; border-radius: 22px; background: var(--eh-surface); border: 1px solid var(--eh-border);
  box-shadow: var(--eh-shadow-lg); backface-visibility: hidden; -webkit-backface-visibility: hidden;
}
.face--front { background: linear-gradient(160deg, var(--eh-surface), var(--eh-surface-2)); }
.face--back { transform: rotateY(180deg); justify-content: flex-start; }
.badge { position: absolute; top: 16px; left: 16px; font-size: 12px; font-weight: 600; padding: 3px 10px; border-radius: 999px; background: var(--eh-accent-soft); color: var(--eh-accent-text); }
.headword { font-size: 44px; letter-spacing: -0.5px; }
.headword--small { font-size: 30px; }
.pos { display: inline-flex; align-items: center; gap: 8px; color: var(--eh-text-2); font-style: italic; }
.meaning { font-size: 24px; font-weight: 700; color: var(--eh-primary); }
.meaning--front { font-size: 30px; }
.definition { color: var(--eh-text-2); }
.example {
  margin: 4px 0 0; padding: 10px 14px; max-width: 520px; border-radius: 12px; background: var(--eh-bg);
  font-style: italic; display: grid; gap: 4px;
}
.example span { font-style: normal; font-size: 14px; color: var(--eh-text-2); }
.context { font-size: 14px; color: var(--eh-text-2); max-width: 520px; }
.note { font-size: 14px; padding: 6px 12px; border-radius: 10px; background: var(--eh-accent-soft); }
.speak {
  display: grid; place-items: center; width: 52px; height: 52px; border-radius: 50%; margin-top: 6px;
  background: var(--eh-primary-soft); color: var(--eh-primary); font-size: 22px;
}
.speak:hover { background: var(--eh-primary); color: var(--eh-on-primary); }
.speak--inline { width: 30px; height: 30px; margin: 0; font-size: 15px; }
.hint { position: absolute; bottom: 16px; font-size: 13px; color: var(--eh-text-3); }

@media (max-width: 599.98px) {
  .card { min-height: 300px; }
  .headword { font-size: 36px; }
  .face { padding: 28px 18px; }
}
@media (prefers-reduced-motion: reduce) {
  .card { transition: none; }
}
</style>
