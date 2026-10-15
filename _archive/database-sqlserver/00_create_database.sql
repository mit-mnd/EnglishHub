/* =====================================================================
   EnglishHub - Tạo database (chạy 1 lần bằng SSMS hoặc sqlcmd, quyền sysadmin)
   Sau khi tạo DB, Flyway (khi backend khởi động) sẽ tự chạy:
     backend/src/main/resources/db/migration/V1__init_schema.sql
     backend/src/main/resources/db/migration/V2__seed_data.sql
   ===================================================================== */
USE master;
GO

IF DB_ID(N'EnglishLearningDB') IS NULL
BEGIN
    CREATE DATABASE EnglishLearningDB;
END
GO

ALTER DATABASE EnglishLearningDB SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;  -- giảm lock đọc/ghi
GO
ALTER DATABASE EnglishLearningDB SET RECOVERY SIMPLE;   -- phù hợp môi trường dev
GO

/* (Khuyến nghị) Tạo login riêng cho ứng dụng thay vì dùng 'sa'.
   Bỏ comment, đổi mật khẩu rồi chạy; sau đó đặt DB_USERNAME/DB_PASSWORD tương ứng.

CREATE LOGIN englishhub_app WITH PASSWORD = N'<DoiMatKhauManh!2026>', CHECK_POLICY = ON;
GO
USE EnglishLearningDB;
GO
CREATE USER englishhub_app FOR LOGIN englishhub_app;
ALTER ROLE db_owner ADD MEMBER englishhub_app;   -- Flyway cần quyền DDL; production nên tách user migrate/user runtime
GO
*/
