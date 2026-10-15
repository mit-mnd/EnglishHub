/**
 * Test tích hợp: chạy trọn luồng của một người học trên các service thật + localStorage (jsdom),
 * tương đương bộ smoke test end-to-end của bản backend cũ.
 */
import { beforeEach, describe, expect, it } from 'vitest'
import * as db from '../../storage/db'
import { setNow } from '../../clock'
import { lessons, placementTest, exercises, getVocabularySet, vocabularySets } from '../../content'
import * as profile from '../profile'
import * as placement from '../placement'
import * as path from '../path'
import * as progress from '../progress'
import * as exercisesSvc from '../exercises'
import * as vocabulary from '../vocabulary'
import * as flashcards from '../flashcards'
import * as games from '../games'
import * as dashboard from '../dashboard'
import * as notifications from '../notifications'
import * as toeic from '../toeic'
import * as sentences from '../sentences'
import * as skills from '../skills'

const correctAnswer = (q) => {
  switch (q.type) {
    case 'SINGLE_CHOICE': return { selectedOptionIds: [q.options.find((o) => o.correct).id] }
    case 'MULTIPLE_CHOICE': return { selectedOptionIds: q.options.filter((o) => o.correct).map((o) => o.id) }
    case 'FILL_BLANK': return { textAnswers: JSON.parse(q.answerKey).map((a) => a[0]) }
    case 'MATCHING': return { matches: Object.fromEntries(q.options.map((o) => [o.id, o.matchText])) }
    case 'ORDERING': return { orderedOptionIds: [...q.options].sort((a, b) => a.correctOrder - b.correctOrder).map((o) => o.id) }
    default: return {}
  }
}

const newProfile = (extra = {}) => profile.createProfile({
  fullName: 'Trần Thị Bình', learningGoal: 'TOEIC', dailyGoalMinutes: 15, reminderEnabled: true, reminderTime: '19:00', ...extra,
})

beforeEach(() => {
  db.clearAll()
  setNow(null)
})

describe('hồ sơ', () => {
  it('chưa khai báo thì các chức năng học báo PROFILE_REQUIRED', () => {
    expect(() => placement.getInfo()).toThrowError(expect.objectContaining({ code: 'PROFILE_REQUIRED' }))
  })

  it('validate khi khai báo, tạo thông báo chào mừng, sửa được hồ sơ', () => {
    setNow(new Date(2026, 9, 14, 8, 0))   // trước giờ nhắc học -> chỉ có thông báo chào mừng
    expect(() => profile.createProfile({ fullName: ' ', learningGoal: 'TOEIC', dailyGoalMinutes: 15 }))
      .toThrowError(expect.objectContaining({ code: 'VALIDATION_ERROR' }))
    const p = newProfile({ levelId: 3 })
    expect(p.currentLevel.code).toBe('INTERMEDIATE')
    expect(notifications.unreadCount().count).toBe(1)
    expect(profile.updateProfile({ dailyGoalMinutes: 30 }).dailyGoalMinutes).toBe(30)
    expect(() => profile.updateProfile({ dailyGoalMinutes: 1 })).toThrow()
  })
})

describe('xếp lớp -> lộ trình', () => {
  it('làm đúng hết: trình độ cao nhất, tạo lộ trình + thông báo; nộp lại idempotent', () => {
    newProfile()
    const attempt = placement.startOrResume()
    expect(attempt.questions).toHaveLength(placementTest.questions.length)
    expect(attempt.questions[0].options[0].correct).toBeUndefined()   // không lộ đáp án
    expect(placement.startOrResume().attemptId).toBe(attempt.attemptId)   // tiếp tục bài dở

    placement.saveAnswer(attempt.attemptId, { questionId: placementTest.questions[0].id, ...correctAnswer(placementTest.questions[0]) })
    expect(() => placement.saveAnswer(attempt.attemptId, { questionId: 999999, selectedOptionIds: [1] }))
      .toThrowError(expect.objectContaining({ code: 'QUESTION_NOT_IN_TEST' }))
    const result = placement.submit(attempt.attemptId, placementTest.questions.map((q) => ({ questionId: q.id, ...correctAnswer(q) })))
    expect(result.percentage).toBe(100)
    expect(result.level.code).toBe('EXAM_PREP')
    expect(result.correctCount).toBe(placementTest.questions.length)
    expect(placement.submit(attempt.attemptId, []).percentage).toBe(100)
    expect(profile.getProfile().currentLevel.code).toBe('EXAM_PREP')
    expect(path.getPath()).not.toBeNull()
    expect(notifications.listNotifications().items.some((n) => n.type === 'PATH_UPDATED')).toBe(true)
  })

  it('bỏ trống: trình độ thấp nhất, kỹ năng yếu được ưu tiên', () => {
    newProfile({ learningGoal: 'GENERAL' })
    const attempt = placement.startOrResume()
    const result = placement.submit(attempt.attemptId, [])
    expect(result.level.code).toBe('FOUNDATION')
    const p = path.getPath()
    expect(p.items[0].status).toBe('AVAILABLE')
    expect(p.source).toBe('PLACEMENT')
  })
})

