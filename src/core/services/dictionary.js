/**
 * Nguồn tra từ bên ngoài (miễn phí, không cần key, gọi thẳng từ trình duyệt):
 *  - Free Dictionary API (dictionaryapi.dev): định nghĩa tiếng Anh, phiên âm, audio.
 *  - Wiktionary REST API: định nghĩa tiếng Anh dự phòng khi nguồn trên lỗi / thiếu từ.
 *  - MyMemory: nghĩa tiếng Việt cho từ, cụm từ và đoạn văn ngắn (hạn mức ẩn danh ~5.000 ký tự/ngày).
 *
 * Các nguồn được gọi song song, mỗi nguồn có thời hạn riêng; nguồn lỗi thì tạm bỏ qua một lúc (cooldown)
 * để lần tra sau không phải chờ lại. Kết quả do vocabulary.js lưu vào máy nên từ đã tra dùng lại được khi mất mạng.
 */

const DICTIONARY_URL = 'https://api.dictionaryapi.dev/api/v2/entries/en/'
const WIKTIONARY_URL = 'https://en.wiktionary.org/api/rest_v1/page/definition/'
const MYMEMORY_URL = 'https://api.mymemory.translated.net/get'
const TIMEOUT_MS = 4000
const COOLDOWN_MS = 60_000

const KNOWN_POS = new Set(['noun', 'verb', 'adjective', 'adverb', 'pronoun', 'preposition', 'conjunction', 'interjection'])
const MAX_POS_ENTRIES = 3

const cooldownUntil = new Map()
/** Dùng cho test. */
export const resetCooldowns = () => cooldownUntil.clear()

/** { data } khi nguồn trả lời (data = null nếu không có từ - 404), { failed } khi lỗi mạng / quá hạn / lỗi máy chủ. */
async function getJson(source, url) {
  if ((cooldownUntil.get(source) ?? 0) > Date.now()) return { failed: true }
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    const res = await fetch(url, { signal: controller.signal })
    if (res.status === 404) return { data: null }
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  } catch {
    cooldownUntil.set(source, Date.now() + COOLDOWN_MS)
    return { failed: true }
  } finally {
    clearTimeout(timer)
  }
}

const normalizePos = (pos) => {
  const p = pos?.toLowerCase()
  return KNOWN_POS.has(p) ? p : 'other'
}

const lowerFirst = (s) => (s ? s[0].toLowerCase() + s.slice(1) : s)

// ------------------------------------------------------------------ Free Dictionary API

/** Gộp mọi entry; mỗi từ loại lấy định nghĩa + ví dụ đầu tiên (tối đa 3 từ loại). */
export function parseDictionary(root) {
  const headword = root[0].word
  let fallback = root[0].phonetic ?? null
  let phoneticUk = null, phoneticUs = null, audioUk = null, audioUs = null
  for (const entry of root) {
    for (const p of entry.phonetics ?? []) {
      if (p.audio?.includes('-uk.') && !audioUk) {
        audioUk = p.audio
        phoneticUk = p.text ?? phoneticUk
      } else if (p.audio?.includes('-us.') && !audioUs) {
        audioUs = p.audio
        phoneticUs = p.text ?? phoneticUs
      } else if (p.text && !fallback) {
        fallback = p.text
      }
    }
  }
  const byPos = new Map()
  for (const entry of root) {
    for (const meaning of entry.meanings ?? []) {
      const pos = normalizePos(meaning.partOfSpeech)
      const slot = byPos.get(pos) ?? {}
      for (const def of meaning.definitions ?? []) {
        slot.definition ??= def.definition
        slot.example ??= def.example
      }
      byPos.set(pos, slot)
    }
  }
  return [...byPos].filter(([, s]) => s.definition).slice(0, MAX_POS_ENTRIES).map(([pos, s]) => ({
    headword, partOfSpeech: pos, phoneticUk: phoneticUk ?? fallback, phoneticUs: phoneticUs ?? fallback,
    audioUrlUk: audioUk, audioUrlUs: audioUs, definitionEn: s.definition, example: s.example ?? null,
  }))
}

async function fromDictionaryApi(word) {
  const r = await getJson('dictionaryapi', DICTIONARY_URL + encodeURIComponent(word))
  if (r.failed) return { failed: true, entries: [] }
  return { failed: false, entries: Array.isArray(r.data) && r.data.length ? parseDictionary(r.data) : [] }
}

// ------------------------------------------------------------------ Wiktionary

