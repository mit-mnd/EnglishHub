# EnglishHub Web (Frontend)

Vue 3 (Composition API) · Vite · Vue Router · Pinia · Axios · Element Plus · ECharts

## Chạy
```bash
npm install
npm run dev        # http://localhost:5173 (proxy /api -> backend)
npm test           # unit test (Vitest)
npm run build      # build production vào dist/
```

Backend mặc định ở `http://localhost:8080`. Nếu chạy cổng khác, tạo file `.env.development.local` (không commit):
```properties
VITE_PROXY_TARGET=http://localhost:8081
```

Tài khoản demo hiện sẵn ở trang đăng nhập khi chạy `npm run dev`.

## Cấu trúc
```
src/
├── api/            http.js (axios + tự refresh token + chuẩn hóa lỗi), index.js (các endpoint)
├── stores/         auth.js (phiên đăng nhập), dashboard.js (số liệu dùng chung header + trang Tổng quan),
│                   notifications.js (số chưa đọc, cập nhật 60 giây/lần)
├── router/         route + guard (requiresAuth, guestOnly, roles)
├── layouts/        AuthLayout, LearnerLayout (sidebar / bottom nav mobile), AdminLayout
├── views/
│   ├── auth/       Login, Register, ForgotPassword, ResetPassword
│   ├── learner/    Dashboard, PlacementIntro/Test/Result, Profile, LearningPath, Lessons, Course, Lesson,
│   │               VocabularyHub, Vocabulary (sổ từ), VocabularySet, FlashcardReview, Practice (trò chơi), Game, Notifications
│   └── admin/      AdminDashboard, AdminUsers, AdminCourses, AdminCourse, AdminLesson, AdminQuestions, AdminTests,
│                   AdminTest, AdminVocabulary, AdminVocabularySet, AdminReports
├── components/
│   ├── quiz/       QuestionRenderer (5 dạng câu hỏi), QuestionNavigator, QuestionStimulus (audio/ảnh + đoạn văn của câu)
│   ├── lesson/     MediaPlayer, InteractiveTranscript, ListeningLesson, ReadingText, WordLookupPanel, LessonRow
│   ├── exercise/   LessonExercises, ExercisePlayer, SpeakingRecorder, WritingEditor, WritingFeedbackView, ScoreRing
│   ├── flashcard/  FlashCard (thẻ lật 3D, 2 chiều Anh→Việt / Việt→Anh)
│   ├── game/       MatchingGame, ScrambleGame, DictationGame, games.js (cấu hình)
│   ├── notification/ NotificationBell (popover trên header), NotificationItem
│   ├── admin/      CourseForm, TestForm, MediaUploader, HtmlEditor, TranscriptEditor, QuestionEditorDialog,
│                   WordEditorDialog, PublishTag
│   ├── charts/     EChart (wrapper, chỉ import module cần dùng)
│   ├── dashboard/  StatCard
│   └── common/     AppLogo, UserMenu, FlameIcon
├── composables/    useBreakpoint, useStudyTracker, useSpeech, useWavRecorder (ghi âm WAV 16 kHz), useSpeechRecognition,
│                   usePagedList (bảng quản trị: lọc + phân trang + chống request chồng)
├── utils/          format, labels, skills, question, readingText, wav (mã hóa WAV), feedback (tô lỗi bài viết), storage,
│                   game (xáo trộn, Levenshtein, tính điểm trò chơi), adminContent (nhãn CMS, đọc SRT/VTT, mốc thời gian)
└── styles/         main.css (design tokens + theme Element Plus), admin.css (kiểu dùng chung trang quản trị)
```

## Ghi chú thiết kế
- **Responsive:** < 768px dùng thanh điều hướng dưới đáy; 768–1199px sidebar thu gọn chỉ còn icon; ≥ 1200px sidebar đầy đủ.
- **Phiên đăng nhập:** khi API trả `TOKEN_EXPIRED`, interceptor gọi `/auth/refresh` **một lần** cho mọi request đang chờ rồi gửi lại. Refresh thất bại → về trang đăng nhập kèm `?redirect=`.
- **Làm bài xếp lớp:**
  - Đồng hồ tính theo giờ server (bù lệch giờ máy), hết giờ tự nộp.
  - Mỗi câu được autosave (câu chọn lưu ngay, ô nhập chữ chờ 0,7 giây).
  - Tải lại trang sẽ tiếp tục đúng câu đang làm; câu "xem lại sau" được lưu trong localStorage.
  - Phím tắt: A–D để chọn đáp án, ← → để chuyển câu.
