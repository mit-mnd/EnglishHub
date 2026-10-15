# EnglishHub

Ứng dụng học tiếng Anh cá nhân hóa cho người Việt - **chạy hoàn toàn trên trình duyệt**:
không cần máy chủ, không cần cơ sở dữ liệu, không cần đăng nhập.

- **Nội dung học** (khóa học, bài học, từ vựng, mẫu câu, đề luyện TOEIC) là các file JSON trong [`data/`](data/README.md).
- **Dữ liệu người học** (hồ sơ, tiến độ, sổ từ, lịch ôn, kết quả) lưu trong localStorage của trình duyệt;
  xuất / nhập bằng file sao lưu `.json` ở trang *Hồ sơ & dữ liệu*.
- Lần đầu mở ứng dụng, người học khai báo tên, mục tiêu, trình độ (hoặc làm bài xếp lớp) và thời gian học mỗi ngày.

## Chạy

Yêu cầu Node.js 20+.

```bash
npm install
npm run dev          # http://localhost:5173
```

```bash
npm test             # unit test + test tích hợp + kiểm tra dữ liệu JSON
npm run validate:data
npm run build        # bản build tĩnh trong dist/ - đưa lên bất kỳ static hosting nào (Netlify, GitHub Pages, Nginx...)
```

Bản build là trang tĩnh: chỉ cần phục vụ thư mục `dist/` và chuyển mọi đường dẫn về `index.html` (SPA fallback).