describe('học bài & lộ trình', () => {
  it('heartbeat cộng thời gian học, hoàn thành bài cập nhật lộ trình và streak', () => {
    newProfile({ levelId: 1, learningGoal: 'GENERAL' })
    let p = path.generatePath({})
    const first = p.items[0]
    expect(() => progress.heartbeat(first.lesson.id, { seconds: 500 })).toThrow()
    progress.heartbeat(first.lesson.id, { seconds: 90, progressPercent: 40 })
    p = path.getPath()
    expect(p.items[0].status).toBe('IN_PROGRESS')
    const done = progress.complete(first.lesson.id, {})
    expect(done.firstCompletion).toBe(true)
    expect(progress.complete(first.lesson.id, {}).firstCompletion).toBe(false)
    p = path.getPath()
    expect(p.items[0].status).toBe('COMPLETED')
    expect(p.completedItems).toBe(1)
    const d = dashboard.getDashboard('7d')
    expect(d.streak.current).toBe(1)
    expect(d.todayProgress.studyMinutes).toBe(2)
    expect(d.totals.lessonsCompleted).toBe(1)
    expect(d.series).toHaveLength(7)
  })

  it('điều chỉnh lộ trình: bỏ qua, học lại, sắp xếp, thêm bài trùng bị chặn', () => {
    newProfile({ levelId: 1, learningGoal: 'GENERAL' })
    const p = path.generatePath({})
    const [a, b] = p.items
    expect(path.skipItem(a.id).items.find((i) => i.id === a.id).status).toBe('SKIPPED')
    expect(path.unskipItem(a.id).items.find((i) => i.id === a.id).status).not.toBe('SKIPPED')
    expect(path.reorder([b.id, a.id, ...p.items.slice(2).map((i) => i.id)]).items[0].id).toBe(b.id)
    expect(() => path.addLesson(a.lesson.id)).toThrowError(expect.objectContaining({ code: 'LESSON_ALREADY_IN_PATH' }))
    const extra = lessons.find((l) => !p.items.some((i) => i.lesson.id === l.id))
    expect(path.addLesson(extra.id).items.at(-1).custom).toBe(true)
  })
})

describe('bài tập trong bài học', () => {
  it('chấm từng câu, không chấm lại, nộp đạt thì hoàn thành bài', async () => {
    newProfile({ levelId: 2 })
    const test = exercises.find((t) => t.questions.every((q) => !['ESSAY', 'SPEAKING'].includes(q.type)))
    const { attempt } = exercisesSvc.start(test.id)
    for (const q of test.questions) {
      const r = await exercisesSvc.check(attempt.attemptId, { questionId: q.id, ...correctAnswer(q) })
      expect(r.correct, q.type).toBe(true)
    }
    await expect(exercisesSvc.check(attempt.attemptId, { questionId: test.questions[0].id, ...correctAnswer(test.questions[0]) }))
      .rejects.toMatchObject({ code: 'ANSWER_ALREADY_CHECKED' })
    const result = exercisesSvc.finish(attempt.attemptId)
    expect(result).toMatchObject({ percentage: 100, passed: true, lessonCompleted: true })
    expect(exercisesSvc.listForLesson(test.lessonId)[0]).toMatchObject({ passed: true, attempts: 1 })
  })

  it('câu tự luận được chấm bằng bộ chấm bài viết', async () => {
    newProfile({ levelId: 2 })
    const test = exercises.find((t) => t.questions.some((q) => q.type === 'ESSAY'))
    const q = test.questions.find((x) => x.type === 'ESSAY')
    const { attempt } = exercisesSvc.start(test.id)
    const r = await exercisesSvc.check(attempt.attemptId, { questionId: q.id, answerText: 'yesterday i goed to the park with my freind .' })
    expect(r.writing.issues.length).toBeGreaterThan(0)
    expect(r.score).toBeLessThan(q.points)
  })
})

