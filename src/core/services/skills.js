/**
 * Luyện nói & luyện viết - chấm ngay trên trình duyệt.
 * Bản ghi âm chỉ giữ trong bộ nhớ của phiên hiện tại (nghe lại được tới khi tải lại trang);
 * lịch sử điểm được lưu lâu dài trong localStorage.
 */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { AppError, invalid, notFound } from '../errors'
import { getQuestion } from '../content'
import { scorePronunciation, speakingTips } from '../domain/pronunciation'
import { evaluateWriting } from '../domain/writingEvaluator'

const MIN_DURATION_MS = 500
const MAX_DURATION_MS = 60_000
/** id bản ghi -> Blob (chỉ tồn tại trong phiên). */
const audioBlobs = new Map()

/** Thời lượng file WAV PCM từ header (null nếu không phải WAV). */
export async function wavDurationMs(blob) {
  const buf = new DataView(await blob.slice(0, 44).arrayBuffer())
  const tag = (o) => String.fromCharCode(buf.getUint8(o), buf.getUint8(o + 1), buf.getUint8(o + 2), buf.getUint8(o + 3))
  if (buf.byteLength < 44 || tag(0) !== 'RIFF' || tag(8) !== 'WAVE') return null
  const byteRate = buf.getUint32(28, true)
  const dataSize = buf.getUint32(40, true)
  return byteRate ? Math.round((Math.min(dataSize, blob.size - 44) * 1000) / byteRate) : null
}

/** form: FormData (hoặc object) gồm audio (WAV), questionId | referenceText, lessonId, clientTranscript. */
export async function submitSpeaking(form) {
  const get = (k) => (form instanceof FormData ? form.get(k) : form[k]) ?? null
  const audio = get('audio')
  const durationMs = audio ? await wavDurationMs(audio) : null
  if (durationMs == null) throw new AppError('INVALID_AUDIO', 'File ghi âm phải ở định dạng WAV')
  if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS) {
    throw new AppError('INVALID_AUDIO', 'Bản ghi âm phải dài từ 0,5 đến 60 giây')
  }
  const questionId = get('questionId') ? Number(get('questionId')) : null
  let referenceText = get('referenceText')
  if (questionId) {
    const q = getQuestion(questionId)
    if (!q || q.type !== 'SPEAKING') throw notFound('câu luyện nói', questionId)
    referenceText = q.answerKey
  }
  if (!referenceText?.trim()) throw invalid('Thiếu câu mẫu cần đọc')

  const result = scorePronunciation({ referenceText: referenceText.trim(), transcript: get('clientTranscript'), durationMs })
  const record = {
    id: db.nextId('speaking'), questionId, lessonId: get('lessonId') ? Number(get('lessonId')) : null,
    referenceText: referenceText.trim(), ...result, createdAt: nowIso(),
  }
  db.update('speaking', (list) => [...list, record])
  audioBlobs.set(record.id, audio)
  return toSpeakingResponse(record)
}

export function getSpeaking(id) {
  const record = db.read('speaking').find((s) => s.id === Number(id))
  if (!record) throw notFound('bản ghi âm', id)
  return toSpeakingResponse(record)
}

export function speakingAudio(id) {
  const blob = audioBlobs.get(Number(id))
  if (!blob) throw notFound('bản ghi âm (chỉ nghe lại được trong phiên hiện tại)', id)
  return blob
}

function toSpeakingResponse(r) {
  return {
    id: r.id, referenceText: r.referenceText, recognizedText: r.recognizedText, provider: r.provider, estimated: r.estimated,
    pronunciationScore: r.pronunciationScore, accuracyScore: r.accuracyScore, fluencyScore: r.fluencyScore,
    completenessScore: r.completenessScore, words: r.words, tips: speakingTips(r),
    audioUrl: audioBlobs.has(r.id) ? `local-audio:${r.id}` : null, createdAt: r.createdAt,
  }
}

/** Chấm bài viết: { text, prompt?, minWords?, maxWords?, lessonId? }. */
export function evaluateWritingSubmission({ text, prompt = null, minWords = null, maxWords = null, lessonId = null, questionId = null }) {
  if (!text?.trim()) throw invalid('Bạn chưa viết nội dung', 'text')
  if (text.length > 5000) throw invalid('Bài viết tối đa 5000 ký tự', 'text')
  const feedback = evaluateWriting({ text, prompt, minWords, maxWords })
  const record = {
    id: db.nextId('writing'), lessonId, questionId, prompt, content: text, wordCount: feedback.wordCount,
    score: feedback.score, createdAt: nowIso(),
  }
  db.update('writing', (list) => [...list, record])
  return { submissionId: record.id, feedback }
}