- **Bài nghe:** phụ đề tô sáng theo thời gian thực; bấm câu để tua hoặc nghe riêng câu đó; tốc độ 0.5x–2x; chế độ luyện nghe làm mờ chữ; tự hoàn thành khi nghe hết; mở lại đúng vị trí đang nghe dở.
- **Bài đọc:** HTML được lọc bằng DOMPurify rồi bọc từng từ để bấm tra; bôi đen tối đa 4 từ để tra cụm; từ đã lưu được gạch chân; lưu kèm câu ngữ cảnh.
- **Thời gian học:** chỉ tính khi tab đang mở **và** (vừa thao tác trong 60 giây **hoặc** đang phát audio); gửi heartbeat 30 giây/lần, khi đóng tab thì gửi bằng `fetch keepalive`.
- **Luyện nói:** ghi âm qua AudioWorklet → cắt khoảng lặng → giảm xuống 16 kHz → mã hóa WAV PCM ngay trên trình duyệt (đúng định dạng Azure, server không cần ffmpeg). Chrome/Edge chạy thêm nhận dạng giọng nói để bộ chấm Mock so khớp từng từ. Micro chỉ dùng được trên `localhost` hoặc HTTPS.
- **Flashcard:** Space/Enter lật thẻ, phím 1–4 chọn Quên/Khó/Được/Dễ (mỗi nút hiện khoảng ôn kế tiếp); thẻ "Quên" quay lại sau 3 thẻ trong cùng phiên; tùy chọn mặt trước (Anh/Việt) và tự phát âm lưu ở localStorage.
- **Trò chơi:** Ghép từ–nghĩa (10 giây/cặp; điểm = 70% số cặp + tối đa 30% thưởng thời gian, mỗi lần ghép sai −5), Xếp chữ (bấm hoặc gõ phím, Backspace gỡ chữ; dùng gợi ý được nửa điểm), Nghe & chép (nghe lại / nghe chậm; sai 1 ký tự với từ ≥ 5 chữ được nửa điểm). Kết thúc hiện kỷ lục và danh sách từ đúng/sai.
- **Thông báo:** chuông trên header cập nhật số chưa đọc 60 giây/lần (dừng khi tab ẩn, cập nhật ngay khi quay lại); bấm thông báo → đánh dấu đã đọc và mở trang liên quan. Trang `/app/notifications` có lọc chưa đọc, phân trang, xóa.
- **Bài tập:** chấm từng câu ngay khi bấm "Kiểm tra"; câu viết hiện điểm 4 tiêu chí, lỗi gạch chân kèm gợi ý, bản đã sửa.
- **Lỗi API** được chuẩn hóa thành `ApiError { status, code, message, fieldErrors }`; form tự gắn lỗi validate của server vào từng ô.
- **CMS (admin):**
  - Soạn bài: nội dung HTML có xem trước (lọc bằng DOMPurify giống trang học viên); tải audio/video, thời lượng tự điền khi đổi media.
  - Phụ đề: bấm ⏱ để lấy mốc từ vị trí đang phát, nghe thử từng câu, nhập SRT/WebVTT (dòng 2 của mỗi khối là bản dịch).
  - Câu hỏi: form đổi theo 7 dạng; điền từ tự sinh ô đáp án theo `{{n}}` trong đề.
  - Bài test: chọn câu từ ngân hàng (ẩn câu đã có), đặt điểm riêng, sắp xếp; cảnh báo khi rời trang mà chưa lưu.
- **Câu hỏi có audio/ảnh/đoạn văn** hiển thị qua `QuestionStimulus` ở cả bài xếp lớp và bài tập.
- **Bảo mật:** token đang lưu ở localStorage cho đơn giản. Khi lên production nên chuyển refresh token sang cookie HttpOnly.
- **Kích thước bundle:** Element Plus đang được import toàn bộ (~280 kB gzip). Có thể tối ưu sau bằng `unplugin-vue-components` (chỉ import component dùng tới).
