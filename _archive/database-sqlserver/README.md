# Database – EnglishLearningDB

## Cách 1 (khuyến nghị): Flyway tự tạo bảng
1. Tạo database (1 lần) – trong SSMS mở `00_create_database.sql` và Execute, hoặc:
   ```bash
   sqlcmd -S "EA-MANH\SQLEXPRESS" -E -C -i database/00_create_database.sql
   ```
2. Từ Giai đoạn 2, khi chạy backend, Flyway tự áp dụng
   `backend/src/main/resources/db/migration/V1__init_schema.sql` và `V2__seed_data.sql`
   (lịch sử lưu ở bảng `flyway_schema_history`).

## Cách 2: Chạy tay toàn bộ script
```bash
sqlcmd -S "EA-MANH\SQLEXPRESS" -E -C -i database/00_create_database.sql
sqlcmd -S "EA-MANH\SQLEXPRESS" -E -C -I -f 65001 -d EnglishLearningDB -i backend/src/main/resources/db/migration/V1__init_schema.sql
sqlcmd -S "EA-MANH\SQLEXPRESS" -E -C -I -f 65001 -d EnglishLearningDB -i backend/src/main/resources/db/migration/V2__seed_data.sql
```
- `-E` = Windows Authentication; dùng SQL login thì thay bằng `-U sa -P <mật khẩu>`.
- `-I` bắt buộc (bật QUOTED_IDENTIFIER cho filtered index), `-f 65001` để đọc file UTF-8 (tiếng Việt).
- Nếu đã chạy tay mà sau đó muốn dùng Flyway, đặt `spring.flyway.baseline-on-migrate=true` và `baseline-version=2`.

## Kiểm tra nhanh
```sql
USE EnglishLearningDB;
SELECT COUNT(*) AS TableCount FROM sys.tables;          -- 30 (31 nếu có flyway_schema_history)
SELECT Code, Name FROM Levels ORDER BY SortOrder;       -- 5 level, tiếng Việt hiển thị đúng
SELECT Email, Status FROM Users;                         -- admin + learner
SELECT COUNT(*) FROM TestQuestions WHERE TestId = 1;     -- 20 câu placement
```

## Tài khoản mẫu
| Email | Mật khẩu | Vai trò |
|---|---|---|
| admin@englishhub.local | Admin@123 | ADMIN, LEARNER |
| learner@englishhub.local | Learner@123 | LEARNER (có sẵn dữ liệu dashboard, lộ trình, flashcard) |

> Đổi mật khẩu các tài khoản này trước khi đưa lên môi trường thật.

## Làm lại từ đầu (chỉ môi trường dev)
```sql
USE master;
ALTER DATABASE EnglishLearningDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
DROP DATABASE EnglishLearningDB;
```
