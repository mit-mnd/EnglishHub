// Smoke test end-to-end cho Giai đoạn 2 (Auth, User, Admin, Placement Test).
// Chạy khi backend đang chạy:   node scripts/smoke-test.mjs [baseUrl]
// Mặc định baseUrl = http://localhost:8080. Cần Node 18+.
// Script tạo 1 tài khoản học viên mới mỗi lần chạy (email ngẫu nhiên) và dùng tài khoản admin seed.

const BASE = (process.argv[2] ?? 'http://localhost:8080') + '/api/v1';
const ADMIN = { email: 'admin@englishhub.local', password: 'Admin@123' };

let passed = 0;
let failed = 0;

async function call(method, path, { body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}

/** WAV PCM 16 kHz mono im lặng dài `ms` (đủ để server đọc header và tính thời lượng). */
function silentWav(ms, sampleRate = 16000) {
  const dataSize = (sampleRate * 2 * ms) / 1000
  const b = Buffer.alloc(44 + dataSize)
  b.write('RIFF', 0); b.writeUInt32LE(36 + dataSize, 4); b.write('WAVE', 8); b.write('fmt ', 12)
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(sampleRate, 24)
  b.writeUInt32LE(sampleRate * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36)
  b.writeUInt32LE(dataSize, 40)
  return b
}

async function upload(path, fields, fileBuffer, token) {
  const form = new FormData()
  form.append('audio', new Blob([fileBuffer], { type: 'audio/wav' }), 'recording.wav')
  Object.entries(fields).forEach(([k, v]) => v != null && form.append(k, String(v)))
  const res = await fetch(BASE + path, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
  const text = await res.text()
  return { status: res.status, data: text ? JSON.parse(text) : null }
}

function check(name, condition, detail) {
  if (condition) {
    passed++;
    console.log(`  ✔ ${name}`);
  } else {
    failed++;
    console.log(`  ✘ ${name}`, detail !== undefined ? JSON.stringify(detail).slice(0, 400) : '');
  }
}

async function main() {
  const email = `learner.${Date.now()}@example.com`;
  const password = 'Hello1234';

  console.log('\n[Auth]');
  let r = await call('POST', '/auth/register', { body: { email: 'bad', password: 'short', fullName: '' } });
  check('register validate -> 400 VALIDATION_ERROR', r.status === 400 && r.data.code === 'VALIDATION_ERROR' && r.data.errors.length >= 3, r.data);

  r = await call('POST', '/auth/register', { body: { email: email.toUpperCase(), password, fullName: 'Trần Thị Bình', learningGoal: 'IELTS' } });
  check('register -> 201 + tokens', r.status === 201 && r.data.accessToken && r.data.refreshToken, r.data);
  check('email được chuẩn hóa chữ thường, role LEARNER', r.data.user?.email === email && r.data.user.roles.join() === 'LEARNER', r.data.user);
  check('tên tiếng Việt lưu đúng', r.data.user?.fullName === 'Trần Thị Bình', r.data.user);

  r = await call('POST', '/auth/register', { body: { email, password, fullName: 'Dup' } });
  check('register trùng email -> 409 EMAIL_ALREADY_EXISTS', r.status === 409 && r.data.code === 'EMAIL_ALREADY_EXISTS', r.data);

  r = await call('POST', '/auth/login', { body: { email, password: 'Wrong1234' } });
  check('login sai mật khẩu -> 401 INVALID_CREDENTIALS', r.status === 401 && r.data.code === 'INVALID_CREDENTIALS', r.data);

  r = await call('POST', '/auth/login', { body: { email, password } });
  check('login -> 200', r.status === 200 && r.data.tokenType === 'Bearer' && r.data.expiresIn === 900, r.data);
  let access = r.data.accessToken;
  let refresh = r.data.refreshToken;

  r = await call('GET', '/users/me');
  check('không có token -> 401 UNAUTHORIZED', r.status === 401 && r.data.code === 'UNAUTHORIZED', r.data);
  r = await call('GET', '/users/me', { token: 'abc.def.ghi' });
  check('token rác -> 401 INVALID_TOKEN', r.status === 401 && r.data.code === 'INVALID_TOKEN', r.data);

  console.log('\n[Refresh token rotation]');
  r = await call('POST', '/auth/refresh', { body: { refreshToken: refresh } });
  check('refresh -> token mới', r.status === 200 && r.data.refreshToken !== refresh, r.data);
  const oldRefresh = refresh;
  access = r.data.accessToken;
  refresh = r.data.refreshToken;
  r = await call('POST', '/auth/refresh', { body: { refreshToken: oldRefresh } });
  check('dùng lại refresh token cũ -> 401 (phát hiện reuse)', r.status === 401 && r.data.code === 'INVALID_REFRESH_TOKEN', r.data);
  r = await call('POST', '/auth/refresh', { body: { refreshToken: refresh } });
  check('sau reuse, mọi phiên bị thu hồi -> token mới nhất cũng 401', r.status === 401, r.data);
  r = await call('POST', '/auth/login', { body: { email, password } });
  access = r.data.accessToken;
  refresh = r.data.refreshToken;

  console.log('\n[User profile]');
  r = await call('GET', '/users/me', { token: access });
  check('GET /users/me', r.status === 200 && r.data.email === email && r.data.currentLevel === null, r.data);
  r = await call('PUT', '/users/me', {
    token: access,
    body: { fullName: 'Trần Thị Bình', learningGoal: 'TOEIC', dailyGoalMinutes: 30, timeZone: 'Asia/Ho_Chi_Minh',
      reminderEnabled: true, reminderTime: '20:30', emailNotificationEnabled: false },
  });
  check('cập nhật hồ sơ', r.status === 200 && r.data.learningGoal === 'TOEIC' && r.data.reminderTime === '20:30', r.data);
  r = await call('PUT', '/users/me', {
    token: access,
    body: { fullName: 'X', learningGoal: 'TOEIC', dailyGoalMinutes: 30, timeZone: 'Mars/Base', reminderEnabled: true, emailNotificationEnabled: false },
  });
  check('múi giờ sai -> 400', r.status === 400, r.data);
  r = await call('GET', '/admin/users', { token: access });
  check('learner gọi API admin -> 403 FORBIDDEN', r.status === 403 && r.data.code === 'FORBIDDEN', r.data);

  console.log('\n[Placement Test]');
  r = await call('GET', '/placement/test', { token: access });
  check('thông tin bài test', r.status === 200 && r.data.totalQuestions === 20 && r.data.durationMinutes === 20, r.data);

  r = await call('POST', '/placement/attempts', { token: access });
  check('bắt đầu làm bài', r.status === 200 && r.data.questions.length === 20 && r.data.expiresAt, r.data);
  const attempt = r.data;
  const leaked = JSON.stringify(attempt).match(/isCorrect|"correct"|answerKey|explanation/i);
  check('không lộ đáp án trong đề', !leaked, leaked);

  // Đáp án đúng của seed (theo thứ tự câu): dùng nội dung phương án để chọn
  const correctTexts = ['is', 'Green', 'am', 'small', 'play', 'went', 'at', 'A nurse', 'will stay', 'since',
    'do', 'Weaker teamwork', 'had been finished', 'effect', 'did he arrive'];
  // Trả lời đúng 15 câu đầu (8 câu 1đ + 4 câu 1.5đ + 3 câu 2đ = 20/30 = 66.67% -> INTERMEDIATE), bỏ trống 5 câu cuối
  const pick = (q, text) => ({ questionId: q.id, selectedOptionIds: [q.options.find((o) => o.content === text).id] });

  r = await call('PUT', `/placement/attempts/${attempt.attemptId}/answers`, { token: access, body: pick(attempt.questions[0], 'am') });
  check('autosave -> 204', r.status === 204, r.data);
  r = await call('PUT', `/placement/attempts/${attempt.attemptId}/answers`, {
    token: access, body: { questionId: attempt.questions[0].id, selectedOptionIds: [999999] } });
  check('option không thuộc câu hỏi -> 400', r.status === 400, r.data);

  r = await call('POST', '/placement/attempts', { token: access });
  check('gọi lại start -> tiếp tục đúng bài cũ + có câu đã lưu',
    r.data.attemptId === attempt.attemptId && Object.keys(r.data.savedAnswers).length === 1, r.data.savedAnswers);

  const answers = correctTexts.map((text, i) => pick(attempt.questions[i], text)); // ghi đè câu 1 bằng đáp án đúng
  r = await call('POST', `/placement/attempts/${attempt.attemptId}/submit`, { token: access, body: { answers } });
  check('nộp bài -> SUBMITTED', r.status === 200 && r.data.status === 'SUBMITTED', r.data);
  check('điểm 20/30 = 66.67%', Number(r.data.score) === 20 && Number(r.data.maxScore) === 30 && Number(r.data.percentage) === 66.67, r.data);
  check('xếp level INTERMEDIATE', r.data.level?.code === 'INTERMEDIATE', r.data.level);
  check('15 câu đúng / 20', r.data.correctCount === 15 && r.data.totalQuestions === 20, r.data);
  check('có điểm theo kỹ năng', r.data.skillScores.length >= 3, r.data.skillScores);
  check('review có lời giải', r.data.review[0].explanation && r.data.review[0].options.some((o) => o.correct), r.data.review[0]);

  r = await call('POST', `/placement/attempts/${attempt.attemptId}/submit`, { token: access, body: { answers: [] } });
  check('nộp lại -> trả kết quả cũ (idempotent)', r.status === 200 && Number(r.data.score) === 20, r.data);
  r = await call('PUT', `/placement/attempts/${attempt.attemptId}/answers`, { token: access, body: pick(attempt.questions[0], 'is') });
  check('lưu đáp án sau khi nộp -> 409', r.status === 409 && r.data.code === 'ATTEMPT_NOT_IN_PROGRESS', r.data);

  r = await call('GET', `/placement/attempts/${attempt.attemptId}/result`, { token: access });
  check('xem lại kết quả', r.status === 200 && r.data.level.code === 'INTERMEDIATE', r.data);
  r = await call('GET', '/placement/attempts?page=0&size=5', { token: access });
  check('lịch sử (phân trang)', r.status === 200 && r.data.totalElements === 1 && r.data.items.length === 1, r.data);
  r = await call('GET', '/placement/attempts?sort=user.passwordHash', { token: access });
  check('sort không hợp lệ -> 400', r.status === 400, r.data);
  r = await call('GET', '/users/me', { token: access });
  check('hồ sơ đã cập nhật level + placementCompletedAt',
    r.data.currentLevel?.code === 'INTERMEDIATE' && r.data.placementCompletedAt, r.data);
  const learnerId = r.data.id;

  console.log('\n[Lộ trình tự sinh sau xếp lớp]');
  r = await call('GET', '/learning-path', { token: access });
  check('có lộ trình PLACEMENT ngay sau khi nộp bài', r.status === 200 && r.data.source === 'PLACEMENT'
    && r.data.level.code === 'INTERMEDIATE' && r.data.items.length > 0, r.data);
  const firstItem = r.data.items[0];
  check('kỹ năng yếu (Đọc) được ưu tiên lên đầu nhóm', firstItem.lesson.skill === 'READING', firstItem);
  check('bài đầu AVAILABLE, các bài sau LOCKED, có lịch ngày học',
    firstItem.status === 'AVAILABLE' && r.data.items.slice(1).every((i) => i.status === 'LOCKED') && firstItem.plannedDate, r.data.items);

  console.log('\n[Khóa học & bài học]');
  r = await call('GET', '/courses', { token: access });
  check('danh sách khóa học', r.status === 200 && r.data.length >= 6 && r.data[0].lessonCount > 0, r.data);
  r = await call('GET', '/lessons/9', { token: access });
  check('bài nghe có audio + 9 câu phụ đề', r.status === 200 && r.data.mediaType === 'AUDIO'
    && r.data.mediaUrl.endsWith('.wav') && r.data.transcript.length === 9, r.data);
  const media = await fetch(BASE.replace('/api/v1', '') + r.data.mediaUrl, { headers: { Range: 'bytes=0-99' } });
  check('file audio phục vụ được và hỗ trợ tua (HTTP 206 Range)', media.status === 206
    && (media.headers.get('content-type') ?? '').includes('audio'), media.status);
  r = await call('GET', '/lessons?skill=READING&size=50', { token: access });
  check('tìm bài theo kỹ năng', r.status === 200 && r.data.items.every((l) => l.skill === 'READING') && r.data.totalElements >= 4, r.data);
  r = await call('GET', '/lessons/999999', { token: access });
  check('bài không tồn tại -> 404', r.status === 404, r.data);

  console.log('\n[Tiến độ học]');
  const lessonId = firstItem.lesson.id;
  r = await call('POST', `/lessons/${lessonId}/progress`, { token: access, body: { seconds: 90, progressPercent: 40 } });
  check('heartbeat -> IN_PROGRESS, cộng thời gian', r.status === 200 && r.data.status === 'IN_PROGRESS' && r.data.timeSpentSeconds === 90, r.data);
  r = await call('POST', `/lessons/${lessonId}/progress`, { token: access, body: { seconds: 500 } });
  check('heartbeat quá 120 giây -> 400', r.status === 400, r.data);
  r = await call('GET', '/learning-path', { token: access });
  check('mục lộ trình chuyển IN_PROGRESS', r.data.items.find((i) => i.lesson.id === lessonId).status === 'IN_PROGRESS', r.data.items);
  r = await call('POST', `/lessons/${lessonId}/complete`, { token: access, body: {} });
  check('hoàn thành bài (lần đầu)', r.status === 200 && r.data.firstCompletion === true && r.data.progress.status === 'COMPLETED', r.data);
  r = await call('POST', `/lessons/${lessonId}/complete`, { token: access });
  check('hoàn thành lại -> không tính trùng', r.status === 200 && r.data.firstCompletion === false, r.data);
  r = await call('GET', '/learning-path', { token: access });
  check('lộ trình: bài đã xong COMPLETED, bài kế tiếp AVAILABLE',
    r.data.items[0].status === 'COMPLETED' && r.data.items[1].status === 'AVAILABLE' && r.data.completedItems === 1, r.data.items);
  r = await call('GET', '/dashboard', { token: access });
  check('dashboard: thời gian học hôm nay, streak = 1, bài hoàn thành = 1',
    r.data.todayProgress.studyMinutes >= 1 && r.data.streak.current === 1 && r.data.streak.activeToday
    && r.data.totals.lessonsCompleted === 1, { today: r.data.todayProgress, streak: r.data.streak, totals: r.data.totals });

  console.log('\n[Điều chỉnh lộ trình]');
  let path = (await call('GET', '/learning-path', { token: access })).data;
  const target = path.items[1];
  r = await call('POST', `/learning-path/items/${target.id}/skip`, { token: access });
  check('bỏ qua bài', r.data.items.find((i) => i.id === target.id).status === 'SKIPPED', r.data);
  r = await call('POST', `/learning-path/items/${target.id}/unskip`, { token: access });
  check('hủy bỏ qua', r.data.items.find((i) => i.id === target.id).status !== 'SKIPPED', r.data);
  r = await call('POST', '/learning-path/items', { token: access, body: { lessonId: 1 } });
  const custom = r.data.items?.find((i) => i.lesson.id === 1);
  check('thêm bài tự chọn vào cuối', r.status === 200 && custom?.custom === true && r.data.items.at(-1).lesson.id === 1, r.data);
  r = await call('POST', '/learning-path/items', { token: access, body: { lessonId: 1 } });
  check('thêm trùng -> 409', r.status === 409 && r.data.code === 'LESSON_ALREADY_IN_PATH', r.data);
  const reversed = r.status === 409 ? (await call('GET', '/learning-path', { token: access })).data.items.map((i) => i.id).reverse() : [];
  r = await call('PUT', '/learning-path/items/order', { token: access, body: { itemIds: reversed } });
  check('sắp xếp lại', r.status === 200 && r.data.items[0].id === reversed[0], r.data);
  r = await call('PUT', '/learning-path/items/order', { token: access, body: { itemIds: reversed.slice(1) } });
  check('sắp xếp thiếu bài -> 400', r.status === 400, r.data);
  r = await call('DELETE', `/learning-path/items/${custom.id}`, { token: access });
  check('xóa bài khỏi lộ trình', r.status === 200 && !r.data.items.some((i) => i.id === custom.id), r.data);
  const oldPathId = r.data.id;
  r = await call('POST', '/learning-path/generate', { token: access, body: { goalType: 'COMMUNICATION', levelId: 2, dailyMinutes: 30 } });
  check('tạo lại lộ trình theo mục tiêu tự chọn', r.status === 201 && r.data.id !== oldPathId && r.data.source === 'GOAL'
    && r.data.level.code === 'ELEMENTARY' && r.data.dailyMinutes === 30, r.data);
  r = await call('GET', '/users/me', { token: access });
  check('hồ sơ đồng bộ mục tiêu mới', r.data.learningGoal === 'COMMUNICATION' && r.data.dailyGoalMinutes === 30, r.data);

  console.log('\n[Từ điển & sổ từ]');
  r = await call('GET', '/dictionary/lookup?q=Souvenirs.', { token: access });
  check('tra "Souvenirs." -> từ gốc "souvenir" trong kho nội bộ', r.status === 200 && r.data.source === 'LOCAL'
    && r.data.matchedHeadword === 'souvenir' && r.data.entries[0].meaningVi === 'quà lưu niệm', r.data);
  const wordId = r.data.entries[0].id;
  r = await call('POST', '/vocabulary/my-words', { token: access,
    body: { wordId, sourceLessonId: 6, contextSentence: 'She visited the old market and bought some souvenirs.' } });
  check('lưu từ vào sổ', r.status === 200 && r.data.source === 'READING_LOOKUP' && r.data.sourceLessonTitle?.includes('city'), r.data);
  const userWordId = r.data.id;
  r = await call('POST', '/vocabulary/my-words', { token: access, body: { wordId } });
  check('lưu lại -> idempotent', r.data.id === userWordId, r.data);
  r = await call('GET', '/dictionary/lookup?q=souvenir', { token: access });
  check('tra lại -> đánh dấu đã lưu', r.data.entries[0].saved === true && r.data.entries[0].userWordId === userWordId, r.data);
  r = await call('GET', '/vocabulary/my-words/headwords', { token: access });
  check('danh sách từ đã lưu (tô sáng bài đọc)', r.data.includes('souvenir'), r.data);
  r = await call('PATCH', `/vocabulary/my-words/${userWordId}`, { token: access, body: { note: 'mua ở Hội An' } });
  check('cập nhật ghi chú', r.data.note === 'mua ở Hội An', r.data);
  r = await call('GET', '/vocabulary/my-words?keyword=souv', { token: access });
  check('tìm trong sổ từ', r.data.totalElements === 1, r.data);
  r = await call('GET', '/dictionary/lookup?q=hello%3Cb%3Eworld', { token: access });
  check('từ tra chứa ký tự lạ ở giữa -> 400', r.status === 400, r.data);
  // Phụ thuộc Internet nên chỉ in thông tin, không tính pass/fail (lần đầu: DICTIONARY_API, các lần sau: LOCAL do đã cache)
  r = await call('GET', '/dictionary/lookup?q=lighthouse', { token: access });
  console.log(`  ℹ tra từ ngoài kho ("lighthouse"): source=${r.data.source}`
    + (r.data.source === 'NONE' ? ' (không gọi được từ điển ngoài)' : ` · ${r.data.entries[0]?.definitionEn?.slice(0, 50)}…`));
  r = await call('DELETE', `/vocabulary/my-words/${userWordId}`, { token: access });
  check('xóa khỏi sổ -> 204', r.status === 204, r.data);

  console.log('\n[Quiz trong bài học - chấm từng câu]');
  r = await call('GET', '/lessons/8/exercises', { token: access });
  check('bài 8 có 1 quiz 4 câu', r.status === 200 && r.data.length === 1 && r.data[0].questionCount === 4, r.data);
  r = await call('POST', `/exercises/${r.data[0].testId}/attempts`, { token: access });
  check('bắt đầu quiz', r.status === 200 && r.data.attempt.questions.length === 4, r.data);
  const quiz = r.data.attempt;
  const byType = Object.fromEntries(quiz.questions.map((q) => [q.type, q]));
  const meaning = { always: 'luôn luôn', usually: 'thường xuyên', sometimes: 'thỉnh thoảng', never: 'không bao giờ' }
  const orderWords = ['She', 'is', 'cooking', 'dinner', 'now'];
  const answersByType = {
    FILL_BLANK: { textAnswers: ['drinks', "'s drinking"] },
    MATCHING: { matches: Object.fromEntries(byType.MATCHING.options.map((o) => [o.id, meaning[o.content]])) },
    ORDERING: { orderedOptionIds: orderWords.map((w) => byType.ORDERING.options.find((o) => o.content === w).id) },
    SINGLE_CHOICE: { selectedOptionIds: [byType.SINGLE_CHOICE.options.find((o) => o.content === 'is crying').id] },
  };
  let allCorrect = true;
  for (const [type, answer] of Object.entries(answersByType)) {
    r = await call('POST', `/exercises/attempts/${quiz.attemptId}/check`, { token: access, body: { questionId: byType[type].id, ...answer } });
    allCorrect &&= r.status === 200 && r.data.correct === true && !!r.data.review;
  }
  check('chấm từng câu: điền từ, ghép, sắp xếp, trắc nghiệm đều đúng + có lời giải', allCorrect, r.data);
  r = await call('POST', `/exercises/attempts/${quiz.attemptId}/check`, { token: access, body: { questionId: byType.SINGLE_CHOICE.id, selectedOptionIds: [] } });
  check('chấm lại câu đã chấm -> 409', r.status === 409 && r.data.code === 'ANSWER_ALREADY_CHECKED', r.data);
  r = await call('POST', `/exercises/attempts/${quiz.attemptId}/finish`, { token: access });
  check('nộp quiz: 100%, đạt, hoàn thành bài học', r.status === 200 && Number(r.data.percentage) === 100 && r.data.passed
    && r.data.lessonCompleted, r.data);
  r = await call('GET', '/lessons/8', { token: access });
  check('bài 8 chuyển sang COMPLETED', r.data.progress?.status === 'COMPLETED', r.data.progress);

  console.log('\n[Speaking - thu âm & chấm phát âm]');
  const speakingTest = (await call('GET', '/lessons/3/exercises', { token: access })).data[0];
  const speak = (await call('POST', `/exercises/${speakingTest.testId}/attempts`, { token: access })).data.attempt;
  const sq = speak.questions[0];
  check('câu luyện nói có câu mẫu', sq.type === 'SPEAKING' && sq.referenceText === 'Hello, my name is Lan. I am a student.', sq);
  r = await upload('/speaking/submissions', { questionId: sq.id, lessonId: 3, clientTranscript: 'hello my name is Lan I am a student' }, silentWav(3000), access);
  check('nộp bản ghi WAV -> chấm Mock theo từng từ', r.status === 200 && r.data.provider === 'MOCK' && !r.data.estimated
    && Number(r.data.accuracyScore) === 100 && Number(r.data.pronunciationScore) >= 85 && r.data.words.length === 9, r.data);
  const speakingId = r.data.id;
  r = await upload('/speaking/submissions', { questionId: sq.id, clientTranscript: 'hello my name is' }, silentWav(2000), access);
  check('đọc thiếu -> điểm đầy đủ thấp + gợi ý từ bỏ sót', Number(r.data.completenessScore) < 60
    && r.data.tips.some((t) => t.includes('bỏ sót')), r.data);
  r = await upload('/speaking/submissions', { questionId: sq.id }, silentWav(2500), access);
  check('không có transcript -> điểm ước lượng', r.status === 200 && r.data.estimated === true && r.data.accuracyScore === null, r.data);
  r = await upload('/speaking/submissions', { questionId: sq.id }, Buffer.from('this is not a wav file, just some text bytes...'), access);
  check('file không phải WAV -> 400 INVALID_AUDIO', r.status === 400 && r.data.code === 'INVALID_AUDIO', r.data);
  const audio = await fetch(`${BASE}/speaking/submissions/${speakingId}/audio`, { headers: { Authorization: `Bearer ${access}` } });
  check('nghe lại bản ghi của mình', audio.status === 200 && (await audio.arrayBuffer()).byteLength === silentWav(3000).length, audio.status);
  r = await call('POST', `/exercises/attempts/${speak.attemptId}/check`, { token: access, body: { questionId: sq.id, speakingSubmissionId: speakingId } });
  check('dùng bản ghi để chấm câu luyện nói', r.status === 200 && r.data.correct === true && r.data.speaking?.id === speakingId, r.data);

  console.log('\n[Writing - chấm & gợi ý sửa lỗi]');
  const writingTest = (await call('GET', '/lessons/7/exercises', { token: access })).data[0];
  const wAttempt = (await call('POST', `/exercises/${writingTest.testId}/attempts`, { token: access })).data.attempt;
  const essayQ = wAttempt.questions.find((q) => q.type === 'ESSAY');
  check('đề viết có yêu cầu 80-120 từ', essayQ.minWords === 80 && essayQ.maxWords === 120, essayQ);
  r = await call('POST', `/exercises/attempts/${wAttempt.attemptId}/check`, { token: access,
    body: { questionId: essayQ.id, answerText: 'last weekend i goed to the beach with my freind. We swimmed and eat a ice cream' } });
  check('nhận xét bài viết: lỗi + bản sửa + điểm', r.status === 200 && r.data.writing.issues.length >= 5
    && r.data.writing.correctedText.startsWith('Last weekend I went') && r.data.writing.criteria.taskAchievement < 30, r.data.writing);
  r = await call('POST', `/exercises/attempts/${wAttempt.attemptId}/finish`, { token: access });
  check('nộp bài viết chưa đạt -> không hoàn thành bài học', r.status === 200 && r.data.passed === false && r.data.lessonCompleted === false, r.data);
  r = await call('POST', '/writing/evaluate', { token: access, body: { text: 'She have a umbrella.', minWords: 3 } });
  check('chấm bài viết tự do', r.status === 200 && r.data.feedback.correctedText === 'She has an umbrella.', r.data);
  r = await call('POST', '/writing/evaluate', { token: access, body: { text: '' } });
  check('bài viết trống -> 400', r.status === 400, r.data);

  console.log('\n[Flashcard SRS & bộ từ vựng]');
  r = await call('GET', '/vocabulary/sets', { token: access });
  check('danh sách bộ từ', r.status === 200 && r.data.length >= 3 && r.data.every((s) => s.wordCount > 0), r.data);
  const travel = r.data.find((s) => s.slug === 'travel-essentials');
  r = await call('GET', `/vocabulary/sets/${travel.id}`, { token: access });
  check('chi tiết bộ từ Du lịch (6 từ)', r.status === 200 && r.data.words.length === 6, r.data);
  r = await call('POST', `/vocabulary/sets/${travel.id}/add`, { token: access });
  check('thêm cả bộ vào sổ từ', r.status === 200 && r.data.added === 6, r.data);
  r = await call('POST', `/vocabulary/sets/${travel.id}/add`, { token: access });
  check('thêm lại -> không trùng', r.data.added === 0 && r.data.alreadyInNotebook === 6, r.data);
  r = await call('GET', '/flashcards/summary', { token: access });
  check('tổng quan: 6 thẻ mới, chưa có thẻ đến hạn', r.status === 200 && r.data.newCards >= 6 && r.data.newAvailableToday >= 6
    && r.data.dueNow === 0 && r.data.forecast.length === 7, r.data);
  r = await call('GET', '/flashcards/queue?limit=10', { token: access });
  const cards = r.data.cards;
  check('hàng đợi có thẻ mới kèm khoảng ôn dự kiến', r.status === 200 && cards.length >= 6 && cards[0].status === 'NEW'
    && cards[0].intervals.GOOD === 86400 && cards[0].intervals.AGAIN === 600, r.data);

  r = await call('POST', `/flashcards/${cards[0].userWordId}/review`, { token: access, body: { grade: 'GOOD', responseTimeMs: 4000 } });
  check('GOOD -> ôn lại sau 1 ngày', r.status === 200 && r.data.card.intervalDays === 1 && r.data.card.status === 'LEARNING'
    && !r.data.relearnInSession, r.data);
  r = await call('POST', `/flashcards/${cards[1].userWordId}/review`, { token: access, body: { grade: 'AGAIN', responseTimeMs: 6000 } });
  check('AGAIN -> học lại ngay trong phiên (10 phút)', r.data.relearnInSession === true
    && Math.abs(new Date(r.data.nextDueAt) - Date.now() - 600000) < 120000, r.data);
  r = await call('POST', `/flashcards/${cards[2].userWordId}/review`, { token: access, body: { grade: 'EASY' } });
  check('EASY -> 4 ngày', r.data.card.intervalDays === 4, r.data);
  r = await call('POST', `/flashcards/${cards[3].userWordId}/review`, { token: access, body: { grade: 'NOPE' } });
  check('mức đánh giá không hợp lệ -> 400', r.status === 400, r.data);
  r = await call('GET', '/flashcards/summary', { token: access });
  check('đã ôn 3 thẻ, còn bớt 3 thẻ mới trong ngày', r.data.reviewedToday === 3 && r.data.learning >= 3
    && r.data.newAvailableToday === Math.min(r.data.newCards, r.data.newCardsPerDay - 3), r.data);

  r = await call('PATCH', `/vocabulary/my-words/${cards[4].userWordId}/suspend`, { token: access, body: { suspended: true } });
  check('tạm ẩn một từ', r.status === 200 && r.data.status === 'SUSPENDED', r.data);
  r = await call('POST', `/flashcards/${cards[4].userWordId}/review`, { token: access, body: { grade: 'GOOD' } });
  check('ôn từ đang tạm ẩn -> 400', r.status === 400, r.data);
  r = await call('GET', '/flashcards/queue?limit=50', { token: access });
  check('hàng đợi bỏ qua từ tạm ẩn', !r.data.cards.some((c) => c.userWordId === cards[4].userWordId), r.data);
  r = await call('PATCH', `/vocabulary/my-words/${cards[4].userWordId}/suspend`, { token: access, body: { suspended: false } });
  check('bỏ tạm ẩn -> trở lại NEW', r.data.status === 'NEW', r.data);
  const otherUser = (await call('POST', '/auth/login', { body: ADMIN })).data.accessToken;
  r = await call('POST', `/flashcards/${cards[0].userWordId}/review`, { token: otherUser, body: { grade: 'GOOD' } });
  check('ôn thẻ của người khác -> 404', r.status === 404, r.data);
  r = await call('GET', '/dashboard', { token: access });
  const todayPoint = r.data.series.at(-1);
  check('dashboard: từ đã ôn & từ mới học hôm nay', todayPoint.wordsReviewed >= 3 && todayPoint.wordsLearned >= 2, todayPoint);

  console.log('\n[Dashboard]');
  r = await call('GET', '/dashboard', { token: access });
  check('dashboard 7 ngày -> 7 điểm theo ngày', r.status === 200 && r.data.series.length === 7 && r.data.granularity === 'DAY', r.data);
  check('dashboard có kết quả xếp lớp vừa làm', r.data.latestPlacement?.levelCode === 'INTERMEDIATE', r.data.latestPlacement);
  r = await call('GET', '/dashboard?range=90d', { token: access });
  check('dashboard 90 ngày -> gộp theo tuần', r.status === 200 && r.data.granularity === 'WEEK' && r.data.series.length >= 13, r.data);
  r = await call('GET', '/dashboard?range=abc', { token: access });
  check('range không hợp lệ -> 400', r.status === 400 && r.data.code === 'VALIDATION_ERROR', r.data);
  const seedLearner = (await call('POST', '/auth/login', { body: { email: 'learner@englishhub.local', password: 'Learner@123' } })).data;
  r = await call('GET', '/dashboard?range=30d', { token: seedLearner.accessToken });
  // Không giả định còn từ đến hạn: học viên mẫu có thể vừa ôn hết khi thử giao diện
  const t = r.data.totals;
  check('học viên mẫu: có lộ trình + kho từ + dữ liệu biểu đồ',
    r.data.learningPath?.totalItems >= 1 && t.wordsNew + t.wordsLearning + t.wordsMastered >= 1
    && r.data.series.some((p) => p.studyMinutes > 0), r.data);

  console.log('\n[Thông báo]');
  r = await call('GET', '/notifications?page=0&size=20', { token: access });
  check('danh sách thông báo', r.status === 200 && Array.isArray(r.data.items), r.data);
  check('xếp lớp xong -> có thông báo PATH_UPDATED', r.data.items?.some((n) => n.type === 'PATH_UPDATED' && n.link === '/app/path'), r.data.items);
  // Giờ nhắc 00:00 + mục tiêu 240 phút: chắc chắn đã qua giờ nhắc và chưa đạt mục tiêu
  r = await call('PUT', '/users/me', {
    token: access,
    body: { fullName: 'Trần Thị Bình', learningGoal: 'TOEIC', dailyGoalMinutes: 240, timeZone: 'Asia/Ho_Chi_Minh',
      reminderEnabled: true, reminderTime: '00:00', emailNotificationEnabled: false },
  });
  check('đặt giờ nhắc 00:00', r.status === 200, r.data);
  const adminAccess = (await call('POST', '/auth/login', { body: ADMIN })).data.accessToken;
  r = await call('POST', '/admin/jobs/study-reminders', { token: access });
  check('learner chạy job nhắc học -> 403', r.status === 403, r.data);
  r = await call('POST', '/admin/jobs/study-reminders', { token: adminAccess });
  check('admin chạy job nhắc học', r.status === 200 && r.data.usersChecked >= 1, r.data);
  r = await call('GET', '/notifications?unreadOnly=true&page=0&size=50', { token: access });
  const reminders = (r.data.items ?? []).filter((n) => n.type === 'STUDY_REMINDER');
  check('có đúng 1 nhắc học STUDY_REMINDER', reminders.length === 1, r.data.items);
  await call('POST', '/admin/jobs/study-reminders', { token: adminAccess });
  r = await call('GET', '/notifications?page=0&size=50', { token: access });
  check('chạy job lần 2 không nhắc trùng trong ngày', r.data.items.filter((n) => n.type === 'STUDY_REMINDER').length === 1, r.data.items);
  r = await call('GET', '/notifications/unread-count', { token: access });
  const unread = r.data.count;
  check('đếm chưa đọc', r.status === 200 && unread >= 2, r.data);
  r = await call('POST', `/notifications/${reminders[0]?.id}/read`, { token: access });
  check('đánh dấu đã đọc', r.status === 200 && r.data.read === true, r.data);
  r = await call('GET', '/notifications/unread-count', { token: access });
  check('chưa đọc giảm 1', r.data.count === unread - 1, r.data);
  r = await call('POST', `/notifications/${reminders[0]?.id}/read`, { token: adminAccess });
  check('đọc thông báo của người khác -> 404', r.status === 404, r.data);
  r = await call('POST', '/notifications/read-all', { token: access });
  check('đọc tất cả', r.status === 200 || r.status === 204, r.data);
  r = await call('GET', '/notifications/unread-count', { token: access });
  check('chưa đọc = 0', r.data.count === 0, r.data);
  r = await call('DELETE', `/notifications/${reminders[0]?.id}`, { token: access });
  check('xóa thông báo -> 204', r.status === 204, r.data);

  console.log('\n[Trò chơi từ vựng]');
  r = await call('GET', '/games/words?gameType=MATCHING&count=6', { token: access });
  check('bộ từ ghép cặp: 6 từ không trùng, có nghĩa', r.status === 200 && r.data.length === 6
    && new Set(r.data.map((w) => w.headword.toLowerCase())).size === 6 && r.data.every((w) => w.meaning), r.data);
  r = await call('GET', '/games/words?gameType=SCRAMBLE&count=8', { token: access });
  check('bộ từ xếp chữ: chỉ từ đơn 3-12 chữ cái', r.status === 200 && r.data.length > 0
    && r.data.every((w) => /^[A-Za-z]{3,12}$/.test(w.headword)), r.data);
  r = await call('GET', '/games/words?gameType=PUZZLE', { token: access });
  check('loại trò chơi sai -> 400', r.status === 400, r.data);
  r = await call('POST', '/games/results', { token: access, body: { gameType: 'SCRAMBLE', score: 50, correctCount: 9, totalCount: 8, durationSeconds: 60 } });
  check('đúng > tổng -> 400', r.status === 400 && r.data.code === 'VALIDATION_ERROR', r.data);
  r = await call('POST', '/games/results', { token: access, body: { gameType: 'SCRAMBLE', score: 150, correctCount: 1, totalCount: 8, durationSeconds: 60 } });
  check('điểm > 100 -> 400', r.status === 400, r.data);
  r = await call('POST', '/games/results', { token: access, body: { gameType: 'SCRAMBLE', score: 75, correctCount: 6, totalCount: 8, durationSeconds: 90 } });
  check('lượt đầu là kỷ lục', r.status === 200 && r.data.personalBest === true && r.data.bestScore === 75, r.data);
  r = await call('POST', '/games/results', { token: access, body: { gameType: 'SCRAMBLE', score: 50, correctCount: 4, totalCount: 8, durationSeconds: 80 } });
  check('điểm thấp hơn -> không phải kỷ lục', r.data.personalBest === false && r.data.bestScore === 75 && r.data.previousBest === 75, r.data);
  r = await call('GET', '/games/stats', { token: access });
  const scramble = r.data.find?.((s) => s.gameType === 'SCRAMBLE');
  check('thống kê trò chơi', r.status === 200 && r.data.length === 3 && scramble.plays === 2 && scramble.bestScore === 75, r.data);

  console.log('\n[Admin]');
  r = await call('POST', '/auth/login', { body: ADMIN });
  check('admin login', r.status === 200 && r.data.user.roles.includes('ADMIN'), r.data);
  const adminToken = r.data.accessToken;
  const adminId = r.data.user.id;

  r = await call('GET', '/admin/reports/overview', { token: adminToken });
  check('báo cáo tổng quan', r.status === 200 && r.data.totalLearners >= 1 && r.data.levelDistribution.length >= 1, r.data);
  r = await call('GET', '/admin/reports/overview', { token: access });
  check('learner xem báo cáo admin -> 403', r.status === 403, r.data);

  r = await call('GET', `/admin/users?keyword=${encodeURIComponent(email)}&role=LEARNER&size=5`, { token: adminToken });
  check('tìm user theo keyword + role', r.status === 200 && r.data.totalElements === 1 && r.data.items[0].email === email, r.data);
  r = await call('GET', '/admin/users?sort=passwordHash,asc', { token: adminToken });
  check('sort theo passwordHash bị chặn -> 400', r.status === 400, r.data);
  r = await call('PATCH', `/admin/users/${adminId}/status`, { token: adminToken, body: { status: 'LOCKED' } });
  check('admin tự khóa mình -> 409', r.status === 409 && r.data.code === 'SELF_MODIFICATION_NOT_ALLOWED', r.data);

  r = await call('PATCH', `/admin/users/${learnerId}/status`, { token: adminToken, body: { status: 'LOCKED' } });
  check('khóa learner', r.status === 200 && r.data.status === 'LOCKED', r.data);
  r = await call('POST', '/auth/login', { body: { email, password } });
  check('learner bị khóa không login được -> 403 ACCOUNT_LOCKED', r.status === 403 && r.data.code === 'ACCOUNT_LOCKED', r.data);
  r = await call('POST', '/auth/refresh', { body: { refreshToken: refresh } });
  check('refresh token của user bị khóa đã bị thu hồi -> 401', r.status === 401, r.data);
  r = await call('PATCH', `/admin/users/${learnerId}/status`, { token: adminToken, body: { status: 'ACTIVE' } });
  check('mở khóa learner', r.status === 200 && r.data.status === 'ACTIVE', r.data);

  console.log('\n[Admin CMS - khóa học, bài học, media, phụ đề]');
  const stamp = Date.now();
  r = await call('POST', '/admin/courses', { token: access, body: { title: 'X', levelId: 1, goalType: 'GENERAL' } });
  check('learner tạo khóa học -> 403', r.status === 403, r.data);
  r = await call('POST', '/admin/courses', { token: adminToken, body: { title: 'Bad', slug: 'Có Dấu', levelId: 1, goalType: 'GENERAL' } });
  check('slug sai định dạng -> 400', r.status === 400 && r.data.code === 'VALIDATION_ERROR', r.data);
  r = await call('POST', '/admin/courses', { token: adminToken, body: {
    title: `Đường đến giao tiếp ${stamp}`, description: 'Khóa học thử', levelId: 2, goalType: 'COMMUNICATION', published: false } });
  check('tạo khóa học nháp, slug tự sinh không dấu', r.status === 201 && r.data.slug === `duong-den-giao-tiep-${stamp}` && !r.data.published, r.data);
  const course = r.data;
  r = await call('GET', `/courses/${course.id}`, { token: access });
  check('học viên không thấy khóa học nháp -> 404', r.status === 404, r.data);

  r = await call('POST', '/admin/lessons', { token: adminToken, body: {
    courseId: course.id, title: 'Nghe chào hỏi', skill: 'LISTENING', mediaType: 'AUDIO', estimatedMinutes: 8 } });
  check('bài AUDIO thiếu media -> 400', r.status === 400, r.data);

  const uploadMedia = async (kind, buffer, name, mime) => {
    const form = new FormData();
    form.append('file', new Blob([buffer], { type: mime }), name);
    const res = await fetch(`${BASE}/admin/media?kind=${kind}`, { method: 'POST', headers: { Authorization: `Bearer ${adminToken}` }, body: form });
    const text = await res.text();
    return { status: res.status, data: text ? JSON.parse(text) : null };
  };
  r = await uploadMedia('AUDIO', silentWav(3000), 'greeting.wav', 'audio/wav');
  check('tải audio lên', r.status === 201 && /^\/media\/uploads\/[0-9a-f-]+\.wav$/.test(r.data.url), r.data);
  const audioUrl = r.data.url;
  const mediaRes = await fetch(BASE.replace('/api/v1', '') + audioUrl, { headers: { Range: 'bytes=0-99' } });
  check('media công khai, hỗ trợ tua (HTTP Range)', mediaRes.status === 206 && (mediaRes.headers.get('content-type') ?? '').includes('audio'), mediaRes.status);
  r = await uploadMedia('IMAGE', Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'), 'x.png', 'image/png');
  check('SVG/HTML đội lốt ảnh bị từ chối', r.status === 400, r.data);
  r = await uploadMedia('IMAGE', silentWav(500), 'fake.png', 'image/png');
  check('file WAV khai là ảnh -> 400', r.status === 400 && r.data.message.includes('wav'), r.data);

  const lessonBody = { courseId: course.id, title: 'Nghe chào hỏi', skill: 'LISTENING', mediaType: 'AUDIO', mediaUrl: audioUrl,
    durationSeconds: 3, estimatedMinutes: 8, summary: 'Chào hỏi cơ bản', content: '<p>Nghe và nhắc lại.</p>', published: true };
  r = await call('POST', '/admin/lessons', { token: adminToken, body: lessonBody });
  check('tạo bài học có audio', r.status === 201 && r.data.slug === 'nghe-chao-hoi' && r.data.levelId === 2, r.data);
  const lesson = r.data;
  r = await call('PUT', `/admin/lessons/${lesson.id}/transcript`, { token: adminToken, body: { segments: [
    { startMs: 0, endMs: 1500, text: 'Hello!' }, { startMs: 1200, endMs: 1000, text: 'Bad' }] } });
  check('phụ đề kết thúc trước khi bắt đầu -> 400', r.status === 400 && r.data.message.includes('#2'), r.data);
  r = await call('PUT', `/admin/lessons/${lesson.id}/transcript`, { token: adminToken, body: { segments: [
    { startMs: 0, endMs: 9000, text: 'Too long' }] } });
  check('phụ đề vượt độ dài audio -> 400', r.status === 400, r.data);
  r = await call('PUT', `/admin/lessons/${lesson.id}/transcript`, { token: adminToken, body: { segments: [
    { startMs: 0, endMs: 1500, text: 'Hello!', translation: 'Xin chào!' }, { startMs: 1500, endMs: 3000, text: 'Nice to meet you.' }] } });
  check('lưu phụ đề 2 câu', r.status === 200 && r.data.transcript.length === 2, r.data);
  r = await call('PUT', `/admin/lessons/${lesson.id}/transcript`, { token: adminToken, body: { segments: [
    { startMs: 0, endMs: 1000, text: 'Hello!' }, { startMs: 1000, endMs: 2000, text: 'Nice to' }, { startMs: 2000, endMs: 3000, text: 'meet you.' }] } });
  check('thay phụ đề (3 câu, không vướng ràng buộc thứ tự)', r.status === 200 && r.data.transcript.map((s) => s.text).join('|') === 'Hello!|Nice to|meet you.', r.data);
  r = await call('POST', '/admin/lessons', { token: adminToken, body: { ...lessonBody, mediaType: 'NONE', mediaUrl: null, published: false } });
  check('bài trùng tên trong khóa -> slug thêm hậu tố', r.status === 201 && r.data.slug === 'nghe-chao-hoi-2', r.data);
  const lesson2 = r.data;
  r = await call('PUT', `/admin/courses/${course.id}/lesson-order`, { token: adminToken, body: { ids: [lesson2.id, lesson.id] } });
  check('sắp xếp lại bài trong khóa', r.status === 200 && r.data.lessons[0].id === lesson2.id, r.data);
  r = await call('PUT', `/admin/courses/${course.id}/lesson-order`, { token: adminToken, body: { ids: [lesson.id] } });
  check('sắp xếp thiếu bài -> 400', r.status === 400, r.data);
  r = await call('PATCH', `/admin/courses/${course.id}/publish`, { token: adminToken, body: { published: true } });
  check('xuất bản khóa học', r.status === 200 && r.data.published, r.data);
  r = await call('GET', `/lessons/${lesson.id}`, { token: access });
  check('học viên mở được bài mới, có phụ đề + audio', r.status === 200 && r.data.transcript.length === 3 && r.data.mediaUrl === audioUrl, r.data);
  r = await call('GET', `/lessons/${lesson2.id}`, { token: access });
  check('bài nháp vẫn ẩn với học viên', r.status === 404, r.data);
  r = await call('GET', `/admin/courses?keyword=${stamp}&published=true`, { token: adminToken });
  check('tìm khóa học (admin) kèm số bài', r.status === 200 && r.data.totalElements === 1 && r.data.items[0].lessonCount === 2
    && r.data.items[0].publishedLessons === 1, r.data);

  console.log('\n[Admin CMS - ngân hàng câu hỏi & bài test]');
  const qBase = { skill: 'LISTENING', levelId: 2, difficulty: 2, defaultPoints: 1, active: true };
  r = await call('POST', '/admin/questions', { token: adminToken, body: { ...qBase, questionType: 'SINGLE_CHOICE', content: 'Pick one',
    options: [{ content: 'A', correct: true }, { content: 'B', correct: true }] } });
  check('một đáp án nhưng 2 phương án đúng -> 400', r.status === 400 && r.data.message.includes('đúng 1'), r.data);
  r = await call('POST', '/admin/questions', { token: adminToken, body: { ...qBase, questionType: 'SINGLE_CHOICE',
    content: 'What does the speaker say first?', explanation: 'Câu đầu là "Hello!"',
    options: [{ content: 'Hello!', correct: true }, { content: 'Goodbye!', correct: false }, { content: 'Thanks!', correct: false }] } });
  check('tạo câu một đáp án', r.status === 201 && r.data.options.length === 3 && r.data.usage.tests.length === 0, r.data);
  const q1 = r.data;
  r = await call('POST', '/admin/questions', { token: adminToken, body: { ...qBase, questionType: 'FILL_BLANK',
    content: 'Nice to {{1}} you.', answerKey: '[[" meet ", "see"]]' } });
  check('tạo câu điền từ, đáp án được chuẩn hóa', r.status === 201 && r.data.answerKey === '[["meet","see"]]', r.data);
  const q2 = r.data;
  r = await call('POST', '/admin/questions', { token: adminToken, body: { ...qBase, skill: 'WRITING', questionType: 'ESSAY',
    content: 'Write 3 sentences introducing yourself.' } });
  const essay = r.data;
  check('tạo câu viết tự luận', r.status === 201, r.data);
  r = await call('GET', '/admin/questions?type=FILL_BLANK&keyword=nice%20to', { token: adminToken });
  check('lọc ngân hàng câu hỏi', r.status === 200 && r.data.items.some((q) => q.id === q2.id) && r.data.items.every((q) => q.questionType === 'FILL_BLANK'), r.data);

  const testBody = { title: `Quiz chào hỏi ${stamp}`, testType: 'LESSON_QUIZ', lessonId: lesson.id, passingPercent: 60, published: true };
  r = await call('POST', '/admin/tests', { token: adminToken, body: testBody });
  check('xuất bản bài test chưa có câu hỏi -> 400', r.status === 400, r.data);
  r = await call('POST', '/admin/tests', { token: adminToken, body: { ...testBody, lessonId: null, published: false } });
  check('quiz bài học thiếu bài học -> 400', r.status === 400, r.data);
  r = await call('POST', '/admin/tests', { token: adminToken, body: { ...testBody, published: false } });
  check('tạo quiz nháp', r.status === 201 && r.data.questions.length === 0, r.data);
  const quizTest = r.data;
  r = await call('PUT', `/admin/tests/${quizTest.id}/questions`, { token: adminToken, body: { items: [
    { questionId: q1.id }, { questionId: q2.id, points: 2 }, { questionId: q1.id }] } });
  check('câu hỏi lặp trong bài test -> 400', r.status === 400, r.data);
  r = await call('PUT', `/admin/tests/${quizTest.id}/questions`, { token: adminToken, body: { items: [
    { questionId: q2.id, points: 2 }, { questionId: q1.id }] } });
  check('gán câu hỏi (thứ tự + điểm riêng)', r.status === 200 && r.data.questions[0].questionId === q2.id
    && Number(r.data.totalPoints) === 3, r.data);
  r = await call('GET', `/admin/questions?excludeTestId=${quizTest.id}&type=SINGLE_CHOICE&keyword=speaker%20say`, { token: adminToken });
  check('ô chọn câu hỏi ẩn câu đã có trong bài', r.status === 200 && !r.data.items.some((q) => q.id === q1.id), r.data);
  r = await call('PATCH', `/admin/tests/${quizTest.id}/publish`, { token: adminToken, body: { published: true } });
  check('xuất bản quiz', r.status === 200 && r.data.published, r.data);
  r = await call('GET', `/lessons/${lesson.id}/exercises`, { token: access });
  check('học viên thấy quiz của bài mới', r.status === 200 && r.data.some((t) => t.testId === quizTest.id), r.data);
  r = await call('POST', `/exercises/${quizTest.id}/attempts`, { token: access });
  check('học viên bắt đầu làm quiz', r.status === 200 || r.status === 201, r.data);
  r = await call('PUT', `/admin/tests/${quizTest.id}/questions`, { token: adminToken, body: { items: [{ questionId: q1.id }] } });
  check('sửa câu hỏi của bài đã có lượt làm -> 409 TEST_HAS_ATTEMPTS', r.status === 409 && r.data.code === 'TEST_HAS_ATTEMPTS', r.data);
  r = await call('POST', `/admin/tests/${quizTest.id}/duplicate`, { token: adminToken });
  check('nhân bản bài test (nháp, giữ câu hỏi)', r.status === 201 && !r.data.published && r.data.questions.length === 2
    && r.data.title.endsWith('(bản sao)'), r.data);
  const copyTest = r.data;
  r = await call('DELETE', `/admin/tests/${quizTest.id}`, { token: adminToken });
  check('xóa bài test đã có lượt làm -> 409', r.status === 409 && r.data.code === 'CONTENT_IN_USE', r.data);
  r = await call('DELETE', `/admin/questions/${q1.id}`, { token: adminToken });
  check('xóa câu hỏi đang nằm trong bài test -> 409', r.status === 409 && r.data.message.includes(quizTest.title), r.data);
  r = await call('PUT', `/admin/questions/${q2.id}`, { token: adminToken, body: { ...qBase, questionType: 'FILL_BLANK',
    content: 'Nice to {{1}} you, {{2}}.', answerKey: '[["meet"],["Tom"]]' } });
  check('sửa câu hỏi (thêm ô trống)', r.status === 200 && r.data.usage.tests.length === 2, r.data);
  r = await call('DELETE', `/admin/tests/${copyTest.id}`, { token: adminToken });
  check('xóa bản sao chưa có lượt làm', r.status === 204, r.data);

  r = await call('POST', '/admin/tests', { token: adminToken, body: { title: `Placement nháp ${stamp}`, testType: 'PLACEMENT', published: false } });
  check('bài xếp lớp thiếu thời gian -> 400', r.status === 400, r.data);
  r = await call('POST', '/admin/tests', { token: adminToken, body: { title: `Placement nháp ${stamp}`, testType: 'PLACEMENT', durationMinutes: 20, published: false } });
  const draftPlacement = r.data;
  r = await call('PUT', `/admin/tests/${draftPlacement.id}/questions`, { token: adminToken, body: { items: [{ questionId: essay.id }] } });
  check('bài xếp lớp không nhận câu tự luận -> 400', r.status === 400 && r.data.message.includes('xếp lớp'), r.data);
  r = await call('GET', '/admin/tests?type=PLACEMENT&size=50', { token: adminToken });
  check('bài xếp lớp đang dùng vẫn là bài cũ', r.status === 200 && r.data.items.filter((t) => t.activePlacement).length === 1
    && !r.data.items.find((t) => t.id === draftPlacement.id).activePlacement, r.data);
  await call('DELETE', `/admin/tests/${draftPlacement.id}`, { token: adminToken });
  r = await call('DELETE', `/admin/questions/${essay.id}`, { token: adminToken });
  check('xóa câu hỏi chưa dùng', r.status === 204, r.data);

  r = await call('DELETE', `/admin/lessons/${lesson.id}`, { token: adminToken });
  check('xóa bài đã có học viên làm -> 409 CONTENT_IN_USE', r.status === 409 && r.data.code === 'CONTENT_IN_USE', r.data);
  r = await call('DELETE', `/admin/courses/${course.id}`, { token: adminToken });
  check('xóa khóa học có dữ liệu học viên -> 409', r.status === 409, r.data);
  await call('PATCH', `/admin/courses/${course.id}/publish`, { token: adminToken, body: { published: false } });
  r = await call('GET', `/lessons/${lesson.id}`, { token: access });
  check('ẩn khóa học -> học viên không mở được bài', r.status === 404, r.data);
  r = await call('DELETE', `/admin/lessons/${lesson2.id}`, { token: adminToken });
  check('xóa bài nháp chưa ai học', r.status === 204, r.data);

  r = await call('POST', '/admin/courses', { token: adminToken, body: { title: `Khóa tạm ${stamp}`, levelId: 1, goalType: 'GENERAL' } });
  const tempCourse = r.data;
  await call('POST', '/admin/lessons', { token: adminToken, body: { courseId: tempCourse.id, title: 'Bài tạm', skill: 'READING', mediaType: 'NONE', estimatedMinutes: 5 } });
  r = await call('DELETE', `/admin/courses/${tempCourse.id}`, { token: adminToken });
  check('xóa khóa học chưa có học viên (kèm bài học)', r.status === 204, r.data);
  r = await call('GET', `/admin/courses/${tempCourse.id}`, { token: adminToken });
  check('khóa học đã xóa -> 404', r.status === 404, r.data);

  console.log('\n[Admin CMS - từ vựng]');
  const head = `smoketest${stamp % 100000}`;
  const wordBody = { headword: head, partOfSpeech: 'noun', phoneticUs: '/smoʊk/', meaningVi: 'từ kiểm thử', example: 'This is a test.', levelId: 1 };
  r = await call('POST', '/admin/vocabulary/words', { token: adminToken, body: wordBody });
  check('tạo từ mới', r.status === 201 && r.data.source === 'ADMIN' && r.data.learnerCount === 0, r.data);
  const word = r.data;
  r = await call('POST', '/admin/vocabulary/words', { token: adminToken, body: wordBody });
  check('từ trùng (cùng từ loại) -> 400', r.status === 400, r.data);
  r = await call('POST', '/admin/vocabulary/words', { token: adminToken, body: { ...wordBody, partOfSpeech: 'adverbial' } });
  check('từ loại sai -> 400', r.status === 400, r.data);
  r = await call('POST', '/admin/vocabulary/sets', { token: adminToken, body: { name: `Bộ thử ${stamp}`, levelId: 1, published: true } });
  check('xuất bản bộ từ rỗng -> 400', r.status === 400, r.data);
  r = await call('POST', '/admin/vocabulary/sets', { token: adminToken, body: { name: `Bộ thử ${stamp}`, levelId: 1 } });
  check('tạo bộ từ nháp', r.status === 201 && r.data.slug === `bo-thu-${stamp}`, r.data);
  const set = r.data;
  r = await call('PUT', `/admin/vocabulary/sets/${set.id}/words`, { token: adminToken, body: { wordIds: [word.id, 1, 2] } });
  check('gán từ cho bộ', r.status === 200 && r.data.words.length === 3 && r.data.words[0].wordId === word.id, r.data);
  r = await call('PUT', `/admin/vocabulary/sets/${set.id}/words`, { token: adminToken, body: { wordIds: [2, word.id] } });
  check('đổi danh sách từ (thứ tự mới)', r.status === 200 && r.data.words.map((w) => w.wordId).join() === `2,${word.id}`, r.data);
  r = await call('GET', `/admin/vocabulary/words?excludeSetId=${set.id}&keyword=${head}`, { token: adminToken });
  check('ô chọn từ ẩn từ đã có trong bộ', r.status === 200 && r.data.totalElements === 0, r.data);
  r = await call('PATCH', `/admin/vocabulary/sets/${set.id}/publish`, { token: adminToken, body: { published: true } });
  check('xuất bản bộ từ', r.status === 200 && r.data.published, r.data);
  r = await call('GET', '/vocabulary/sets', { token: access });
  check('học viên thấy bộ từ mới', r.data.some((s) => s.id === set.id && s.wordCount === 2), r.data);
  await call('POST', `/vocabulary/sets/${set.id}/add`, { token: access });
  r = await call('DELETE', `/admin/vocabulary/words/${word.id}`, { token: adminToken });
  check('xóa từ học viên đã lưu -> 409', r.status === 409 && r.data.code === 'CONTENT_IN_USE', r.data);
  r = await call('DELETE', `/admin/vocabulary/sets/${set.id}`, { token: adminToken });
  check('xóa bộ từ (sổ từ học viên giữ nguyên)', r.status === 204, r.data);
  r = await call('GET', `/admin/vocabulary/words/${word.id}`, { token: adminToken });
  check('từ vẫn còn, có 1 học viên đã lưu', r.status === 200 && r.data.learnerCount === 1, r.data);

  console.log('\n[Báo cáo]');
  r = await call('GET', '/admin/reports/activity?days=30', { token: adminToken });
  const lastDay = r.data.points?.at(-1);
  check('hoạt động 30 ngày (đủ ngày, có học viên hôm nay)', r.status === 200 && r.data.points.length === 30
    && lastDay.activeLearners >= 1 && lastDay.newLearners >= 1, r.data.points?.slice(-2));
  r = await call('GET', '/admin/reports/content', { token: adminToken });
  check('báo cáo nội dung', r.status === 200 && r.data.topLessons.length >= 1 && r.data.tests.length >= 1
    && r.data.productiveSkills.length === 2 && r.data.vocabulary.savedWords >= 1, r.data);
  const csvRes = await fetch(`${BASE}/admin/reports/learners.csv`, { headers: { Authorization: `Bearer ${adminToken}` } });
  const csvBytes = new Uint8Array(await csvRes.arrayBuffer());
  const csv = new TextDecoder().decode(csvBytes);   // text() tự bỏ BOM nên kiểm tra byte thô
  check('xuất CSV học viên (UTF-8 BOM, có học viên vừa tạo)', csvRes.status === 200
    && (csvRes.headers.get('content-type') ?? '').startsWith('text/csv') && csvBytes[0] === 0xef && csvBytes[1] === 0xbb && csvBytes[2] === 0xbf
    && csv.includes(email) && csv.includes('Họ tên'), csv.slice(0, 200));
  r = await call('GET', '/admin/reports/content', { token: access });
  check('learner xem báo cáo -> 403', r.status === 403, r.data);

  console.log('\n[Password]');
  r = await call('POST', '/auth/login', { body: { email, password } });
  access = r.data.accessToken;
  refresh = r.data.refreshToken;
  r = await call('PUT', '/users/me/password', { token: access, body: { currentPassword: 'Wrong1234', newPassword: 'NewPass123' } });
  check('đổi mật khẩu với mật khẩu cũ sai -> 400', r.status === 400 && r.data.code === 'INVALID_CURRENT_PASSWORD', r.data);
  r = await call('PUT', '/users/me/password', { token: access, body: { currentPassword: password, newPassword: 'NewPass123' } });
  check('đổi mật khẩu -> 204', r.status === 204, r.data);
  r = await call('POST', '/auth/login', { body: { email, password: 'NewPass123' } });
  check('login bằng mật khẩu mới', r.status === 200, r.data);
  r = await call('POST', '/auth/forgot-password', { body: { email: 'khong-ton-tai@example.com' } });
  check('quên mật khẩu (email không tồn tại) vẫn 202', r.status === 202, r.data);
  r = await call('POST', '/auth/reset-password', { body: { token: 'invalid', newPassword: 'Another123' } });
  check('reset với token sai -> 400 INVALID_RESET_TOKEN', r.status === 400 && r.data.code === 'INVALID_RESET_TOKEN', r.data);

  console.log('\n[Logout]');
  r = await call('POST', '/auth/login', { body: { email, password: 'NewPass123' } });
  refresh = r.data.refreshToken;
  r = await call('POST', '/auth/logout', { body: { refreshToken: refresh } });
  check('logout -> 204', r.status === 204, r.data);
  r = await call('POST', '/auth/refresh', { body: { refreshToken: refresh } });
  check('refresh sau logout -> 401', r.status === 401, r.data);

  // 2 thiết bị: logout thiết bị A rồi dùng lại token A không được làm đăng xuất thiết bị B
  const deviceA = (await call('POST', '/auth/login', { body: { email, password: 'NewPass123' } })).data.refreshToken;
  const deviceB = (await call('POST', '/auth/login', { body: { email, password: 'NewPass123' } })).data.refreshToken;
  await call('POST', '/auth/logout', { body: { refreshToken: deviceA } });
  await call('POST', '/auth/refresh', { body: { refreshToken: deviceA } });
  r = await call('POST', '/auth/refresh', { body: { refreshToken: deviceB } });
  check('token đã logout bị dùng lại không ảnh hưởng thiết bị khác', r.status === 200, r.data);

  console.log(`\nKết quả: ${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error('Smoke test lỗi:', e.message);
  process.exit(1);
});
