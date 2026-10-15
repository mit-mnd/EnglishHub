import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as db from '../../storage/db'
import * as profile from '../profile'
import * as vocabulary from '../vocabulary'
import { resetCooldowns } from '../dictionary'

const json = (body, status = 200) => ({ ok: status < 400, status, json: async () => body })

/** Mỗi nguồn: một đối tượng trả lời, hoặc 'down' (lỗi 522 như dictionaryapi.dev khi sập), hoặc 404. */
function mockSources({ dict = 'down', wikt = 404, mymemory = null } = {}) {
  const calls = []
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    const source = url.includes('dictionaryapi') ? 'dict' : url.includes('wiktionary') ? 'wikt' : 'mymemory'
    calls.push(source)
    const spec = { dict, wikt, mymemory }[source]
    if (spec === 'down') return json({}, 522)
    if (spec === 404 || spec == null) return json({}, 404)
    if (typeof spec === 'function') return json(spec(decodeURIComponent(url.split('q=')[1]?.split('&')[0] ?? '')))
    return json(spec)
  }))
  return calls
}

const translation = (text) => ({ responseStatus: 200, quotaFinished: false, responseData: { translatedText: text } })
const wiktionary = (pos, definition, example) => ({ en: [{ partOfSpeech: pos, definitions: [{ definition, examples: example ? [example] : [] }] }] })

beforeEach(() => {
  db.clearAll()
  resetCooldowns()
  profile.createProfile({ fullName: 'Tester', learningGoal: 'TOEIC', dailyGoalMinutes: 15, reminderEnabled: false })
})
afterEach(() => vi.unstubAllGlobals())

describe('tra từ trực tuyến', () => {
  it('Free Dictionary API sập -> vẫn có định nghĩa (Wiktionary) và nghĩa tiếng Việt, rồi dùng lại khi offline', async () => {
    const calls = mockSources({
      wikt: wiktionary('Noun', 'A gentle <a href="/wiki/breeze">breeze</a>.', 'A soft <b>zephyr</b> blew from the west.'),
      mymemory: translation('Làn gió nhẹ'),
    })
    const r = await vocabulary.lookup('zephyr')
    expect(r.source).toBe('DICTIONARY_API')
    expect(r.entries[0]).toMatchObject({
      headword: 'zephyr', partOfSpeech: 'noun', meaningVi: 'làn gió nhẹ',
      definitionEn: 'A gentle breeze.', example: 'A soft zephyr blew from the west.',
    })
    expect(calls).toEqual(expect.arrayContaining(['dict', 'wikt', 'mymemory']))

    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('offline') }))   // mất mạng
    const again = await vocabulary.lookup('zephyr')
    expect(again.source).toBe('LOCAL')
    expect(again.entries[0].meaningVi).toBe('làn gió nhẹ')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('dạng biến đổi (planners) -> dùng nghĩa của từ gốc, không phải "plural of ..."', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url) => {
      if (url.includes('dictionaryapi')) return json({}, 522)
      if (url.includes('wiktionary')) return json(url.endsWith('/planners') ? wiktionary('Noun', 'plural of planner') : wiktionary('Noun', 'One who plans.'))
      const q = decodeURIComponent(url.split('q=')[1].split('&')[0])
      return json(translation(q === 'planners' ? 'những người lập kế hoạch' : 'người lập kế hoạch'))
    }))
    const r = await vocabulary.lookup('planners')
    expect(r).toMatchObject({ matchedHeadword: 'planner', source: 'DICTIONARY_API' })
    expect(r.entries[0]).toMatchObject({ definitionEn: 'One who plans.', meaningVi: 'người lập kế hoạch' })
  })

  it('cụm từ không có trong từ điển -> vẫn có nghĩa tiếng Việt (mục kiểu "cụm từ"), lưu được vào sổ', async () => {
    mockSources({ mymemory: translation('Giải pháp dựa trên tự nhiên') })
    const r = await vocabulary.lookup('nature-based solutions')
    expect(r.entries).toHaveLength(1)
    expect(r.entries[0]).toMatchObject({ partOfSpeech: 'phrase', meaningVi: 'giải pháp dựa trên tự nhiên', definitionEn: null })
    expect(vocabulary.saveWord({ wordId: r.entries[0].id }).id).toBeTruthy()
  })

  it('mọi nguồn đều lỗi -> báo offline (để giao diện cho thử lại), không phải "không có từ"; nguồn lỗi được tạm bỏ qua', async () => {
    const calls = mockSources({ dict: 'down', wikt: 'down', mymemory: 'down' })
    const r = await vocabulary.lookup('zzxqword')
    expect(r).toMatchObject({ source: 'NONE', offline: true, entries: [] })
    const before = calls.length
    await vocabulary.lookup('zzxqother')
    expect(calls.length).toBe(before)   // đang trong thời gian chờ nên không gọi lại nguồn đã lỗi
  })

  it('từ thật sự không tồn tại -> không phải offline', async () => {
    mockSources({ mymemory: (q) => translation(q) })   // dịch vụ trả lại nguyên văn = không dịch được
    const r = await vocabulary.lookup('zzxqword')
    expect(r).toMatchObject({ source: 'NONE', offline: false, entries: [] })
  })

  it('từ đã lưu cache từ trước mà chưa có nghĩa tiếng Việt -> được bổ sung một lần', async () => {
    db.update('customWords', () => [{
      id: 1_000_001, headword: 'quixotic', partOfSpeech: 'adjective', definitionEn: 'Able to recover.', meaningVi: '', source: 'DICTIONARY_API',
    }])
    mockSources({ mymemory: translation('Kiên cường') })
    const r = await vocabulary.lookup('quixotic')
    expect(r.entries[0].meaningVi).toBe('kiên cường')
    expect(db.read('customWords')[0]).toMatchObject({ meaningVi: 'kiên cường', viTried: true })
  })

  it('hết hạn mức dịch -> báo lỗi dịch vụ thay vì hiện nội dung cảnh báo của dịch vụ', async () => {
    mockSources({ mymemory: { responseStatus: 429, quotaFinished: true, responseData: { translatedText: 'MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS' } } })
    const r = await vocabulary.translate('Coastal cities are vulnerable.')
    expect(r).toMatchObject({ translation: null, offline: true })
  })
})

describe('dịch đoạn văn', () => {
  it('dịch cả câu, cache lại trong phiên và kiểm tra đầu vào', async () => {
    mockSources({ mymemory: translation('Các thành phố ven biển rất dễ bị tổn thương.') })
    const text = 'Coastal cities are particularly vulnerable.'
    expect(await vocabulary.translate(text)).toMatchObject({ translation: 'Các thành phố ven biển rất dễ bị tổn thương.', offline: false, text })
    expect(decodeURIComponent(vi.mocked(fetch).mock.calls[0][0])).toContain(`q=${text}&`)   // gửi nguyên văn, không làm mất chữ
    expect(await vocabulary.translate('  Rising   sea\nlevels. ')).toMatchObject({ text: 'Rising sea levels.' })   // gộp khoảng trắng
    await vocabulary.translate(text)
    expect(fetch).toHaveBeenCalledTimes(2)   // câu thứ nhất (1 lần, lần sau lấy từ cache) + câu thứ hai
    await expect(vocabulary.translate('12345')).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
    await expect(vocabulary.translate('a'.repeat(301))).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
  })
})
