# EnglishHub API (Backend)

Spring Boot 3.5 · Java 17+ · SQL Server · Flyway · JWT

## 1. Chuẩn bị
| Yêu cầu | Ghi chú |
|---|---|
| JDK 17+ | Đã thử trên JDK 21 |
| SQL Server | Bật **TCP/IP** (SQL Server Configuration Manager → Protocols for SQLEXPRESS → TCP/IP = Enabled) và **SQL Server and Windows Authentication mode** nếu dùng login `sa` |
| Database | Tạo 1 lần: `sqlcmd -S "EA-MANH\SQLEXPRESS" -E -C -i ../database/00_create_database.sql` |
| Maven | Không cần cài – dùng `mvnw` / `mvnw.cmd` |

> Kết nối JDBC dùng `host:port` (mặc định `localhost:1433`) nên **không cần** dịch vụ SQL Server Browser.
> Nếu instance dùng cổng động, đặt cổng cố định 1433 trong TCP/IP → IP Addresses → IPAll.

## 2. Cấu hình
Sao chép `.env.example` thành `.env` (đã nằm trong `.gitignore`) rồi điền:

```properties
DB_HOST=localhost
DB_PORT=1433
DB_USERNAME=sa
DB_PASSWORD=<mật khẩu>
JWT_SECRET=<chuỗi base64 >= 32 byte>
```