describe('từ vựng & flashcard', () => {
  it('thêm bộ từ, ôn thẻ theo SM-2, tạm ẩn', () => {
    newProfile()
    const set = vocabularySets[0]
    expect(vocabulary.addSetToNotebook(set.id).added).toBe(set.wordIds.length)
    expect(vocabulary.addSetToNotebook(set.id)).toEqual({ added: 0, alreadyInNotebook: set.wordIds.length })
    const s = flashcards.summary()
    expect(s.newCards).toBe(set.wordIds.length)
    expect(s.forecast).toHaveLength(7)
    const q = flashcards.queue(10)
    expect(q.cards[0].intervals.GOOD).toBe(86400)
    const good = flashcards.review(q.cards[0].userWordId, 'GOOD', 4000)
    expect(good.card).toMatchObject({ intervalDays: 1, status: 'LEARNING' })
    expect(flashcards.review(q.cards[1].userWordId, 'AGAIN', 3000).relearnInSession).toBe(true)
    expect(vocabulary.setSuspended(q.cards[2].userWordId, true).status).toBe('SUSPENDED')
    expect(() => flashcards.review(q.cards[2].userWordId, 'GOOD')).toThrow()
    expect(flashcards.summary().reviewedToday).toBe(2)
    expect(dashboard.getDashboard().totals.wordsLearning).toBe(2)
  })

  it('không giới hạn thẻ mới theo ngày: học hết lô này vẫn còn thẻ mới cho lô sau', () => {
    newProfile()
    for (const set of vocabularySets.slice(0, 4)) vocabulary.addSetToNotebook(set.id)
    const total = flashcards.summary().newCards
    expect(total).toBeGreaterThan(30)
    let learned = 0
    for (let batch = 0; batch < 3; batch++) {
      const q = flashcards.queue(20)
      expect(q.cards.length).toBeGreaterThan(0)
      q.cards.forEach((c) => { flashcards.review(c.userWordId, 'GOOD', 3000); learned++ })
    }
    expect(learned).toBeGreaterThan(20)                 // vượt mốc 20 thẻ/ngày cũ ngay trong hôm nay
    const s = flashcards.summary()
    expect(s.newAvailableToday).toBe(total - learned)   // thẻ mới còn lại đều sẵn sàng
  })

  it('tra từ trong kho nội bộ (kể cả dạng số nhiều / quá khứ) và lưu vào sổ', async () => {
    newProfile()
    const r = await vocabulary.lookup('Souvenirs.')
    expect(r).toMatchObject({ source: 'LOCAL', matchedHeadword: 'souvenir' })
    const saved = vocabulary.saveWord({ wordId: r.entries[0].id, contextSentence: 'I bought souvenirs.' })
    expect(vocabulary.saveWord({ wordId: r.entries[0].id }).id).toBe(saved.id)   // idempotent
    expect(vocabulary.savedHeadwords()).toContain('souvenir')
    expect(vocabulary.listNotebook({ keyword: 'quà' }).totalElements).toBe(1)
    await expect(vocabulary.lookup('ab1cd')).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
  })
})

