# EnglishHub

Nền tảng học tiếng Anh trực tuyến: Placement Test, lộ trình cá nhân hóa, 4 kỹ năng, flashcard SRS, quiz, theo dõi tiến độ, trang quản trị.

| Thư mục | Nội dung |
|---|---|
| [docs/](docs/) | Kiến trúc tổng thể, ERD |
| [database/](database/) | Script tạo database, hướng dẫn |
| [backend/](backend/) | Spring Boot 3.5 · Java 17+ · SQL Server · Flyway · JWT |
| [frontend/](frontend/) | Vue 3 · Vite · Pinia · Element Plus · ECharts |

## Chạy nhanh (môi trường dev)
1. Tạo database (1 lần): xem [database/README.md](database/README.md)
2. Backend: tạo `backend/.env` từ `.env.example` (điền `DB_PASSWORD`, `JWT_SECRET`), rồi chạy `cd backend && ./mvnw spring-boot:run`
3. Frontend: `cd frontend && npm install && npm run dev`, mở http://localhost:5173

Tài khoản mẫu: `learner@englishhub.local / Learner@123`, `admin@englishhub.local / Admin@123`.

## Tiến độ
- [x] Giai đoạn 1 – Thiết kế: kiến trúc, ERD, script database
- [x] Giai đoạn 2 – Backend nền tảng: JWT, User, Admin user, Placement Test
- [x] Giai đoạn 3 – Frontend nền tảng: layout responsive, xác thực, Placement Test, Dashboard, Admin users
- [x] Giai đoạn 4 – Lộ trình cá nhân hóa, thư viện bài học, Listening (phụ đề tương tác, tốc độ), Reading (tra từ, sổ từ), ghi nhận thời gian học & streak
- [x] Giai đoạn 5 – Speaking (thu âm WAV, chấm phát âm qua interface: Mock / Azure), Writing (chấm theo quy tắc, gợi ý sửa lỗi), bài tập & quiz chấm từng câu
- [x] Giai đoạn 6 – Flashcard SRS (SM-2), bộ từ vựng theo chủ đề, dự báo lịch ôn
- [x] Giai đoạn 7 – Trò chơi từ vựng (ghép từ–nghĩa, xếp chữ, nghe & chép), thông báo trong ứng dụng (nhắc học, cảnh báo mất streak, thành tích, lộ trình sẵn sàng)
- [x] Giai đoạn 8 – Admin CMS: khóa học, bài học (tải audio/video, soạn phụ đề có mốc thời gian, nhập SRT/VTT), ngân hàng câu hỏi 7 dạng, bài test & bài xếp lớp, kho từ & bộ từ; báo cáo hoạt động, hiệu quả nội dung, xuất CSV