Sinh `JWT_SECRET` (PowerShell):
```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Spring Boot tự đọc `.env` (`spring.config.import`); biến môi trường hệ thống được ưu tiên hơn file.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `SERVER_PORT` | 8080 | Cổng HTTP |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | 15m / 7d | Thời hạn token |
| `APP_CORS_ORIGINS` | http://localhost:5173 | Origin frontend (phân tách bằng dấu phẩy) |
| `APP_FRONTEND_URL` | http://localhost:5173 | Dùng tạo link đặt lại mật khẩu |
| `SWAGGER_ENABLED` | true | Đặt `false` ở production |
| `AI_SPEECH_PROVIDER` | mock | `mock` hoặc `azure` (cần `AZURE_SPEECH_KEY`, `AZURE_SPEECH_REGION`) |
| `APP_STORAGE_DIR` | ./uploads | Nơi lưu file tải lên: bản ghi âm học viên (không public) và media admin (`public-media/`, phục vụ tại `/media/uploads/**`) |
| `UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE` | 50MB / 52MB | Giới hạn file tải lên (audio/video bài học) |
| `DICTIONARY_PROVIDER` | free-dictionary | `none` để tắt tra từ điển ngoài |
| `SRS_NEW_CARDS_PER_DAY` | 20 | Số thẻ flashcard MỚI tối đa mỗi ngày |

## 3. Chạy
```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```
Lần chạy đầu Flyway tự áp dụng `V1` (schema), `V2` (seed), `V3`. Hibernate chạy `ddl-auto: validate` để đảm bảo entity khớp schema.

- Swagger UI: http://localhost:8080/swagger-ui.html (bấm **Authorize**, dán `accessToken`)
- Tài khoản seed: `admin@englishhub.local / Admin@123`, `learner@englishhub.local / Learner@123`

## 4. Kiểm thử
```bash
./mvnw test                                       # unit test: chấm điểm, xếp level, JWT
node scripts/smoke-test.mjs http://localhost:8080 # end-to-end 215 kịch bản (backend phải đang chạy)
```
Smoke test tạo một tài khoản học viên mới mỗi lần chạy (email `learner.<timestamp>@example.com`).

**Quên mật khẩu khi dev:** email chưa gửi thật – link đặt lại được in ra log dạng `[DEV MAIL] ... /reset-password?token=...`.

## 5. API (prefix `/api/v1`)
| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| POST | `/auth/register` | public | Đăng ký (role LEARNER) → token |
| POST | `/auth/login` | public | Đăng nhập → `accessToken`, `refreshToken` |
| POST | `/auth/refresh` | public | Xoay vòng refresh token |
| POST | `/auth/logout` | public | Thu hồi refresh token |
| POST | `/auth/forgot-password` | public | Gửi link đặt lại (luôn 202) |
| POST | `/auth/reset-password` | public | Đặt lại mật khẩu bằng token |
| GET | `/levels` | public | Danh sách trình độ |
| GET / PUT | `/users/me` | đăng nhập | Xem / sửa hồ sơ, mục tiêu, giờ nhắc học |
| PUT | `/users/me/password` | đăng nhập | Đổi mật khẩu (đăng xuất mọi thiết bị) |
| GET | `/placement/test` | đăng nhập | Thông tin bài xếp lớp, bài đang làm dở, kết quả gần nhất |
| POST | `/placement/attempts` | đăng nhập | Bắt đầu / tiếp tục bài làm |
| PUT | `/placement/attempts/{id}/answers` | đăng nhập | Autosave 1 câu |
| POST | `/placement/attempts/{id}/submit` | đăng nhập | Nộp bài → điểm, level, điểm theo kỹ năng, lời giải |
| GET | `/placement/attempts/{id}/result` | đăng nhập | Xem lại kết quả |
| GET | `/placement/attempts` | đăng nhập | Lịch sử (phân trang) |
| GET | `/dashboard?range=7d\|30d\|90d\|365d` | đăng nhập | Streak, mục tiêu hôm nay, tổng quan, biểu đồ (ngày/tuần/tháng), lộ trình, xếp lớp gần nhất |
| GET | `/courses?levelId=&goal=` · `/courses/{id}` | đăng nhập | Khóa học + tiến độ |
| GET | `/lessons?skill=&levelId=&keyword=` · `/lessons/{id}` | đăng nhập | Tìm bài; chi tiết bài (nội dung, media, phụ đề, tiến độ, bài trước/sau) |
| POST | `/lessons/{id}/progress` | đăng nhập | Heartbeat: `{seconds ≤ 120, positionMs, progressPercent}` → thời gian học, streak |
| POST | `/lessons/{id}/complete` | đăng nhập | Hoàn thành bài (lần đầu mới tính vào số bài hoàn thành) |
| GET | `/learning-path` | đăng nhập | Lộ trình đang chạy (204 nếu chưa có) |
| POST | `/learning-path/generate` | đăng nhập | Tạo lại: `{goalType?, levelId?, dailyMinutes?}` (trống = theo kết quả xếp lớp) |
| POST/DELETE | `/learning-path/items[/{id}]` | đăng nhập | Thêm / xóa bài |
| POST | `/learning-path/items/{id}/skip` · `/unskip` | đăng nhập | Bỏ qua / học lại |
| PUT | `/learning-path/items/order` | đăng nhập | Sắp xếp lại `{itemIds}` |
| GET | `/dictionary/lookup?q=` | đăng nhập | Tra từ / cụm từ (kho nội bộ → Free Dictionary API, có cache) |
| GET/POST | `/vocabulary/my-words` | đăng nhập | Sổ từ (tìm kiếm, phân trang) / lưu từ (idempotent) |
| PATCH/DELETE | `/vocabulary/my-words/{id}` | đăng nhập | Sửa ghi chú / xóa |
| GET | `/vocabulary/my-words/headwords` | đăng nhập | Từ đã lưu (tô sáng trong bài đọc) |
| GET | `/media/**` | public | File audio/video bài học (hỗ trợ HTTP Range để tua) |
| GET | `/lessons/{id}/exercises` | đăng nhập | Bài tập / quiz của bài học + kết quả tốt nhất |
| POST | `/exercises/{testId}/attempts` | đăng nhập | Bắt đầu / tiếp tục lượt làm |
| POST | `/exercises/attempts/{id}/check` | đăng nhập | Chấm 1 câu ngay (đáp án, lời giải, nhận xét viết / nói) |
| POST | `/exercises/attempts/{id}/finish` | đăng nhập | Nộp: điểm, đạt/chưa đạt; đạt thì hoàn thành bài học |
| POST | `/speaking/submissions` | đăng nhập | Multipart: `audio` (WAV 16 kHz) + `questionId`/`referenceText`, `clientTranscript` → điểm phát âm |
| GET | `/speaking/submissions[/{id}/audio]` | đăng nhập | Lịch sử luyện nói / nghe lại bản ghi của mình |
| POST | `/writing/evaluate` | đăng nhập | Chấm bài viết tự do `{text, prompt?, minWords?, maxWords?}` |
| GET | `/flashcards/summary` | đăng nhập | Số thẻ đến hạn, thẻ mới còn trong ngày, phân bố trạng thái, dự báo 7 ngày |
| GET | `/flashcards/queue?limit=20` | đăng nhập | Thẻ cho một phiên (đến hạn trước, rồi thẻ mới) + khoảng ôn dự kiến từng nút |
| POST | `/flashcards/{userWordId}/review` | đăng nhập | `{grade: AGAIN\|HARD\|GOOD\|EASY, responseTimeMs}` → lịch ôn mới (SM-2) |
| PATCH | `/vocabulary/my-words/{id}/suspend` | đăng nhập | Tạm ẩn / đưa lại vào lịch ôn |
| GET | `/vocabulary/sets` · `/vocabulary/sets/{id}` | đăng nhập | Bộ từ vựng có sẵn |
| POST | `/vocabulary/sets/{id}/add` | đăng nhập | Thêm cả bộ vào sổ từ |
| GET | `/notifications?unreadOnly=` | đăng nhập | Thông báo của tôi (phân trang, mới nhất trước) |
| GET | `/notifications/unread-count` | đăng nhập | `{count}` cho chuông thông báo |
| POST | `/notifications/{id}/read` · `/notifications/read-all` | đăng nhập | Đánh dấu đã đọc |
| DELETE | `/notifications/{id}` | đăng nhập | Xóa thông báo |
| GET | `/games/words?gameType=MATCHING\|SCRAMBLE\|DICTATION&count=` | đăng nhập | Bộ từ để chơi (ưu tiên sổ từ, bổ sung từ kho theo trình độ) |
| POST | `/games/results` | đăng nhập | `{gameType, score 0–100, correctCount, totalCount, durationSeconds}` → kỷ lục cá nhân |
| GET | `/games/stats` | đăng nhập | Số lượt, điểm cao nhất, lần chơi gần nhất theo trò |
| POST | `/admin/jobs/study-reminders` | ADMIN | Chạy ngay job nhắc học (job tự chạy 15 phút/lần) |
| GET | `/admin/reports/overview` | ADMIN | Số học viên, hoạt động 7 ngày, điểm xếp lớp TB, phân bố level |
| GET | `/admin/reports/activity?days=7..365` | ADMIN | Theo ngày: học viên hoạt động, đăng ký mới, phút học, bài hoàn thành, lượt ôn từ |
| GET | `/admin/reports/content` | ADMIN | Bài học hoàn thành nhiều / bỏ dở nhiều, bài test (điểm TB, tỉ lệ đạt), câu hỏi khó nhất, điểm nói/viết, từ vựng |
| GET | `/admin/reports/learners.csv` | ADMIN | Xuất CSV học viên (UTF-8 BOM, chống CSV injection) |
| GET/POST | `/admin/courses` · `/admin/courses/options` | ADMIN | Tìm (cả bản nháp) / tạo khóa học; slug tự sinh từ tên tiếng Việt |
| GET/PUT/DELETE | `/admin/courses/{id}` | ADMIN | Chi tiết (kèm bài học) / sửa / xóa (409 `CONTENT_IN_USE` nếu đã có học viên) |
| PATCH · PUT | `/admin/courses/{id}/publish` · `/admin/courses/{id}/lesson-order` | ADMIN | Xuất bản / ẩn · sắp xếp bài trong khóa |
| GET/POST | `/admin/lessons` | ADMIN | Tìm (lọc khóa, kỹ năng, level, trạng thái) / tạo bài học |
| GET/PUT/DELETE · PATCH | `/admin/lessons/{id}` · `/publish` | ADMIN | Chi tiết (phụ đề, quiz) / sửa / xóa · xuất bản |
| PUT | `/admin/lessons/{id}/transcript` | ADMIN | Thay toàn bộ phụ đề `{segments:[{startMs,endMs,text,translation}]}` |
| POST | `/admin/media?kind=AUDIO\|VIDEO\|IMAGE` | ADMIN | Multipart `file` → `{url}`; định dạng nhận diện theo nội dung file |
| GET/POST · GET/PUT/DELETE | `/admin/questions` · `/admin/questions/{id}` | ADMIN | Ngân hàng câu hỏi (lọc dạng, kỹ năng, level, `excludeTestId`) |
| PATCH · POST | `/admin/questions/{id}/active` · `/duplicate` | ADMIN | Bật/tắt · nhân bản câu hỏi |
| GET/POST · GET/PUT/DELETE | `/admin/tests` · `/admin/tests/{id}` | ADMIN | Bài test: xếp lớp, quiz bài học, luyện tập, thi thử |
| PUT | `/admin/tests/{id}/questions` | ADMIN | `{items:[{questionId, points?}]}` theo thứ tự; 409 `TEST_HAS_ATTEMPTS` nếu đã có lượt làm |
| PATCH · POST | `/admin/tests/{id}/publish` · `/duplicate` | ADMIN | Xuất bản · nhân bản (bản nháp) |
| GET/POST · GET/PUT/DELETE | `/admin/vocabulary/words` · `/{id}` | ADMIN | Kho từ (lọc từ loại, level, nguồn, `excludeSetId`) |
| GET/POST · GET/PUT/DELETE | `/admin/vocabulary/sets` · `/{id}` | ADMIN | Bộ từ hệ thống |
| PUT · PATCH | `/admin/vocabulary/sets/{id}/words` · `/publish` | ADMIN | Thay danh sách từ `{wordIds}` · xuất bản |
| GET | `/admin/users` | ADMIN | Tìm kiếm: `keyword, status, role, levelId, page, size, sort` |
| GET / PUT | `/admin/users/{id}` | ADMIN | Xem / cập nhật user, vai trò, level |
| POST | `/admin/users` | ADMIN | Tạo user |
| PATCH | `/admin/users/{id}/status` | ADMIN | Khóa / mở khóa / vô hiệu hóa |

### Định dạng câu trả lời (`AnswerRequest`)
```jsonc
{ "questionId": 12, "selectedOptionIds": [45] }                 // SINGLE/MULTIPLE_CHOICE
{ "questionId": 21, "textAnswers": ["drinks", "is drinking"] }  // FILL_BLANK (theo thứ tự {{1}}, {{2}})
{ "questionId": 22, "matches": { "88": "luôn luôn" } }          // MATCHING: optionId -> vế phải
{ "questionId": 23, "orderedOptionIds": [93, 95, 91, 94, 92] }  // ORDERING
{ "questionId": 26, "answerText": "Last weekend I ..." }        // ESSAY / SPEAKING
```

### Mã lỗi chính
`VALIDATION_ERROR` 400 · `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` / `INVALID_CREDENTIALS` / `INVALID_REFRESH_TOKEN` 401 ·
`FORBIDDEN` / `ACCOUNT_LOCKED` 403 · `RESOURCE_NOT_FOUND` 404 · `EMAIL_ALREADY_EXISTS` / `ATTEMPT_EXPIRED` / `ATTEMPT_NOT_IN_PROGRESS` 409.
Frontend nên tự gọi `/auth/refresh` khi gặp `TOKEN_EXPIRED`.

## 6. Ghi chú thiết kế
- **Refresh token rotation + phát hiện đánh cắp:** mỗi lần refresh, token cũ bị thu hồi với lý do `ROTATED`. Nếu một token `ROTATED` bị dùng lại → thu hồi mọi phiên của user. Token bị thu hồi vì logout/khóa tài khoản chỉ đơn giản bị từ chối.
- **Access token không tra DB mỗi request** (stateless). User bị khóa sẽ mất quyền khi access token hết hạn (tối đa 15 phút) vì refresh token đã bị thu hồi ngay.
- **Thời gian làm bài kiểm soát ở server:** `ExpiresAt` + 30 giây ân hạn. Bài quá hạn được tự chấm với các câu đã autosave (status `EXPIRED`).
- **Chống race condition:** khóa dòng (`UPDLOCK`) khi bắt đầu bài và khi nộp bài; nộp lại là idempotent.
- `AnswerGrader` dùng chung cho Placement và Quiz (giai đoạn sau): chấm một phần cho FILL_BLANK/MATCHING, ORDERING so theo nội dung nên chịu được từ lặp.
- **Liên kết module bằng sự kiện** (đồng bộ, cùng transaction): nộp bài xếp lớp → `PlacementCompletedEvent` → tự tạo lộ trình nếu chưa có; học/hoàn thành bài → `LessonStarted/LessonCompleted` → cập nhật trạng thái lộ trình. Module lộ trình không bị module khác phụ thuộc ngược.
- **Sinh lộ trình:** khóa học ở level hiện tại + level kế tiếp, khóa đúng mục tiêu xếp trước; trong mỗi nhóm 4 bài, bài thuộc kỹ năng yếu (< 60% ở bài xếp lớp) được đẩy lên đầu; lên lịch ngày học theo số phút/ngày (`PathPlanner`, có unit test).
- **Thời gian học & streak** ghi bằng `MERGE ... WITH (HOLDLOCK)` (upsert nguyên tử, an toàn khi nhiều tab) theo ngày ở múi giờ học viên.
- **Audio demo** sinh bằng `scripts/generate-demo-audio.ps1` (giọng TTS Windows). Chạy lại script thì phải cập nhật mốc thời gian phụ đề trong migration.
- **Chấm phát âm (`PronunciationScorer`):**
  - `MockPronunciationScorer` (mặc định): không phân tích âm thanh. Nếu trình duyệt gửi kèm văn bản tự nhận dạng (Chrome/Edge), bộ chấm so khớp từng từ với câu mẫu bằng Levenshtein → độ chính xác / đầy đủ; độ trôi chảy tính theo số từ/phút. Không có văn bản thì chỉ ước lượng theo thời lượng (`estimated = true`, tối đa 80 điểm).
  - `AzurePronunciationScorer`: gọi Azure Pronunciation Assessment REST. **Chưa được chạy thử với key thật.**
- **Chấm bài viết (`WritingEvaluator`):** `RuleBasedWritingEvaluator` phát hiện lỗi chính tả thường gặp, quá khứ sai của động từ bất quy tắc, a/an, hòa hợp chủ ngữ–động từ cơ bản, viết hoa, dấu câu, lặp từ, câu quá dài; chấm 4 tiêu chí; trả vị trí lỗi + bản sửa. Giới hạn: không bắt được lỗi chia thì theo ngữ cảnh ("eat" → "ate"). Có thể thay bằng bộ chấm dùng mô hình ngôn ngữ sau này mà không sửa nghiệp vụ.
- **Flashcard SRS (`Sm2Scheduler`, có unit test):** SM-2 với 4 nút — Quên (q=1, học lại sau 10 phút, đếm lapse nếu từ đang ôn), Khó (q=3), Được (q=4), Dễ (q=5). Khoảng ôn 1 → 6 → I×EF; Khó ×1,2, Dễ ×EF×1,3; EF ≥ 1,3. Trạng thái NEW → LEARNING (< 6 ngày) → REVIEW → MASTERED (≥ 21 ngày). Giới hạn thẻ mới/ngày tính bằng số thẻ có lần ôn đầu tiên trong ngày (theo múi giờ học viên). Mỗi lượt ôn ghi `FlashcardReviews` và cộng thời gian suy nghĩ (tối đa 60 giây/thẻ) vào thời gian học.
- **Bài tập trong bài học** dùng chung `TestAttempts` / `AnswerGrader` với Placement Test; phần hiển thị câu hỏi / xem lại được tách vào `QuestionPresenter`.
- **Nhắc học (`StudyReminderJob`, 15 phút/lần):** với mỗi học viên bật nhắc học, `StudyReminderService` gom số phút đã học hôm nay, streak, số thẻ đến hạn (theo múi giờ học viên) rồi hỏi `ReminderPolicy` (hàm thuần, có unit test):
  - `STUDY_REMINDER` khi đã qua giờ nhắc học viên chọn mà chưa đạt mục tiêu ngày — nhắc kèm số thẻ cần ôn và link tới phiên ôn nếu có.
  - `STREAK` (cảnh báo mất chuỗi) sau 20:00 nếu chuỗi ≥ 2 ngày mà hôm nay chưa học.
  - Mỗi loại tối đa 1 lần/ngày (kiểm tra theo đầu ngày ở múi giờ học viên) nên job chạy lại hay nhiều instance cũng không gửi trùng trong ngày; lỗi của 1 học viên không làm dừng cả job.
- **Thông báo theo sự kiện:** đạt mốc streak 3/7/14/21/30/50/100/200/365 ngày (`MERGE ... OUTPUT inserted.CurrentStreak` → `StreakMilestone`) → `ACHIEVEMENT`; xếp lớp xong → `PATH_UPDATED`. Kênh email để sẵn trong bảng (`Channel`), hiện chỉ dùng `IN_APP`.
- **Trò chơi:** bộ từ lấy ngẫu nhiên từ sổ từ (trừ từ tạm ẩn); thiếu thì bổ sung từ kho chung ở trình độ hiện tại trở xuống. Xếp chữ / nghe chép chỉ dùng từ đơn 3–12 chữ cái. Điểm do client tính (luyện tập cá nhân, không có bảng xếp hạng) nên server chỉ kiểm tra giới hạn; thời gian chơi (tối đa 15 phút/lượt) được cộng vào thời gian học & streak.
- **CMS - toàn vẹn dữ liệu học viên:**
  - Xóa khóa học / bài học / câu hỏi / bài test / từ chỉ khi chưa có dữ liệu học viên gắn vào (`ContentUsageRepository`); ngược lại trả `CONTENT_IN_USE` để admin **ẩn** (bỏ xuất bản / tắt) thay vì xóa.
  - Bài test đã có lượt làm không đổi được danh sách câu hỏi (kết quả cũ, điểm theo kỹ năng, phần xem lại dựa vào đó) → `TEST_HAS_ATTEMPTS`; admin **nhân bản** rồi sửa bản mới. Câu hỏi đã có người trả lời không đổi được dạng câu hỏi; đổi đáp án chỉ áp dụng cho lượt làm sau.
  - Bài xếp lớp đang dùng = bài `PLACEMENT` đã xuất bản mới nhất; chỉ nhận câu hỏi chấm tự động và bắt buộc có giới hạn thời gian.
- **Kiểm tra câu hỏi (`QuestionDefinitionValidator`, có unit test):** một đáp án = đúng 1 phương án đúng; điền từ = ô trống `{{1}}..{{n}}` liên tiếp khớp số mảng đáp án (AnswerKey được chuẩn hóa); nối cặp cần vế phải; sắp xếp cần hoán vị 1..n; luyện nói cần câu mẫu.
- **Upload media:** `MediaSniffer` nhận diện định dạng theo magic bytes (mp3, wav, m4a, ogg, mp4, webm, jpg, png, webp, gif) - file HTML/SVG đội lốt ảnh bị từ chối; tên file là UUID, phục vụ công khai có cache 30 ngày và hỗ trợ HTTP Range. Chưa có dọn file không còn được dùng.
- **Hibernate - thay danh sách con có ràng buộc UNIQUE / khóa ghép** (phụ đề, câu hỏi của bài test, từ của bộ): xóa → `flush` (hoặc xóa hàng loạt với `clearAutomatically`) rồi mới thêm, vì Hibernate mặc định INSERT trước DELETE khi flush.
- **Lưu ý khi viết migration:** không viết chuỗi `/` + `*` bên trong block comment — T-SQL hỗ trợ comment lồng nhau nên cả file sẽ thành comment mà Flyway vẫn báo thành công.
