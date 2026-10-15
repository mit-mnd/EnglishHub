<script setup>
/**
 * Hiển thị và nhận câu trả lời cho mọi dạng câu hỏi khách quan.
 * v-model là AnswerPayload giống backend:
 *   { selectedOptionIds } | { textAnswers } | { matches } | { orderedOptionIds } | { answerText }
 */
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { OPTION_LETTERS, parseBlanks } from '@/utils/question'

const props = defineProps({
  question: { type: Object, required: true },
  modelValue: { type: Object, default: null },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const q = computed(() => props.question)
const answer = computed(() => props.modelValue ?? {})
const update = (patch) => emit('update:modelValue', { ...patch })

// ----- SINGLE / MULTIPLE CHOICE -----
const selected = computed(() => new Set(answer.value.selectedOptionIds ?? []))
function toggleOption(id) {
  if (props.disabled) return
  if (q.value.type === 'SINGLE_CHOICE') {
    update({ selectedOptionIds: [id] })
  } else {
    const next = new Set(selected.value)
    next.has(id) ? next.delete(id) : next.add(id)
    update({ selectedOptionIds: [...next] })
  }
}

// ----- FILL BLANK -----
const blankParts = computed(() => parseBlanks(q.value.content))
function setBlank(index, value) {
  const texts = Array.from({ length: q.value.blankCount ?? 0 }, (_, i) => answer.value.textAnswers?.[i] ?? '')
  texts[index] = value
  update({ textAnswers: texts })
}

// ----- MATCHING -----
function setMatch(optionId, value) {
  update({ matches: { ...(answer.value.matches ?? {}), [optionId]: value } })
}
const usedMatches = computed(() => new Set(Object.values(answer.value.matches ?? {})))

// ----- ORDERING: bấm vào mảnh để thêm/bỏ khỏi câu (thân thiện cảm ứng hơn kéo-thả) -----
const ordered = computed(() => answer.value.orderedOptionIds ?? [])
const optionById = computed(() => Object.fromEntries((q.value.options ?? []).map((o) => [o.id, o])))
const bank = computed(() => (q.value.options ?? []).filter((o) => !ordered.value.includes(o.id)))
function pick(id) {
  if (!props.disabled) update({ orderedOptionIds: [...ordered.value, id] })
}
function unpick(index) {
  if (props.disabled) return
  const next = [...ordered.value]
  next.splice(index, 1)
  update({ orderedOptionIds: next })
}
</script>

<template>
  <div class="qr">
    <!-- Nội dung câu hỏi (FILL_BLANK hiển thị ô trống ngay trong câu) -->
    <p v-if="q.type === 'FILL_BLANK'" class="qr__content qr__content--blanks">
      <template v-for="(part, i) in blankParts" :key="i">
        <span v-if="part.type === 'text'">{{ part.value }}</span>
        <el-input
          v-else
          class="qr__blank"
          :model-value="answer.textAnswers?.[part.index] ?? ''"
          :disabled="disabled"
          :aria-label="`Ô trống số ${part.index + 1}`"
          @update:model-value="(v) => setBlank(part.index, v)"
        />
      </template>
    </p>
    <p v-else class="qr__content">{{ q.content }}</p>

    <p v-if="q.type === 'MULTIPLE_CHOICE'" class="eh-subtle qr__hint">Chọn tất cả đáp án đúng.</p>

    <!-- SINGLE / MULTIPLE CHOICE -->
    <div v-if="q.type === 'SINGLE_CHOICE' || q.type === 'MULTIPLE_CHOICE'" class="qr__options"
         :role="q.type === 'SINGLE_CHOICE' ? 'radiogroup' : 'group'">
      <button
        v-for="(opt, i) in q.options"
        :key="opt.id"
        type="button"
        class="option"
        :class="{ 'option--selected': selected.has(opt.id) }"
        :role="q.type === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'"
        :aria-checked="selected.has(opt.id)"
        :disabled="disabled"
        @click="toggleOption(opt.id)"
      >
        <span class="option__letter">
          <el-icon v-if="selected.has(opt.id)"><Check /></el-icon>
          <template v-else>{{ OPTION_LETTERS[i] }}</template>
        </span>
        <span class="option__text">{{ opt.content }}</span>
      </button>
    </div>

    <!-- MATCHING -->
    <div v-else-if="q.type === 'MATCHING'" class="qr__matching">
      <div v-for="opt in q.options" :key="opt.id" class="match-row">
        <span class="match-row__left">{{ opt.content }}</span>
        <span class="match-row__arrow" aria-hidden="true">→</span>
        <el-select
          :model-value="answer.matches?.[opt.id] ?? ''"
          :disabled="disabled"
          placeholder="Chọn nghĩa"
          :aria-label="`Ghép với ${opt.content}`"
          @update:model-value="(v) => setMatch(opt.id, v)"
        >
          <el-option v-for="choice in q.matchChoices" :key="choice" :label="choice" :value="choice"
                     :disabled="usedMatches.has(choice) && answer.matches?.[opt.id] !== choice" />
        </el-select>
      </div>
    </div>

    <!-- ORDERING -->
    <div v-else-if="q.type === 'ORDERING'" class="qr__ordering">
      <div class="sentence" :class="{ 'sentence--empty': !ordered.length }" aria-label="Câu bạn đã sắp xếp">
        <span v-if="!ordered.length" class="eh-subtle">Bấm vào các từ bên dưới theo đúng thứ tự…</span>
        <button v-for="(id, i) in ordered" :key="`${id}-${i}`" type="button" class="chip chip--placed"
                :disabled="disabled" @click="unpick(i)">
          {{ optionById[id]?.content }}
        </button>
      </div>
      <div class="bank">
        <button v-for="opt in bank" :key="opt.id" type="button" class="chip" :disabled="disabled" @click="pick(opt.id)">
          {{ opt.content }}
        </button>
      </div>
    </div>

    <!-- ESSAY / SPEAKING (giai đoạn sau sẽ có giao diện riêng) -->
    <el-input
      v-else
      type="textarea"
      :rows="6"
      :model-value="answer.answerText ?? ''"
      :disabled="disabled"
      placeholder="Nhập câu trả lời…"
      @update:model-value="(v) => update({ answerText: v })"
    />
  </div>
</template>

<style scoped>
.qr__content { font-size: 18px; line-height: 1.6; font-weight: 500; margin-bottom: 18px; }
.qr__content--blanks { line-height: 2.4; }
.qr__blank { width: 150px; margin: 0 4px; vertical-align: middle; }
.qr__hint { margin: -10px 0 12px; }

.qr__options { display: grid; gap: 10px; }
.option {
  display: flex; align-items: center; gap: 14px; width: 100%; min-height: 56px;
  padding: 10px 16px; text-align: left; font: inherit; font-size: 16px; color: var(--eh-text);
  background: var(--eh-surface); border: 1.5px solid var(--eh-border); border-radius: 12px;
  cursor: pointer; transition: border-color 0.15s, background-color 0.15s, box-shadow 0.15s;
}
.option:hover:not(:disabled) { border-color: var(--el-color-primary-light-5); background: #fafaff; }
.option:disabled { cursor: not-allowed; opacity: 0.7; }
.option--selected { border-color: var(--eh-primary); background: var(--eh-primary-soft); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12); }
.option__letter {
  display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; border-radius: 8px;
  font-weight: 700; font-size: 14px; background: var(--eh-bg); color: var(--eh-text-2);
}
.option--selected .option__letter { background: var(--eh-primary); color: #fff; }
.option__text { flex: 1; }

.qr__matching { display: grid; gap: 10px; }
.match-row { display: grid; grid-template-columns: 1fr auto 1.4fr; align-items: center; gap: 12px; }
.match-row__left { font-weight: 600; padding: 10px 14px; background: var(--eh-bg); border-radius: 10px; }
.match-row__arrow { color: var(--eh-text-3); }

.qr__ordering { display: grid; gap: 16px; }
.sentence {
  display: flex; flex-wrap: wrap; gap: 8px; min-height: 64px; padding: 12px;
  border: 2px dashed var(--el-color-primary-light-7); border-radius: 12px; background: #fafaff; align-items: center;
}
.bank { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  padding: 8px 14px; font: inherit; font-weight: 500; border-radius: 10px; cursor: pointer;
  border: 1.5px solid var(--eh-border); background: var(--eh-surface); color: var(--eh-text);
  box-shadow: 0 2px 0 var(--eh-border);
}
.chip:hover:not(:disabled) { border-color: var(--eh-primary); }
.chip--placed { background: var(--eh-primary); border-color: var(--eh-primary); color: #fff; box-shadow: none; }

@media (max-width: 767.98px) {
  .qr__content { font-size: 17px; }
  .match-row { grid-template-columns: 1fr; gap: 6px; }
  .match-row__arrow { display: none; }
}
</style>