describe('luyện nói / trò chơi / TOEIC / mẫu câu / nhắc học', () => {
  it('chấm phát âm từ file WAV + văn bản nhận dạng', async () => {
    newProfile()
    const sampleRate = 16000
    const dataSize = sampleRate * 2 * 2   // 2 giây
    const buf = new DataView(new ArrayBuffer(44 + dataSize))
    const w = (o, s) => [...s].forEach((c, i) => buf.setUint8(o + i, c.charCodeAt(0)))
    w(0, 'RIFF'); w(8, 'WAVE'); buf.setUint32(28, sampleRate * 2, true); buf.setUint32(40, dataSize, true)
    const blob = new Blob([buf.buffer], { type: 'audio/wav' })
    const r = await skills.submitSpeaking({ audio: blob, referenceText: 'Nice to meet you.', clientTranscript: 'nice to meet you' })
    expect(r.pronunciationScore).toBeGreaterThanOrEqual(90)
    await expect(skills.submitSpeaking({ audio: new Blob(['not wav at all, not a header ok?']), referenceText: 'Hi' }))
      .rejects.toMatchObject({ code: 'INVALID_AUDIO' })
  })

  it('trò chơi: bộ từ không trùng, kỷ lục cá nhân, validate', () => {
    newProfile({ levelId: 2 })
    const ws = games.gameWords('SCRAMBLE', 8)
    expect(ws.length).toBeGreaterThanOrEqual(4)
    expect(ws.every((w) => /^[A-Za-z]{3,12}$/.test(w.headword))).toBe(true)
    expect(games.submitResult({ gameType: 'SCRAMBLE', score: 75, correctCount: 6, totalCount: 8, durationSeconds: 90 }).personalBest).toBe(true)
    expect(games.submitResult({ gameType: 'SCRAMBLE', score: 50, correctCount: 4, totalCount: 8, durationSeconds: 60 }))
      .toMatchObject({ personalBest: false, bestScore: 75 })
    expect(() => games.submitResult({ gameType: 'SCRAMBLE', score: 50, correctCount: 9, totalCount: 8, durationSeconds: 60 })).toThrow()
    expect(games.gameStats().find((s) => s.gameType === 'SCRAMBLE')).toMatchObject({ plays: 2, bestScore: 75 })
  })

  it('TOEIC: luyện theo Part, ưu tiên nhóm chưa làm, đề thi thử có điểm ước lượng', async () => {
    newProfile()
    const set = await toeic.practiceSet(5, 10)
    expect(set.groups).toHaveLength(10)
    const answers = Object.fromEntries(set.groups.map((g) => [g.questions[0].id, g.questions[0].answer]))
    const r = await toeic.submitSession({ mode: 'PRACTICE', part: 5, groupIds: set.groups.map((g) => g.id), answers, durationSeconds: 120 })
    expect(r).toMatchObject({ correct: 10, total: 10, percent: 100 })
    const next = await toeic.practiceSet(5, 10)
    expect(next.groups.some((g) => set.groups.some((x) => x.id === g.id))).toBe(false)   // nhóm chưa làm trước

    const test = await toeic.miniTest()
    expect(new Set(test.groups.map((g) => g.part))).toEqual(new Set([2, 3, 4, 5, 6, 7]))
    const tr = await toeic.submitSession({ mode: 'TEST', groupIds: test.groups.map((g) => g.id), answers: {}, durationSeconds: 600 })
    expect(tr.estimate).toEqual({ listening: 5, reading: 5, total: 10 })
    const o = await toeic.overview()
    // Nhóm đã làm tính cả lượt luyện (10) lẫn các câu Part 5 trong đề thi thử
    const part5 = o.parts.find((p) => p.part === 5)
    expect(part5).toMatchObject({ sessions: 1, bestPercent: 100 })
    expect(part5.completedGroups).toBeGreaterThanOrEqual(10)
    expect(o.miniTest.attempts).toBe(1)
  })

  it('mẫu câu: lưu và đánh dấu đã luyện', async () => {
    newProfile()
    const [topic] = await sentences.listTopics()
    const detail = await sentences.getTopic(topic.id)
    const p = detail.patterns[0]
    sentences.setSaved(p.id, true)
    sentences.markPracticed(p.id)
    expect((await sentences.savedPatterns())[0].id).toBe(p.id)
    expect((await sentences.listTopics())[0]).toMatchObject({ savedCount: 1, practicedCount: 1 })
  })

  it('nhắc học: 1 lần/ngày sau giờ nhắc nếu chưa đạt mục tiêu', () => {
    setNow(new Date(2026, 9, 14, 8, 0))
    newProfile({ reminderTime: '19:00' })
    notifications.markAllRead()
    expect(notifications.unreadCount().count).toBe(0)
    setNow(new Date(2026, 9, 14, 19, 30))
    expect(notifications.unreadCount().count).toBe(1)
    expect(notifications.unreadCount().count).toBe(1)   // không nhắc trùng
    expect(notifications.listNotifications().items[0].type).toBe('STUDY_REMINDER')
  })
})

describe('sao lưu', () => {
  it('xuất ra JSON rồi nhập lại được nguyên trạng', () => {
    newProfile()
    vocabulary.addSetToNotebook(getVocabularySet(vocabularySets[0].id).id)
    const backup = JSON.parse(JSON.stringify(db.exportAll()))
    db.clearAll()
    expect(profile.getProfile()).toBeNull()
    db.importAll(backup)
    expect(profile.getProfile().fullName).toBe('Trần Thị Bình')
    expect(db.read('notebook').length).toBe(vocabularySets[0].wordIds.length)
    expect(() => db.importAll({ hello: 'world' })).toThrow()
  })
})
