<script setup>
/**
 * Hiển thị nội dung bài (HTML đã lọc) với tra từ trực tiếp:
 *  - Bấm vào một từ -> tra từ đó.
 *  - Bôi đen một cụm (tối đa 4 từ) -> tra cả cụm.
 * Từ đã lưu vào sổ được gạch chân để học viên nhận ra.
 */
import { computed, ref } from 'vue'
import WordLookupPanel from './WordLookupPanel.vue'
import { prepareReadingHtml, sentenceAround } from '@/utils/readingText'

const props = defineProps({
  html: { type: String, default: '' },
  savedHeadwords: { type: Array, default: () => [] },
  lessonId: { type: Number, default: null },
})
const emit = defineEmits(['saved'])

const container = ref()
const lookup = ref(null)      // { query, rect, context }

const renderedHtml = computed(() => prepareReadingHtml(props.html, new Set(props.savedHeadwords)))

function contextOf(node, word) {
  const block = node.closest('p, li, blockquote, td') ?? container.value
  const text = block.textContent
  const index = text.indexOf(word)
  return sentenceAround(text, index === -1 ? 0 : index)
}

function onClick(event) {
  const target = event.target.closest('.rw')
  if (!target || window.getSelection()?.toString().trim()) return
  lookup.value = { query: target.dataset.w, rect: target.getBoundingClientRect(), context: contextOf(target, target.dataset.w) }
}

/** Bôi đen nhiều từ -> tra cụm từ. */
function onSelectionEnd() {
  const selection = window.getSelection()
  const text = selection?.toString().trim().replace(/\s+/g, ' ')
  if (!text || text.split(' ').length > 4 || text.length > 60 || !/[A-Za-z]/.test(text)) return
  const range = selection.getRangeAt(0)
  if (!container.value?.contains(range.commonAncestorContainer)) return
  const node = range.startContainer.parentElement ?? container.value
  lookup.value = { query: text, rect: range.getBoundingClientRect(), context: contextOf(node, text) }
}
</script>

<template>
  <div>
    <!-- Nội dung đã được lọc bằng DOMPurify trong prepareReadingHtml -->
    <div ref="container" class="reading" @click="onClick" @mouseup="onSelectionEnd" @touchend="onSelectionEnd"
         v-html="renderedHtml" />
    <WordLookupPanel v-if="lookup" :key="lookup.query + lookup.rect.top" :query="lookup.query" :anchor-rect="lookup.rect"
                     :context="lookup.context" :lesson-id="lessonId" @close="lookup = null"
                     @saved="(w) => emit('saved', w)" />
  </div>
</template>

<style scoped>
.reading { font-size: 18px; line-height: 1.85; color: var(--eh-text); }
.reading :deep(p) { margin: 0 0 1em; }
.reading :deep(h3) { font-size: 20px; margin: 0.6em 0 0.4em; }
.reading :deep(.rw) { cursor: pointer; border-radius: 4px; transition: background-color 0.1s; }
.reading :deep(.rw:hover) { background: var(--eh-primary-soft); color: var(--eh-primary); }
.reading :deep(.rw--saved) {
  text-decoration: underline; text-decoration-color: var(--eh-accent); text-decoration-thickness: 2px; text-underline-offset: 4px;
}
@media (max-width: 767.98px) {
  .reading { font-size: 17px; }
}
</style>