const ENTITIES = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'", '&apos;': "'", '&nbsp;': ' ' }
/** Wiktionary trả HTML -> văn bản thuần (kết quả chỉ được hiển thị dạng text nên không cần xử lý thêm). */
const plain = (html) => (html ?? '').replace(/<[^>]*>/g, '').replace(/&(?:amp|lt|gt|quot|#39|apos|nbsp);/g, (m) => ENTITIES[m]).replace(/\s+/g, ' ').trim()

/** "plural of planner", "simple past of stop"... - dạng biến đổi, nên tra tiếp từ gốc thay vì dùng nghĩa này. */
const INFLECTION = /^(?:the\s+)?(?:plural|simple past|past participle|past tense|present participle|third-person singular|comparative|superlative|alternative (?:form|spelling|letter-case form)|misspelling|nonstandard spelling)\b.{0,40}\bof\b/i

export function parseWiktionary(root, word) {
  const byPos = new Map()
  for (const item of root?.en ?? []) {
    const pos = normalizePos(item.partOfSpeech)
    const slot = byPos.get(pos) ?? {}
    for (const d of item.definitions ?? []) {
      const definition = plain(d.definition)
      if (!definition || INFLECTION.test(definition)) continue
      slot.definition ??= definition
      slot.example ??= plain(d.examples?.[0] ?? d.parsedExamples?.[0]?.example) || null
    }
    byPos.set(pos, slot)
  }
  return [...byPos].filter(([, s]) => s.definition).slice(0, MAX_POS_ENTRIES).map(([pos, s]) => ({
    headword: word, partOfSpeech: pos, phoneticUk: null, phoneticUs: null, audioUrlUk: null, audioUrlUs: null,
    definitionEn: s.definition, example: s.example ?? null,
  }))
}

async function fromWiktionary(word) {
  const r = await getJson('wiktionary', WIKTIONARY_URL + encodeURIComponent(word))
  if (r.failed) return { failed: true, entries: [] }
  return { failed: false, entries: r.data ? parseWiktionary(r.data, word) : [] }
}

/** Định nghĩa tiếng Anh: ưu tiên Free Dictionary API (có phiên âm, audio), thiếu thì lấy Wiktionary. */
async function defineEnglish(word) {
  const [primary, backup] = await Promise.all([fromDictionaryApi(word), fromWiktionary(word)])
  return { entries: primary.entries.length ? primary.entries : backup.entries, failed: primary.failed && backup.failed }
}

// ------------------------------------------------------------------ MyMemory (dịch sang tiếng Việt)

/** { text } ('' = nguồn không dịch được), hoặc { failed: true } khi lỗi mạng / hết hạn mức. */
export async function translateVi(rawText) {
  const text = (rawText ?? '').trim()
  const r = await getJson('mymemory', `${MYMEMORY_URL}?q=${encodeURIComponent(text)}&langpair=en%7Cvi`)
  const body = r.data
  // Hết hạn mức: máy chủ trả responseStatus 429 / quotaFinished (đôi khi vẫn HTTP 200)
  if (r.failed || !body || Number(body.responseStatus) !== 200 || body.quotaFinished) {
    if (!r.failed) cooldownUntil.set('mymemory', Date.now() + COOLDOWN_MS)
    return { failed: true, text: '' }
  }
  const translated = plain(body.responseData?.translatedText)
  // Không dịch được thì dịch vụ trả lại nguyên văn hoặc thông báo của chính nó
  if (!translated || translated.toLowerCase() === text.toLowerCase() || /MYMEMORY|INVALID/i.test(translated)) return { text: '' }
  const isShort = text.split(' ').length <= 4
  // Từ viết thường thì nghĩa cũng viết thường ("Đa dạng sinh học" -> "đa dạng sinh học")
  return { text: isShort && text[0] === text[0].toLowerCase() ? lowerFirst(translated) : translated }
}

// ------------------------------------------------------------------ tra một từ / cụm từ

/**
 * Tra một từ hoặc cụm từ ở mọi nguồn cùng lúc.
 * entries: định nghĩa tiếng Anh (nếu có) và nghĩa tiếng Việt gắn vào mục đầu tiên;
 * chỉ có bản dịch (cụm từ, từ hiếm) thì tạo một mục chỉ có nghĩa tiếng Việt.
 * englishFound = false nghĩa là chưa có định nghĩa (nên thử tiếp dạng gốc của từ).
 */
export async function lookupExternal(word) {
  const [english, vietnamese] = await Promise.all([defineEnglish(word), translateVi(word)])
  let entries = english.entries
  if (!entries.length && vietnamese.text) {
    entries = [{
      headword: word, partOfSpeech: word.includes(' ') ? 'phrase' : 'other', phoneticUk: null, phoneticUs: null,
      audioUrlUk: null, audioUrlUs: null, definitionEn: null, example: null,
    }]
  }
  if (entries.length && vietnamese.text) entries[0] = { ...entries[0], meaningVi: vietnamese.text }
  return { entries, englishFound: english.entries.length > 0, failed: english.failed && !!vietnamese.failed }
}
