/* =====================================================================
   EnglishHub - V5: Bài tập trong bài học (Speaking, Writing, quiz TOEIC, quiz nghe hiểu)
   - SPEAKING: AnswerKey = câu mẫu cần đọc
   - ESSAY:    AnswerKey = JSON yêu cầu độ dài, ví dụ {"minWords":80,"maxWords":120}
   ===================================================================== */

-- Câu 26 (viết về cuối tuần) đã có từ V2: bổ sung yêu cầu độ dài
UPDATE Questions SET AnswerKey = N'{"minWords":80,"maxWords":120}', UpdatedAt = SYSUTCDATETIME() WHERE Id = 26;
UPDATE Questions SET Content = N'Read the sentence aloud.', UpdatedAt = SYSUTCDATETIME() WHERE Id = 25;

SET IDENTITY_INSERT Questions ON;
INSERT INTO Questions (Id, QuestionType, Skill, LevelId, TopicId, Content, AnswerKey, Explanation, Difficulty, DefaultPoints, CreatedBy) VALUES
 -- Luyện nói (bài 3)
 (27, 'SPEAKING', 'SPEAKING', 1, 1, N'Read the sentence aloud.', N'Nice to meet you.', NULL, 1, 1.0, 1),
 (28, 'SPEAKING', 'SPEAKING', 1, 1, N'Read the sentence aloud.', N'I am from Vietnam and I live in Hanoi.', NULL, 1, 1.0, 1),
 (29, 'SPEAKING', 'SPEAKING', 1, 1, N'Read the sentence aloud.', N'My favourite hobby is reading books.', NULL, 1, 1.0, 1),
 -- Luyện viết (bài 7): chia quá khứ đơn
 (30, 'FILL_BLANK', 'WRITING', 2, 1, N'Last Saturday I {{1}} (go) to the beach and {{2}} (swim) in the sea.',
      N'[["went"],["swam"]]', N'go -> went, swim -> swam (động từ bất quy tắc).', 2, 2.0, 1),
 (31, 'FILL_BLANK', 'WRITING', 2, 1, N'We {{1}} (not / watch) TV last night because we were tired.',
      N'[["did not watch","didn''t watch"]]', N'Phủ định quá khứ đơn: did not + động từ nguyên mẫu.', 2, 1.0, 1),
 -- Viết email (bài 11)
 (32, 'ESSAY', 'WRITING', 3, 3,
      N'Write an email to your manager asking for two days off next week. Explain why and suggest how your work will be covered.',
      N'{"minWords":80,"maxWords":160}', NULL, 3, 10.0, 1),
 -- IELTS Writing Task 2 (bài 14): viết mở bài
 (33, 'ESSAY', 'WRITING', 5, 4,
      N'Write an introduction for this topic: "Some people think university education should be free for everyone. To what extent do you agree or disagree?"',
      N'{"minWords":40,"maxWords":80}', NULL, 4, 10.0, 1),
 -- TOEIC Part 5 (bài 13)
 (34, 'SINGLE_CHOICE', 'GRAMMAR', 5, 3, N'Ms. Tran is responsible ___ training new employees.', NULL,
      N'Cụm cố định: be responsible for + V-ing.', 4, 1.0, 1),
 (35, 'SINGLE_CHOICE', 'GRAMMAR', 5, 3, N'The conference has been ___ until next month because of the storm.', NULL,
      N'Bị động thì hiện tại hoàn thành: has been + V3 (postponed).', 4, 1.0, 1),
 (36, 'SINGLE_CHOICE', 'GRAMMAR', 5, 3, N'Please read the instructions ___ before using the new machine.', NULL,
      N'Bổ nghĩa cho động từ "read" cần trạng từ: carefully.', 4, 1.0, 1),
 (37, 'SINGLE_CHOICE', 'VOCABULARY', 5, 3, N'Our sales increased ___ 15% in the last quarter.', NULL,
      N'"increase by + mức thay đổi".', 4, 1.0, 1),
 -- Nghe hiểu (bài 5)
 (38, 'SINGLE_CHOICE', 'LISTENING', 2, 2, N'What drink does the customer order?', NULL,
      N'"Can I have a large iced latte, please?"', 2, 1.0, 1),
 (39, 'SINGLE_CHOICE', 'LISTENING', 2, 2, N'How much does the customer pay?', NULL,
      N'"That''s six dollars fifty." = $6.50', 2, 1.0, 1);
SET IDENTITY_INSERT Questions OFF;

INSERT INTO QuestionOptions (QuestionId, Content, IsCorrect, SortOrder) VALUES
 (34, N'for', 1, 1), (34, N'of', 0, 2), (34, N'to', 0, 3), (34, N'with', 0, 4),
 (35, N'postpone', 0, 1), (35, N'postponed', 1, 2), (35, N'postponing', 0, 3), (35, N'postpones', 0, 4),
 (36, N'careful', 0, 1), (36, N'care', 0, 2), (36, N'carefully', 1, 3), (36, N'caring', 0, 4),
 (37, N'by', 1, 1), (37, N'at', 0, 2), (37, N'on', 0, 3), (37, N'in', 0, 4),
 (38, N'A hot tea', 0, 1), (38, N'A large iced latte', 1, 2), (38, N'A cappuccino', 0, 3), (38, N'An orange juice', 0, 4),
 (39, N'$5.60', 0, 1), (39, N'$16.50', 0, 2), (39, N'$6.50', 1, 3), (39, N'$6.15', 0, 4);

SET IDENTITY_INSERT Tests ON;
INSERT INTO Tests (Id, Title, Description, TestType, LessonId, LevelId, DurationMinutes, PassingPercent, ShuffleQuestions, IsPublished, CreatedBy) VALUES
 (3, N'Luyện nói: Giới thiệu bản thân', N'Nghe mẫu, thu âm và nhận điểm phát âm cho từng câu.', 'PRACTICE', 3, 1, NULL, 60.00, 0, 1, 1),
 (4, N'Luyện viết: Cuối tuần của tôi', N'Chia động từ quá khứ đơn rồi viết đoạn văn 80-120 từ.', 'PRACTICE', 7, 2, NULL, 60.00, 0, 1, 1),
 (5, N'Viết email xin nghỉ phép', N'Viết email 80-160 từ và nhận nhận xét tự động.', 'PRACTICE', 11, 3, NULL, 60.00, 0, 1, 1),
 (6, N'Viết mở bài IELTS Task 2', N'Viết đoạn mở bài 40-80 từ.', 'PRACTICE', 14, 5, NULL, 60.00, 0, 1, 1),
 (7, N'Quiz: TOEIC Part 5', N'4 câu điền từ phong cách TOEIC.', 'LESSON_QUIZ', 13, 5, NULL, 75.00, 1, 1, 1),
 (8, N'Quiz nghe hiểu: At the coffee shop', N'Kiểm tra bạn đã nghe hiểu đoạn hội thoại chưa.', 'LESSON_QUIZ', 5, 2, NULL, 50.00, 0, 1, 1);
SET IDENTITY_INSERT Tests OFF;

INSERT INTO TestQuestions (TestId, QuestionId, SortOrder) VALUES
 (3, 25, 1), (3, 27, 2), (3, 28, 3), (3, 29, 4),
 (4, 30, 1), (4, 31, 2), (4, 26, 3),
 (5, 32, 1),
 (6, 33, 1),
 (7, 34, 1), (7, 35, 2), (7, 36, 3), (7, 37, 4),
 (8, 38, 1), (8, 39, 2);
