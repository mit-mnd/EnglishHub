<script setup>
import { computed } from 'vue'

/**
 * Phần "ngữ liệu" đi kèm câu hỏi: audio/video/ảnh (mediaUrl) và đoạn văn đọc hiểu (passage).
 * Loại media đoán theo đuôi file (file tải lên luôn có đuôi do server đặt theo nội dung thật).
 */
const props = defineProps({
  mediaUrl: { type: String, default: null },
  passage: { type: String, default: null },
})

const mediaKind = computed(() => {
  const url = (props.mediaUrl ?? '').toLowerCase().split('?')[0]
  if (!url) return null
  if (/\.(mp4|webm)$/.test(url)) return 'video'
  if (/\.(jpe?g|png|webp|gif)$/.test(url)) return 'image'
  return 'audio'
})
</script>

<template>
  <div v-if="mediaUrl || passage" class="stimulus">
    <audio v-if="mediaKind === 'audio'" :key="mediaUrl" :src="mediaUrl" controls preload="metadata" class="stimulus__media"
           aria-label="Audio của câu hỏi" />
    <video v-else-if="mediaKind === 'video'" :key="mediaUrl" :src="mediaUrl" controls preload="metadata"
           class="stimulus__media stimulus__video" />
    <img v-else-if="mediaKind === 'image'" :src="mediaUrl" alt="Hình minh họa câu hỏi" class="stimulus__image">

    <blockquote v-if="passage" class="stimulus__passage">
      <span class="eh-subtle">Đọc đoạn văn sau:</span>
      <p>{{ passage }}</p>
    </blockquote>
  </div>
</template>

<style scoped>
.stimulus { display: grid; gap: 14px; margin-bottom: 20px; }
.stimulus__media { width: 100%; max-width: 560px; }
.stimulus__video { max-height: 320px; border-radius: 10px; background: #000; }
.stimulus__image { max-width: 100%; max-height: 280px; border-radius: 10px; object-fit: contain; justify-self: start; }
.stimulus__passage {
  margin: 0; padding: 14px 18px; border-left: 4px solid var(--el-color-primary-light-5);
  background: #fafaff; border-radius: 0 10px 10px 0;
}
.stimulus__passage p { margin-top: 6px; line-height: 1.7; font-size: 16px; white-space: pre-line; }
</style>