**Triển khai:** repo GitHub `mit-mnd/EnglishHub` được nối với project Vercel `englishhub`: `git push` lên `main` tự deploy production
(https://englishhub-eight.vercel.app), các nhánh khác có bản xem trước riêng. Cấu hình SPA fallback nằm ở `vercel.json`.

## Tính năng

| Nhóm | Chức năng |
|---|---|
| Bắt đầu | Khai báo hồ sơ lần đầu · khôi phục từ file sao lưu |
| Xếp lớp & lộ trình | Bài kiểm tra 20 câu (tự lưu, hẹn giờ) → xếp 5 trình độ, điểm theo kỹ năng → lộ trình tự sinh theo trình độ + mục tiêu + kỹ năng yếu, lên lịch theo phút/ngày; bỏ qua, sắp xếp, thêm bài |
| 4 kỹ năng | Nghe: phụ đề tương tác, tốc độ 0.5-2x · Đọc: bấm từ để tra & lưu sổ · Nói: ghi âm, chấm phát âm từng từ (Web Speech API) · Viết: chấm theo quy tắc, gợi ý sửa lỗi |
| Từ vựng | 4.313 từ (đời sống, TOEIC, B1-B2, cụm từ & collocation, học thuật/IELTS), 97 bộ theo chủ đề (lọc theo nhóm, tìm kiếm), tra từ tự động (kho nội bộ → Free Dictionary API + Wiktionary + MyMemory dịch sang tiếng Việt, lưu cache vào máy), dịch cả câu / đoạn khi bôi đen, flashcard lặp lại ngắt quãng SM-2 (không giới hạn thẻ mới mỗi ngày, học theo lô 20 thẻ) |
| Mẫu câu | 22 chủ đề · 272 mẫu câu đời sống / công việc / TOEIC, nghe mẫu, lưu, tự kiểm tra |
| Luyện thi TOEIC | 788 câu Part 2-7 có giải thích tiếng Việt & bản dịch · luyện theo Part (ưu tiên câu chưa làm) · đề thi thử rút gọn có điểm ước lượng · phần Nghe đọc bằng giọng nam/nữ của trình duyệt |
| Luyện tập | Trò chơi ghép từ, xếp chữ, nghe chép (kỷ lục cá nhân) |
| Theo dõi | Dashboard: chuỗi ngày học, mục tiêu ngày, biểu đồ 7 ngày - 12 tháng · thông báo nhắc học, cảnh báo mất chuỗi, thành tích |
| Giao diện | Sáng / Tối / Theo hệ thống (nút trên thanh trên, chọn ở Hồ sơ; nhớ lựa chọn, không nháy khi tải trang), chuyển trang mượt, dùng tốt trên điện thoại |

## Kiến trúc

```
Vue views ──> src/api (giữ nguyên giao diện API cũ, trả Promise)
                 │
                 ▼
          src/core/services   nghiệp vụ: xếp lớp, bài tập, lộ trình, từ vựng, flashcard, TOEIC...
             │         │
             ▼         ▼
   src/core/domain   src/core/storage (localStorage, có phiên bản schema, sao lưu JSON)
   (thuật toán thuần,      src/core/content (nạp /data/*.json; mẫu câu & TOEIC tải khi cần)
    có unit test)
```

- Lớp `src/api` giữ nguyên tên hàm và dạng dữ liệu của REST API bản trước, nên màn hình không phụ thuộc
  nơi lưu dữ liệu. Muốn đồng bộ lên máy chủ sau này chỉ cần thay phần ruột của `src/api` / `src/core/storage`.
- Thuật toán thuần trong `src/core/domain` (chuyển từ bản Java, giữ nguyên bộ test): chấm câu hỏi 7 dạng, SM-2,
  xếp level, lập lịch lộ trình, chấm bài viết, so khớp phát âm, chính sách nhắc học, kiểm tra cấu trúc câu hỏi.

```
├── data/                  nội dung học (JSON) - xem data/README.md
├── public/media/          audio bài nghe
├── src/
│   ├── api/               mặt tiền API cho màn hình
│   ├── core/
│   │   ├── content/       nạp dữ liệu JSON
│   │   ├── domain/        thuật toán thuần (+ __tests__)
│   │   ├── services/      nghiệp vụ (+ test tích hợp toàn luồng người học)
│   │   ├── storage/       localStorage + sao lưu / khôi phục
│   │   ├── clock.js       thời gian (cố định được trong test)
│   │   └── errors.js      AppError { code, status, message, fieldErrors }
│   ├── components/        quiz, lesson, exercise, flashcard, game, toeic, notification, charts, common
│   ├── composables/       useSpeech (đọc văn bản, giọng nam/nữ), useWavRecorder, useStudyTracker...
│   ├── layouts/           AuthLayout (trang chào mừng), LearnerLayout (sidebar / thanh dưới trên điện thoại)
│   ├── router/            route + chặn khi chưa khai báo hồ sơ
│   ├── stores/            profile, dashboard, notifications (Pinia)
│   ├── utils/             format, labels, game, toeic, wav... (+ __tests__)
│   └── views/             onboarding/WelcomeView, learner/* (dashboard, bài học, từ vựng, mẫu câu, TOEIC...)
└── _archive/              mã nguồn bản 1 (Spring Boot + SQL Server) - chỉ để tham khảo, có thể xóa
```

## Lưu ý

- Dữ liệu chỉ nằm trên trình duyệt đang dùng. Xóa dữ liệu trình duyệt / chế độ ẩn danh / đổi máy sẽ mất tiến độ
  nếu không có file sao lưu. localStorage giới hạn ~5 MB: lịch sử ôn tập, kết quả luyện tập được tự cắt bớt phần cũ.
- Ghi âm cần micro và chỉ chạy trên `localhost` hoặc HTTPS. Chấm phát âm từng từ cần Chrome / Edge (Web Speech API);
  trình duyệt khác chỉ cho điểm ước lượng. Bản ghi âm chỉ nghe lại được trong phiên hiện tại.
- Tra từ ngoài kho nội bộ cần mạng (Free Dictionary API, Wiktionary, MyMemory); từ tra được lưu lại trên máy.

## Giao diện & màu sắc

- Mọi màu đi qua token `--eh-*` trong `src/styles/main.css`; chế độ tối chỉ ghi đè token ở `html.dark`. **Không viết màu hex trong component**:
  dùng `var(--eh-surface)`, `--eh-text`, `--eh-success-soft` / `--eh-success-text`... (đủ bộ cho primary, success, accent, warn, danger, info).
- Chữ đặt trên nền màu đặc (`--eh-primary`, `--eh-success`...) dùng `var(--eh-on-primary)` (ở chế độ tối nền sáng hơn nên chữ phải tối).
- Nền nhạt cho biểu tượng: `color-mix(in srgb, <màu> 14%, transparent)`, đúng ở cả hai chế độ.
- Biểu đồ ECharts (canvas) không hiểu `var()`: lấy màu bằng `cssVar('--eh-primary')` (`src/utils/cssVar.js`) và để `EChart.vue` dựng lại khi đổi giao diện.
- Logic đổi giao diện: `src/composables/useTheme.js` (+ script nhỏ trong `index.html` áp class `dark` trước khi vẽ).
- Nhắc học hiển thị trong ứng dụng khi mở EnglishHub sau giờ nhắc (không có thông báo đẩy khi đóng trình duyệt).
- Phần Nghe TOEIC dùng giọng tổng hợp của hệ điều hành; chất lượng tùy máy (Windows có sẵn giọng David / Zira).
- Nội dung TOEIC do EnglishHub tự biên soạn theo định dạng đề thi, không phải đề chính thức của ETS; chưa có Part 1.
