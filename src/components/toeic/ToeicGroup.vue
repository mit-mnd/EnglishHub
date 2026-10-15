<script setup>
import { computed } from 'vue'
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import ListeningPlayer from './ListeningPlayer.vue'
import { LETTERS, splitPassage } from '@/utils/toeic'

/**
 * Một nhóm câu hỏi TOEIC: ngữ liệu (âm thanh / câu / đoạn văn / tài liệu) + các câu hỏi trắc nghiệm.
 * revealed = true: tô đúng/sai, hiện lời giải, kịch bản và bản dịch.
 */
const props = defineProps({
  group: { type: Object, required: true },
  answers: { type: Object, required: true },        // { questionId: index }
  revealed: { type: Boolean, default: false },
  numberStart: { type: Number, default: 1 },
})
const emit = defineEmits(['answer'])

const listening = computed(() => props.group.kind === 'response' || props.group.kind === 'talk')
const passageParts = computed(() => (props.group.kind === 'passage' ? splitPassage(props.group.text) : []))
const questionByBlank = computed(() => Object.fromEntries(props.group.questions.map((q) => [q.blank, q])))

/** Part 2: đề thật không in câu trả lời - chỉ hiện chữ sau khi trả lời. */
const hideOptionText = computed(() => props.group.kind === 'response' && !props.revealed)

function optionClass(q, i) {
  if (!props.revealed) return { 'opt--chosen': props.answers[q.id] === i }
  return {
    'opt--correct': q.answer === i,
    'opt--wrong': props.answers[q.id] === i && q.answer !== i,
  }
}

/** Ô trống Part 6: hiện đáp án đã chọn (hoặc đáp án đúng khi đã chấm). */
function blankText(blank) {
  const q = questionByBlank.value[blank]
  const chosen = props.revealed ? q.answer : props.answers[q.id]
  const text = chosen == null ? null : q.options[chosen]
  return text && text.split(/\s+/).length <= 4 ? text : null
}

function choose(q, i) {
  if (!props.revealed) emit('answer', q.id, i)
}
</script>

<template>
  <div class="tg">
    <!-- Ngữ liệu -->
    <section class="tg__stimulus">
      <ListeningPlayer v-if="listening" :group="group" :show-script="revealed" />

      <div v-if="group.graphic" class="tg__graphic">
        <strong>{{ group.graphic.title }}</strong>
        <pre>{{ group.graphic.text }}</pre>
      </div>

      <article v-if="group.kind === 'passage'" class="tg__doc">
        <header class="tg__doc-title">{{ group.title }}</header>
        <p class="tg__passage">
          <template v-for="(part, i) in passageParts" :key="i">
            <span v-if="part.type === 'text'">{{ part.value }}</span>
            <mark v-else class="tg__blank">({{ numberStart + group.questions.findIndex((q) => q.blank === part.blank) }})
              {{ blankText(part.blank) ?? '-------' }}</mark>
          </template>
        </p>
      </article>

      <template v-if="group.kind === 'documents'">
        <article v-for="(doc, i) in group.documents" :key="i" class="tg__doc">
          <header class="tg__doc-title">{{ doc.title }}</header>
          <pre class="tg__text">{{ doc.text }}</pre>
        </article>
      </template>
    </section>

    <!-- Câu hỏi -->
    <ol class="tg__questions">
      <li v-for="(q, qi) in group.questions" :key="q.id" class="tq">
        <p class="tq__prompt">
          <span class="tq__no">{{ numberStart + qi }}.</span>
          <span v-if="q.prompt" class="tq__text">{{ q.prompt }}</span>
          <span v-else-if="group.kind === 'response'" class="eh-subtle">Nghe câu hỏi và chọn câu trả lời phù hợp nhất.</span>
          <span v-else-if="group.kind === 'passage'" class="eh-subtle">Chọn phương án cho ô ({{ numberStart + qi }}).</span>
          <template v-if="revealed">
            <el-icon v-if="answers[q.id] === q.answer" class="tq__mark tq__mark--ok"><CircleCheckFilled /></el-icon>
            <el-icon v-else class="tq__mark tq__mark--bad"><CircleCloseFilled /></el-icon>
          </template>
        </p>
        <div class="tq__options" role="radiogroup" :aria-label="`Câu ${numberStart + qi}`">
          <button v-for="(opt, i) in q.options" :key="i" type="button" role="radio" class="opt" :class="optionClass(q, i)"
                  :aria-checked="answers[q.id] === i" :disabled="revealed" @click="choose(q, i)">
            <span class="opt__letter">{{ LETTERS[i] }}</span>
            <span class="opt__text">{{ hideOptionText ? '' : opt }}</span>
          </button>
        </div>
        <div v-if="revealed && q.explanation" class="tq__explain">
          <strong>Giải thích:</strong> {{ q.explanation }}
        </div>
      </li>
    </ol>

    <details v-if="revealed && group.translation" class="tg__translation">
      <summary>Xem bản dịch</summary>
      <pre>{{ group.translation }}</pre>
    </details>
  </div>
