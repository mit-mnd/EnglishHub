-- EnglishHub - V6: Đồng bộ yêu cầu độ dài bài viết "cuối tuần" (đề, mô tả bài học) với cấu hình chấm 80-120 từ
UPDATE Questions
SET Content = N'Write 80-120 words about your last weekend. Use the past simple.', UpdatedAt = SYSUTCDATETIME()
WHERE Id = 26;

UPDATE Lessons
SET Summary = N'Viết đoạn văn 80-120 từ kể về cuối tuần vừa rồi.',
    Content = N'<p>Write 80-120 words about your last weekend. Use the past simple tense.</p>',
    UpdatedAt = SYSUTCDATETIME()
WHERE Id = 7;
