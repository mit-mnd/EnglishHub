import { onBeforeUnmount, onMounted } from 'vue'
import { progressApi } from '@/api'
import { heartbeat } from '@/core/services/progress'

const FLUSH_INTERVAL_MS = 30_000
const IDLE_AFTER_MS = 60_000      // không thao tác 60s (và không phát media) thì ngừng tính giờ
const MAX_SECONDS_PER_PING = 120  // khớp giới hạn của service tiến độ

/**
 * Đo thời gian học THỰC SỰ của một bài và gửi heartbeat định kỳ.
 * Chỉ tính giây khi tab đang hiển thị VÀ (học viên vừa thao tác trong 60s HOẶC media đang phát).
 *
 * @param initialLessonId id bài học
 * @param getSnapshot     () => ({ positionMs, progressPercent, mediaPlaying })
 */
export function useStudyTracker(initialLessonId, getSnapshot) {
  let lessonId = initialLessonId
  let activeSeconds = 0
  let lastInteraction = Date.now()
  let tickTimer = null
  let flushTimer = null

  const markActive = () => { lastInteraction = Date.now() }
  const interactionEvents = ['pointerdown', 'keydown', 'scroll', 'wheel', 'touchstart']

  function tick() {
    if (document.visibilityState !== 'visible') return
    const snapshot = getSnapshot()
    if (snapshot.mediaPlaying || Date.now() - lastInteraction < IDLE_AFTER_MS) {
      activeSeconds += 1
    }
  }

  function takePayload() {
    const snapshot = getSnapshot()
    const seconds = Math.min(activeSeconds, MAX_SECONDS_PER_PING)
    activeSeconds -= seconds
    return {
      seconds,
      positionMs: snapshot.positionMs != null ? Math.round(snapshot.positionMs) : undefined,
      progressPercent: snapshot.progressPercent != null ? Math.min(100, Math.round(snapshot.progressPercent)) : undefined,
    }
  }

  async function flush() {
    const targetLesson = lessonId
    const payload = takePayload()
    if (!payload.seconds && payload.positionMs == null) return null
    try {
      return await progressApi.heartbeat(targetLesson, payload)
    } catch {
      activeSeconds += payload.seconds   // gửi lỗi -> giữ lại để gửi lần sau
      return null
    }
  }

  /** Đóng tab: ghi thẳng vào localStorage (đồng bộ) để không mất thời gian học của lần cuối. */
  function flushOnPageHide() {
    const payload = takePayload()
    if (!payload.seconds) return
    try {
      heartbeat(lessonId, payload)
    } catch {
      /* bài học không còn tồn tại */
    }
  }

  onMounted(() => {
    interactionEvents.forEach((e) => window.addEventListener(e, markActive, { passive: true }))
    window.addEventListener('pagehide', flushOnPageHide)
    tickTimer = setInterval(tick, 1000)
    flushTimer = setInterval(flush, FLUSH_INTERVAL_MS)
  })

  onBeforeUnmount(() => {
    interactionEvents.forEach((e) => window.removeEventListener(e, markActive))
    window.removeEventListener('pagehide', flushOnPageHide)
    clearInterval(tickTimer)
    clearInterval(flushTimer)
    flush()
  })

  /** Chuyển sang bài khác trên cùng màn hình: gửi nốt thời gian của bài cũ rồi đổi id. */
  function switchLesson(newLessonId) {
    if (newLessonId === lessonId) return
    flush()
    lessonId = newLessonId
    activeSeconds = 0
    lastInteraction = Date.now()
  }

  return { flush, switchLesson }
}
