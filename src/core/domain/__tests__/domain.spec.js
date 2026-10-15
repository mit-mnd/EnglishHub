import { describe, expect, it } from 'vitest'
import { grade, normalizeAnswer } from '../grader'
import { newCardState, preview, schedule } from '../sm2'
import { evaluateWriting } from '../writingEvaluator'
import { alignWords, scorePronunciation } from '../pronunciation'
import { planDates, prioritizeWeakSkills, refreshStatuses } from '../pathPlanner'
import { decideReminders } from '../reminderPolicy'
import { resolveLevel } from '../placement'
import { candidates } from '../lemmatize'
import { validateQuestion } from '../questionValidator'

const opt = (id, content, correct = false, extra = {}) => ({ id, content, correct, ...extra })

describe('grader', () => {
  it('một đáp án: đúng được trọn điểm, chọn 2 phương án là sai', () => {
    const q = { type: 'SINGLE_CHOICE', options: [opt(1, 'am'), opt(2, 'is', true), opt(3, 'are')] }
    expect(grade(q, { selectedOptionIds: [2] }, 2)).toEqual({ graded: true, correct: true, score: 2 })
    expect(grade(q, { selectedOptionIds: [1] }, 2)).toEqual({ graded: true, correct: false, score: 0 })
    expect(grade(q, { selectedOptionIds: [1, 2] }, 2).correct).toBe(false)
  })

  it('nhiều đáp án: phải chọn đúng tập', () => {
    const q = { type: 'MULTIPLE_CHOICE', options: [opt(1, 'a', true), opt(2, 'b', true), opt(3, 'c')] }
    expect(grade(q, { selectedOptionIds: [2, 1] }, 2).correct).toBe(true)
    expect(grade(q, { selectedOptionIds: [1] }, 2).correct).toBe(false)
    expect(grade(q, { selectedOptionIds: [1, 2, 3] }, 2).correct).toBe(false)
  })

  it('điền từ: chấm một phần, chuẩn hóa hoa thường / dấu nháy / dấu câu', () => {
    const q = { type: 'FILL_BLANK', answerKey: '[["drinks"],["is drinking","\'s drinking"]]' }
    expect(grade(q, { textAnswers: ['  Drinks ', '’s   drinking.'] }, 2)).toEqual({ graded: true, correct: true, score: 2 })
    expect(grade(q, { textAnswers: ['drinks', 'drink'] }, 2)).toEqual({ graded: true, correct: false, score: 1 })
    expect(normalizeAnswer('  Hello   World!! ')).toBe('hello world')
  })

  it('nối cặp: chấm một phần', () => {
    const q = { type: 'MATCHING', options: [opt(1, 'always', false, { matchText: 'luôn luôn' }), opt(2, 'never', false, { matchText: 'không bao giờ' })] }
    expect(grade(q, { matches: { 1: 'luôn luôn', 2: 'thỉnh thoảng' } }, 2)).toEqual({ graded: true, correct: false, score: 1 })
  })

  it('sắp xếp: so theo nội dung nên hai chữ "the" đổi chỗ vẫn đúng', () => {
    const q = {
      type: 'ORDERING',
      options: [opt(5, 'fish', false, { correctOrder: 5 }), opt(4, 'the', false, { correctOrder: 4 }), opt(3, 'ate', false, { correctOrder: 3 }),
        opt(2, 'cat', false, { correctOrder: 2 }), opt(1, 'the', false, { correctOrder: 1 })],
    }
    expect(grade(q, { orderedOptionIds: [4, 2, 3, 1, 5] }, 2).correct).toBe(true)
    expect(grade(q, { orderedOptionIds: [2, 1, 3, 4, 5] }, 2).correct).toBe(false)
  })

  it('tự luận không chấm tự động; bỏ trống = 0 điểm', () => {
    expect(grade({ type: 'ESSAY' }, {}, 2).graded).toBe(false)
    expect(grade({ type: 'SINGLE_CHOICE', options: [opt(1, 'x', true)] }, null, 2)).toEqual({ graded: true, correct: false, score: 0 })
  })
})