</template>

<style scoped>
.tg { display: grid; gap: 18px; }
.tg__stimulus { display: grid; gap: 14px; }
.tg__graphic { padding: 12px 14px; border: 1px dashed var(--eh-border); border-radius: 10px; background: var(--eh-surface); }
.tg__graphic pre, .tg__text, .tg__translation pre {
  margin: 6px 0 0; white-space: pre-wrap; font-family: inherit; line-height: 1.65;
}
.tg__doc { padding: 14px 16px; border: 1px solid var(--eh-border); border-radius: 12px; background: #fffef8; }
.tg__doc-title { font-weight: 700; font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; color: var(--eh-text-2); margin-bottom: 6px; }
.tg__passage { margin: 0; white-space: pre-wrap; line-height: 1.75; }
.tg__blank { padding: 1px 6px; border-radius: 6px; background: var(--eh-primary-soft); color: var(--eh-primary); font-weight: 600; }

.tg__questions { list-style: none; margin: 0; padding: 0; display: grid; gap: 18px; }
.tq { display: grid; gap: 10px; }
.tq__prompt { margin: 0; display: flex; gap: 8px; align-items: flex-start; line-height: 1.55; }
.tq__no { font-weight: 700; flex-shrink: 0; }
.tq__text { flex: 1; font-weight: 500; }
.tq__mark { font-size: 20px; flex-shrink: 0; }
.tq__mark--ok { color: #10b981; }
.tq__mark--bad { color: #ef4444; }
.tq__options { display: grid; gap: 8px; }
.opt {
  display: flex; align-items: center; gap: 10px; min-height: 44px; padding: 8px 12px; text-align: left;
  border: 1.5px solid var(--eh-border); border-radius: 10px; background: var(--eh-surface); color: var(--eh-text);
  font: inherit; cursor: pointer; transition: border-color 0.15s, background-color 0.15s;
}
.opt:hover:not(:disabled) { border-color: var(--el-color-primary-light-5); }
.opt:disabled { cursor: default; }
.opt__letter {
  display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
  background: var(--eh-bg); font-weight: 700; font-size: 13px;
}
.opt--chosen { border-color: var(--eh-primary); background: var(--eh-primary-soft); }
.opt--chosen .opt__letter { background: var(--eh-primary); color: #fff; }
.opt--correct { border-color: #10b981; background: #ecfdf5; }
.opt--correct .opt__letter { background: #10b981; color: #fff; }
.opt--wrong { border-color: #ef4444; background: #fef2f2; }
.opt--wrong .opt__letter { background: #ef4444; color: #fff; }
.tq__explain { padding: 10px 12px; border-radius: 10px; background: #f8fafc; border-left: 3px solid var(--eh-primary); line-height: 1.55; font-size: 14px; }
.tg__translation summary { cursor: pointer; color: var(--eh-primary); font-weight: 500; }
.tg__translation pre { padding: 10px 12px; border-radius: 10px; background: var(--eh-bg); }
</style>
