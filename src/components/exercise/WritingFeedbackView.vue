<script setup>
/** Nhận xét bài viết: điểm, tiêu chí, lỗi gạch chân trong bài, bản đã sửa, điểm mạnh / cần cải thiện. */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import ScoreRing from './ScoreRing.vue'
import { ISSUE_TYPES, scoreColor, segmentText } from '@/utils/feedback'

const props = defineProps({
  feedback: { type: Object, required: true },
  text: { type: String, required: true },
})

const view = ref('marked')   // marked | corrected
const parts = computed(() => segmentText(props.text, props.feedback.issues))
const criteria = computed(() => [
  { label: 'Hoàn thành yêu cầu', value: props.feedback.criteria.taskAchievement },
  { label: 'Ngữ pháp & chính tả', value: props.feedback.criteria.grammar },
  { label: 'Từ vựng', value: props.feedback.criteria.vocabulary },
  { label: 'Mạch lạc', value: props.feedback.criteria.coherence },
])
const issueCounts = computed(() => props.feedback.issues.reduce((acc, i) => {
  acc[i.type] = (acc[i.type] ?? 0) + 1
  return acc
}, {}))

async function copyCorrected() {
  try {
    await navigator.clipboard.writeText(props.feedback.correctedText)
    ElMessage.success('Đã sao chép bản sửa')
  } catch {
    ElMessage.warning('Không sao chép được, hãy bôi đen và sao chép thủ công')
  }
}
</script>

<template>
  <section class="wf">
    <div class="wf__top">
      <ScoreRing :score="feedback.score" :size="96" label="Điểm" />
      <div class="criteria">
        <div v-for="c in criteria" :key="c.label" class="criterion">
          <span>{{ c.label }}</span>
          <el-progress :percentage="c.value" :stroke-width="8" :color="scoreColor(c.value)" />
        </div>
        <p class="eh-subtle">{{ feedback.wordCount }} từ · {{ feedback.sentenceCount }} câu</p>
      </div>
    </div>

    <div class="wf__text">
      <div class="wf__text-head">
        <el-segmented v-model="view" size="small"
                      :options="[{ value: 'marked', label: `Bài của bạn (${feedback.issues.length} gợi ý)` }, { value: 'corrected', label: 'Bản đã sửa' }]" />
        <el-button v-if="view === 'corrected'" size="small" text :icon="CopyDocument" @click="copyCorrected">Sao chép</el-button>
      </div>
      <p v-if="view === 'marked'" class="essay">
        <template v-for="(p, i) in parts" :key="i">
          <el-tooltip v-if="p.issue" placement="top" :show-after="100">
            <template #content>
              <strong>{{ ISSUE_TYPES[p.issue.type]?.label }}</strong>: {{ p.issue.message }}
              <template v-if="p.issue.suggestion != null"><br>Gợi ý: “{{ p.issue.suggestion }}”</template>
            </template>
            <mark class="issue" :style="{ '--c': ISSUE_TYPES[p.issue.type]?.color }" tabindex="0">{{ p.text }}</mark>
          </el-tooltip>
          <span v-else>{{ p.text }}</span>
        </template>
      </p>
      <p v-else class="essay essay--corrected">{{ feedback.correctedText }}</p>
      <div v-if="feedback.issues.length" class="legend">
        <span v-for="(count, type) in issueCounts" :key="type" :style="{ '--c': ISSUE_TYPES[type]?.color }">
          {{ ISSUE_TYPES[type]?.label }}: {{ count }}
        </span>
      </div>
    </div>

    <div class="notes">
      <div v-if="feedback.strengths.length" class="note note--good">
        <strong>Điểm tốt</strong>
        <ul><li v-for="s in feedback.strengths" :key="s">{{ s }}</li></ul>
      </div>
      <div v-if="feedback.improvements.length" class="note note--improve">
        <strong>Cần cải thiện</strong>
        <ul><li v-for="s in feedback.improvements" :key="s">{{ s }}</li></ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.wf { display: grid; gap: 16px; }
.wf__top { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.criteria { flex: 1; min-width: 220px; display: grid; gap: 6px; }
.criterion { display: grid; grid-template-columns: 150px 1fr; align-items: center; gap: 8px; font-size: 14px; }
.wf__text { display: grid; gap: 10px; }
.wf__text-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.essay {
  margin: 0; padding: 14px 16px; border-radius: 12px; background: var(--eh-bg); font-size: 16px; line-height: 1.9;
  white-space: pre-wrap;
}
.essay--corrected { background: var(--eh-success-soft); }
.issue {
  background: color-mix(in srgb, var(--c) 12%, transparent); color: inherit; border-radius: 3px; cursor: help;
  text-decoration: underline wavy var(--c); text-underline-offset: 4px; text-decoration-skip-ink: none;
}
.issue:focus-visible { outline: 2px solid var(--c); }
.legend { display: flex; flex-wrap: wrap; gap: 12px; font-size: 13px; color: var(--eh-text-2); }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.legend span::before { content: ''; width: 10px; height: 10px; border-radius: 50%; background: var(--c); }
.notes { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }
.note { padding: 12px 14px; border-radius: 12px; font-size: 14px; }
.note ul { margin: 6px 0 0; padding-left: 18px; display: grid; gap: 4px; }
.note--good { background: var(--eh-success-soft); color: #065f46; }
.note--improve { background: var(--eh-accent-soft); color: #92400e; }
@media (max-width: 599.98px) {
  .criterion { grid-template-columns: 1fr; gap: 2px; }
}
</style>