describe('SM-2', () => {
  it('chọn "Được" liên tục theo khoảng SM-2 cổ điển', () => {
    let s = newCardState()
    const intervals = []
    for (let i = 0; i < 5; i++) {
      s = schedule(s, 'GOOD').state
      intervals.push(s.intervalDays)
    }
    expect(intervals).toEqual([1, 6, 15, 38, 95])
    expect(s.easeFactor).toBe(2.5)
    expect(s.status).toBe('MASTERED')
  })

  it('"Quên" đặt lại số lần, tính 1 lần quên, học lại sau 10 phút', () => {
    const review = { easeFactor: 2.5, intervalDays: 15, repetitions: 3, lapseCount: 0, status: 'REVIEW' }
    const r = schedule(review, 'AGAIN')
    expect(r.delayMs).toBe(10 * 60 * 1000)
    expect(r.state).toMatchObject({ repetitions: 0, lapseCount: 1, status: 'LEARNING', easeFactor: 1.96 })
    expect(schedule(newCardState(), 'AGAIN').state.lapseCount).toBe(0)
  })

  it('EF không dưới 1,3; "Khó" luôn tăng ít nhất 1 ngày; nút sắp theo độ khó', () => {
    let s = { easeFactor: 1.4, intervalDays: 10, repetitions: 4, lapseCount: 2, status: 'REVIEW' }
    for (let i = 0; i < 5; i++) s = schedule(s, 'AGAIN').state
    expect(s.easeFactor).toBe(1.3)
    expect(schedule({ easeFactor: 1.3, intervalDays: 2, repetitions: 3, lapseCount: 0, status: 'LEARNING' }, 'HARD').state.intervalDays).toBe(3)
    const p = preview({ easeFactor: 2.5, intervalDays: 6, repetitions: 2, lapseCount: 0, status: 'REVIEW' })
    expect(p.AGAIN < p.HARD && p.HARD < p.GOOD && p.GOOD < p.EASY).toBe(true)
  })
})

describe('chấm bài viết', () => {
  it('phát hiện lỗi thường gặp và đưa ra bản sửa', () => {
    const f = evaluateWriting({ text: 'last weekend i goed to a beach with my freind. She have a umbrella and we swimmed .' })
    expect(f.issues.map((i) => i.original)).toEqual(expect.arrayContaining(['l', 'i', 'goed', 'freind', 'have', 'a', 'swimmed', ' .']))
    expect(f.correctedText).toBe('Last weekend I went to a beach with my friend. She has an umbrella and we swam.')
  })

  it('không báo lỗi câu đúng', () => {
    expect(evaluateWriting({ text: 'Does she have an hour? I am a university student and it is an honest answer.' }).issues).toEqual([])
  })

  it('lặp từ, khoảng trắng, thiếu dấu chấm cuối', () => {
    const issues = evaluateWriting({ text: 'I like the the park,it is  big' }).issues
    expect(issues.map((i) => i.type)).toEqual(expect.arrayContaining(['GRAMMAR', 'PUNCTUATION', 'STYLE']))
    expect(issues.some((i) => i.suggestion === 'big.')).toBe(true)
  })

  it('chấm theo yêu cầu số từ; bài tốt điểm cao; bài trống 0 điểm', () => {
    const short = evaluateWriting({ text: 'I went to the park. It was fun.', minWords: 80, maxWords: 120 })
    expect(short.wordCount).toBe(8)
    expect(short.criteria.taskAchievement).toBeLessThan(20)
    expect(short.improvements.some((s) => s.includes('8/80'))).toBe(true)
    const good = evaluateWriting({
      text: 'Last weekend was wonderful. First, I visited my grandparents in the countryside. '
        + 'Then we cooked a delicious lunch together and talked about our family. '
        + 'In the afternoon, my cousins and I went fishing near the river, but we did not catch anything. '
        + 'However, we laughed a lot because my little brother fell into the water. '
        + 'Finally, we returned home in the evening, tired but happy. '
        + 'I hope I can visit them again soon because the fresh air and quiet villages always help me relax '
        + 'after a busy week at school.',
      minWords: 80, maxWords: 120,
    })
    expect(good.issues).toEqual([])
    expect(good.score).toBeGreaterThanOrEqual(90)
    expect(evaluateWriting({ text: '   ', minWords: 50 }).score).toBe(0)
  })
})

describe('chấm phát âm', () => {
  it('so khớp từng từ: đúng / sai / bỏ sót, giữ từ hiển thị', () => {
    const a = alignWords('Hello, my name is Lan. I am a student.', 'hello my name is land i am student')
    expect(a.words.map((w) => w.status)).toEqual(['CORRECT', 'CORRECT', 'CORRECT', 'CORRECT', 'MISPRONOUNCED', 'CORRECT', 'CORRECT', 'OMITTED', 'CORRECT'])
    expect(a.words[4].word).toBe('Lan.')
    const c = alignWords("I'm from Vietnam.", 'I am from Vietnam')
    expect(c.words.every((w) => w.status === 'CORRECT') && c.inserted === 0).toBe(true)
  })

  it('đọc đúng điểm cao; thiếu từ bị trừ; không có văn bản nhận dạng thì chỉ ước lượng', () => {
    const perfect = scorePronunciation({ referenceText: 'Nice to meet you.', transcript: 'nice to meet you', durationMs: 2500 })
    expect(perfect).toMatchObject({ estimated: false, accuracyScore: 100, completenessScore: 100 })
    expect(perfect.pronunciationScore).toBeGreaterThanOrEqual(90)
    const missing = scorePronunciation({ referenceText: 'I am from Vietnam and I live in Hanoi.', transcript: 'I am from Vietnam', durationMs: 2500 })
    expect(missing.completenessScore).toBeLessThan(60)
    expect(missing.words.some((w) => w.word === 'Hanoi.' && w.errorType === 'OMISSION')).toBe(true)
    const est = scorePronunciation({ referenceText: 'Nice to meet you.', durationMs: 2200 })
    expect(est.estimated).toBe(true)
    expect(est.accuracyScore).toBeNull()
    expect(est.pronunciationScore).toBeLessThanOrEqual(80)
  })
})

describe('lộ trình', () => {
  it('ưu tiên kỹ năng yếu trong từng nhóm 4 bài, giữ thứ tự giữa các nhóm', () => {
    const items = ['R1', 'L1', 'R2', 'L2', 'R3', 'R4', 'L3', 'R5']
    expect(prioritizeWeakSkills(items, (x) => x[0], new Set(['L']))).toEqual(['L1', 'L2', 'R1', 'R2', 'L3', 'R3', 'R4', 'R5'])
  })

  it('xếp lịch theo quỹ phút/ngày; bài dài hơn quỹ ngày vẫn trọn 1 ngày', () => {
    expect(planDates([10, 10, 30, 5], (m) => m, 20, '2026-10-14')).toEqual(['2026-10-14', '2026-10-14', '2026-10-15', '2026-10-16'])
  })

  it('bài chưa xong đầu tiên là "học tiếp", bài đang học dở giữ nguyên', () => {
    const items = [{ status: 'COMPLETED' }, { status: 'LOCKED' }, { status: 'IN_PROGRESS' }, { status: 'AVAILABLE' }]
    refreshStatuses(items)
    expect(items.map((i) => i.status)).toEqual(['COMPLETED', 'AVAILABLE', 'IN_PROGRESS', 'LOCKED'])
  })
})

describe('nhắc học', () => {
  const base = {
    localTime: '20:30', reminderEnabled: true, reminderTime: '19:00', studyReminderSentToday: false, streakWarningSentToday: false,
    studyMinutesToday: 5, dailyGoalMinutes: 15, currentStreak: 3, studiedToday: false, dueCards: 4,
  }
  it('nhắc học + cảnh báo mất chuỗi; mỗi loại 1 lần/ngày; tắt nhắc thì im lặng', () => {
    expect(decideReminders(base).map((r) => r.type)).toEqual(['STUDY_REMINDER', 'STREAK'])
    expect(decideReminders(base)[0].link).toBe('/app/vocabulary/review')
    expect(decideReminders({ ...base, studyReminderSentToday: true, streakWarningSentToday: true })).toEqual([])
    expect(decideReminders({ ...base, localTime: '18:00' })).toEqual([])
    expect(decideReminders({ ...base, reminderEnabled: false })).toEqual([])
    expect(decideReminders({ ...base, studyMinutesToday: 15, studiedToday: true })).toEqual([])
  })
})

describe('xếp lớp, tra từ, kiểm tra câu hỏi', () => {
  const levels = [{ id: 1, placementMinPercent: 0 }, { id: 2, placementMinPercent: 21 }, { id: 3, placementMinPercent: 46 }]
  it('chọn level có ngưỡng lớn nhất không vượt %', () => {
    expect(resolveLevel(20.99, levels).id).toBe(1)
    expect(resolveLevel(21, levels).id).toBe(2)
    expect(resolveLevel(100, levels).id).toBe(3)
  })

  it('sinh dạng gốc của từ', () => {
    expect(candidates('souvenirs')).toEqual(['souvenirs', 'souvenir'])
    expect(candidates('stopped')).toEqual(['stopped', 'stopp', 'stop', 'stoppe'])
    expect(candidates('studies')).toContain('study')
  })

  it('validateQuestion bắt lỗi cấu trúc', () => {
    expect(validateQuestion({ id: 1, type: 'SINGLE_CHOICE', content: 'Q', points: 1, options: [opt(1, 'a', true), opt(2, 'b', true)] }))
      .toEqual(['Câu #1: câu một đáp án phải có đúng 1 phương án đúng'])
    expect(validateQuestion({ id: 2, type: 'FILL_BLANK', content: '{{1}} and {{2}}', points: 1, answerKey: '[["a"]]' })[0]).toContain('đủ 2')
    expect(validateQuestion({ id: 3, type: 'SPEAKING', content: 'Read', points: 1, answerKey: 'Hello.' })).toEqual([])
  })
})
