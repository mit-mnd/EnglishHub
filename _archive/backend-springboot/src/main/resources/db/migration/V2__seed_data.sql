/* =====================================================================
   EnglishHub - V2: Dữ liệu mẫu
   Tài khoản mặc định (BẮT BUỘC đổi mật khẩu khi triển khai thật):
     admin@englishhub.local   / Admin@123    (ADMIN)
     learner@englishhub.local / Learner@123  (LEARNER)
   Đường dẫn media (/media/...) là placeholder - thêm file thật ở các giai đoạn sau.
   ===================================================================== */

/* ---------- Roles ---------- */
SET IDENTITY_INSERT Roles ON;
INSERT INTO Roles (Id, Code, Name) VALUES
 (1, 'LEARNER', N'Học viên'),
 (2, 'ADMIN',   N'Quản trị viên');
SET IDENTITY_INSERT Roles OFF;

/* ---------- Levels (khoảng % dùng để xếp lớp sau Placement Test) ---------- */
SET IDENTITY_INSERT Levels ON;
INSERT INTO Levels (Id, Code, Name, Description, CefrRange, PlacementMinPercent, PlacementMaxPercent, SortOrder) VALUES
 (1, 'FOUNDATION',   N'Mất gốc',                   N'Làm quen lại từ đầu: phát âm, từ vựng và ngữ pháp nền tảng.', 'Pre-A1', 0.00,  20.99, 1),
 (2, 'ELEMENTARY',   N'Cơ bản',                    N'Giao tiếp các tình huống quen thuộc hằng ngày.',             'A1-A2',  21.00, 45.99, 2),
 (3, 'INTERMEDIATE', N'Trung cấp',                 N'Diễn đạt ý kiến, hiểu nội dung chính của bài nghe/đọc dài.',  'B1',     46.00, 70.99, 3),
 (4, 'ADVANCED',     N'Nâng cao',                  N'Sử dụng tiếng Anh linh hoạt trong học tập và công việc.',     'B2-C1',  71.00, 85.99, 4),
 (5, 'EXAM_PREP',    N'Luyện thi TOEIC - IELTS',   N'Đủ nền tảng để luyện đề TOEIC 650+ / IELTS 6.5+.',           'B2+',    86.00, 100.00, 5);
SET IDENTITY_INSERT Levels OFF;

/* ---------- Users ---------- */
SET IDENTITY_INSERT Users ON;
INSERT INTO Users (Id, Email, PasswordHash, FullName, Status, CurrentLevelId, LearningGoal, DailyGoalMinutes, ReminderEnabled, ReminderTime) VALUES
 (1, 'admin@englishhub.local',   '$2a$10$GP6B2w/nQcd0vysLsB/abut3bXtt.Oo5BKYR0MKCDlAOUMAINIYFi', N'Quản trị hệ thống', 'ACTIVE', NULL, 'GENERAL',       15, 0, NULL),
 (2, 'learner@englishhub.local', '$2a$10$vh/gcsFFU6CoWWv2SKpv/uxoNd2Vibr6ViGzYzqDOTFbtLaj94bhu', N'Nguyễn Văn An',     'ACTIVE', 2,    'COMMUNICATION', 20, 1, '20:00');
SET IDENTITY_INSERT Users OFF;

INSERT INTO UserRoles (UserId, RoleId) VALUES (1, 2), (1, 1), (2, 1);

/* ---------- Topics ---------- */
SET IDENTITY_INSERT Topics ON;
INSERT INTO Topics (Id, Name, Slug, Description) VALUES
 (1, N'Đời sống hằng ngày',   'daily-life',  N'Chào hỏi, gia đình, thói quen hằng ngày'),
 (2, N'Du lịch',              'travel',      N'Sân bay, khách sạn, hỏi đường'),
 (3, N'Công việc & Kinh doanh','work-business', N'Phỏng vấn, email, cuộc họp'),
 (4, N'Giáo dục',             'education',   N'Trường học, học tập'),
 (5, N'Sức khỏe',             'health',      N'Bệnh viện, lối sống lành mạnh'),
 (6, N'Môi trường & Xã hội',  'environment-society', N'Biến đổi khí hậu, đô thị hóa');
SET IDENTITY_INSERT Topics OFF;

/* ---------- Courses ---------- */
SET IDENTITY_INSERT Courses ON;
INSERT INTO Courses (Id, Title, Slug, Description, LevelId, GoalType, IsPublished, SortOrder, CreatedBy) VALUES
 (1, N'Tiếng Anh cho người mất gốc',   'english-foundation',      N'Xây lại nền tảng: chào hỏi, giới thiệu bản thân, gia đình.', 1, 'GENERAL',       1, 1, 1),
 (2, N'Giao tiếp cơ bản hằng ngày',    'everyday-communication',  N'Tự tin giao tiếp trong quán cà phê, thành phố, cuối tuần.', 2, 'COMMUNICATION', 1, 2, 1),
 (3, N'Tiếng Anh trung cấp 4 kỹ năng', 'intermediate-4-skills',   N'Phỏng vấn xin việc, làm việc từ xa, viết email công việc.', 3, 'GENERAL',       1, 3, 1),
 (4, N'Tiếng Anh nâng cao',            'advanced-english',        N'Đọc hiểu bài báo học thuật, thảo luận các vấn đề xã hội.',  4, 'GENERAL',       1, 4, 1),
 (5, N'Luyện thi TOEIC 650+',          'toeic-650',               N'Chiến lược làm bài TOEIC Reading & Listening.',             5, 'TOEIC',         1, 5, 1),
 (6, N'Luyện thi IELTS 6.5',           'ielts-6-5',               N'Làm quen cấu trúc bài thi IELTS Academic.',                 5, 'IELTS',         1, 6, 1);
SET IDENTITY_INSERT Courses OFF;

/* ---------- Lessons ---------- */
SET IDENTITY_INSERT Lessons ON;
INSERT INTO Lessons (Id, CourseId, TopicId, LevelId, Title, Slug, Skill, Summary, Content, MediaType, MediaUrl, DurationSeconds, EstimatedMinutes, SortOrder, IsPublished) VALUES
 (1, 1, 1, 1, N'Greetings and introductions', 'greetings-and-introductions', 'LISTENING',
     N'Nghe đoạn hội thoại chào hỏi đơn giản giữa hai người mới gặp.', NULL,
     'AUDIO', N'/media/lessons/greetings.mp3', 24, 10, 1, 1),
 (2, 1, 1, 1, N'The alphabet and numbers', 'alphabet-and-numbers', 'VOCABULARY',
     N'Bảng chữ cái, số đếm 1-100 và cách đánh vần tên.',
     N'<h3>Numbers</h3><p>one, two, three, four, five, six, seven, eight, nine, ten ...</p>',
     'NONE', NULL, NULL, 15, 2, 1),
 (3, 1, 1, 1, N'Speaking: Introduce yourself', 'introduce-yourself', 'SPEAKING',
     N'Luyện nói câu giới thiệu bản thân và nhận chấm điểm phát âm.',
     N'<p>Read aloud: <strong>Hello, my name is Lan. I am a student.</strong></p>',
     'NONE', NULL, NULL, 10, 3, 1),
 (4, 1, 1, 1, N'Reading: My family', 'reading-my-family', 'READING',
     N'Bài đọc ngắn về gia đình.',
     N'<p>My name is Minh. I live in Da Nang with my family. There are four people in my family: my father, my mother, my sister and me. My father is a doctor. My mother is a teacher. My sister is ten years old. We love going to the beach on Sundays.</p>',
     'NONE', NULL, NULL, 10, 4, 1),

 (5, 2, 2, 2, N'At the coffee shop', 'at-the-coffee-shop', 'LISTENING',
     N'Gọi đồ uống và thanh toán tại quán cà phê.', NULL,
     'AUDIO', N'/media/lessons/coffee-shop.mp3', 30, 10, 1, 1),
 (6, 2, 2, 2, N'Reading: A day in the city', 'reading-a-day-in-the-city', 'READING',
     N'Đọc nhật ký một ngày khám phá thành phố.',
     N'<p>Last Saturday, Linh took a bus to the city centre. First, she visited the old market and bought some souvenirs. Then she had lunch at a small restaurant near the river. In the afternoon, she went to a museum. It was crowded, but the exhibition was interesting. She got home at seven o''clock, tired but happy.</p>',
     'NONE', NULL, NULL, 12, 2, 1),
 (7, 2, 1, 2, N'Writing: My last weekend', 'writing-my-last-weekend', 'WRITING',
     N'Viết đoạn văn 80-100 từ kể về cuối tuần vừa rồi.',
     N'<p>Write 80-100 words about your last weekend. Use the past simple tense.</p>',
     'NONE', NULL, NULL, 20, 3, 1),
 (8, 2, 1, 2, N'Present simple vs present continuous', 'present-simple-vs-continuous', 'GRAMMAR',
     N'Phân biệt thì hiện tại đơn và hiện tại tiếp diễn.',
     N'<p><strong>Present simple</strong>: thói quen, sự thật (She <em>drinks</em> tea every day).</p><p><strong>Present continuous</strong>: đang diễn ra (She <em>is drinking</em> coffee now).</p>',
     'NONE', NULL, NULL, 15, 4, 1),

 (9, 3, 3, 3, N'A job interview', 'a-job-interview', 'LISTENING',
     N'Xem đoạn phỏng vấn xin việc và trả lời câu hỏi.', NULL,
     'VIDEO', N'/media/lessons/job-interview.mp4', 180, 15, 1, 1),
 (10, 3, 3, 3, N'Reading: The rise of remote work', 'reading-remote-work', 'READING',
     N'Ưu và nhược điểm của làm việc từ xa.',
     N'<p>Since 2020, remote work has become a normal part of many people''s lives. Employees save time on commuting and often feel more productive. However, some managers worry that teamwork and company culture suffer when people rarely meet in person. As a result, many companies now choose a hybrid model.</p>',
     'NONE', NULL, NULL, 15, 2, 1),
 (11, 3, 3, 3, N'Writing: An email to your manager', 'writing-email-to-manager', 'WRITING',
     N'Viết email xin nghỉ phép lịch sự.',
     N'<p>Write an email (120-150 words) to your manager asking for two days off next week. Explain why and suggest how your work will be covered.</p>',
     'NONE', NULL, NULL, 20, 3, 1),

 (12, 4, 6, 4, N'Reading: Climate change and cities', 'reading-climate-change-cities', 'READING',
     N'Bài đọc học thuật về tác động của biến đổi khí hậu lên đô thị.',
     N'<p>Coastal cities are particularly vulnerable to rising sea levels. Urban planners are therefore exploring nature-based solutions, such as restoring wetlands, which can absorb storm surges while also improving biodiversity.</p>',
     'NONE', NULL, NULL, 20, 1, 1),

 (13, 5, 3, 5, N'TOEIC Part 5: Incomplete sentences', 'toeic-part-5', 'GRAMMAR',
     N'Chiến lược làm nhanh câu hỏi điền từ Part 5.',
     N'<p>Tip: read the whole sentence, identify the word type needed (noun, verb, adjective, adverb) before looking at the options.</p>',
     'NONE', NULL, NULL, 20, 1, 1),

 (14, 6, 4, 5, N'IELTS Writing Task 2: Introduction', 'ielts-writing-task-2-intro', 'WRITING',
     N'Cách viết mở bài cho IELTS Writing Task 2.',
     N'<p>Paraphrase the question, then state your position clearly in one sentence.</p>',
     'NONE', NULL, NULL, 25, 1, 1);
SET IDENTITY_INSERT Lessons OFF;

/* ---------- Phụ đề tương tác ---------- */
INSERT INTO LessonTranscriptSegments (LessonId, SortOrder, StartMs, EndMs, Text, Translation) VALUES
 (1, 1,     0,  3500, N'Hi! I''m Anna. What''s your name?',       N'Chào! Mình là Anna. Bạn tên gì?'),
 (1, 2,  3500,  7000, N'Hello, Anna. My name is Nam.',            N'Chào Anna. Mình tên là Nam.'),
 (1, 3,  7000, 11000, N'Nice to meet you, Nam. Where are you from?', N'Rất vui được gặp bạn, Nam. Bạn đến từ đâu?'),
 (1, 4, 11000, 15500, N'I''m from Vietnam. And you?',             N'Mình đến từ Việt Nam. Còn bạn?'),
 (1, 5, 15500, 20000, N'I''m from Canada. Nice to meet you too.',  N'Mình đến từ Canada. Mình cũng rất vui được gặp bạn.'),
 (5, 1,     0,  3000, N'Good morning! What can I get for you?',   N'Chào buổi sáng! Tôi có thể lấy gì cho bạn?'),
 (5, 2,  3000,  7000, N'Can I have a large iced latte, please?',  N'Cho tôi một ly latte đá cỡ lớn nhé.'),
 (5, 3,  7000, 10500, N'Sure. Would you like anything to eat?',   N'Được ạ. Bạn có muốn ăn gì không?'),
 (5, 4, 10500, 14000, N'Yes, a chocolate muffin, please.',        N'Có, một cái bánh muffin sô-cô-la.'),
 (5, 5, 14000, 18000, N'That''s six dollars fifty.',              N'Tổng cộng sáu đô năm mươi xu.'),
 (5, 6, 18000, 22000, N'Here you are. Thank you!',                N'Gửi bạn. Cảm ơn nhé!');

/* ---------- Ngân hàng câu hỏi ---------- */
SET IDENTITY_INSERT Questions ON;
INSERT INTO Questions (Id, QuestionType, Skill, LevelId, TopicId, Content, Passage, AnswerKey, Explanation, Difficulty, DefaultPoints, CreatedBy) VALUES
 -- Placement: mức 1 (Mất gốc)
 (1,  'SINGLE_CHOICE', 'GRAMMAR',    1, 1, N'She ___ a student.', NULL, NULL, N'Chủ ngữ số ít "She" đi với "is".', 1, 1.0, 1),
 (2,  'SINGLE_CHOICE', 'VOCABULARY', 1, 1, N'Which word is a colour?', NULL, NULL, N'"Green" là màu xanh lá.', 1, 1.0, 1),
 (3,  'SINGLE_CHOICE', 'GRAMMAR',    1, 1, N'I ___ from Vietnam.', NULL, NULL, N'"I" đi với "am".', 1, 1.0, 1),
 (4,  'SINGLE_CHOICE', 'VOCABULARY', 1, 1, N'The opposite of "big" is ___.', NULL, NULL, N'big (to) >< small (nhỏ).', 1, 1.0, 1),
 -- Placement: mức 2 (Cơ bản)
 (5,  'SINGLE_CHOICE', 'GRAMMAR',    2, 1, N'They ___ football every Sunday.', NULL, NULL, N'Thói quen -> hiện tại đơn; "They" dùng động từ nguyên mẫu.', 2, 1.0, 1),
 (6,  'SINGLE_CHOICE', 'GRAMMAR',    2, 1, N'Yesterday I ___ to the cinema.', NULL, NULL, N'"Yesterday" -> quá khứ đơn: go -> went.', 2, 1.0, 1),
 (7,  'SINGLE_CHOICE', 'GRAMMAR',    2, 1, N'I usually have breakfast ___ 7 a.m.', NULL, NULL, N'Giới từ "at" dùng với giờ cụ thể.', 2, 1.0, 1),
 (8,  'SINGLE_CHOICE', 'READING',    2, 5, N'What is Tom''s job most likely?',
      N'Tom works in a hospital. He helps sick people every day and sometimes works at night.', NULL,
      N'Làm ở bệnh viện, chăm sóc người bệnh -> y tá.', 2, 1.0, 1),
 -- Placement: mức 3 (Trung cấp)
 (9,  'SINGLE_CHOICE', 'GRAMMAR',    3, NULL, N'If it rains tomorrow, we ___ at home.', NULL, NULL, N'Câu điều kiện loại 1: If + hiện tại đơn, will + V.', 3, 1.5, 1),
 (10, 'SINGLE_CHOICE', 'GRAMMAR',    3, NULL, N'I have lived here ___ 2015.', NULL, NULL, N'"since" + mốc thời gian.', 3, 1.5, 1),
 (11, 'SINGLE_CHOICE', 'VOCABULARY', 3, NULL, N'Could you ___ me a favour?', NULL, NULL, N'Cụm cố định: do someone a favour.', 3, 1.5, 1),
 (12, 'SINGLE_CHOICE', 'READING',    3, 3, N'What concern do critics have?',
      N'Many companies now allow employees to work from home two or three days a week. Supporters say this saves commuting time, while critics worry about weaker teamwork.', NULL,
      N'Câu cuối: critics worry about weaker teamwork.', 3, 1.5, 1),
 -- Placement: mức 4 (Nâng cao)
 (13, 'SINGLE_CHOICE', 'GRAMMAR',    4, 3, N'The report ___ by the time the manager arrived.', NULL, NULL, N'Hành động hoàn tất trước một mốc quá khứ, dạng bị động -> quá khứ hoàn thành bị động.', 4, 2.0, 1),
 (14, 'SINGLE_CHOICE', 'VOCABULARY', 4, 3, N'The new policy had a significant ___ on sales.', NULL, NULL, N'"effect" (danh từ): have an effect on.', 4, 2.0, 1),
 (15, 'SINGLE_CHOICE', 'GRAMMAR',    4, NULL, N'Not only ___ late, but he also forgot the documents.', NULL, NULL, N'Đảo ngữ sau "Not only": trợ động từ + S + V.', 4, 2.0, 1),
 (16, 'SINGLE_CHOICE', 'READING',    4, 3, N'What happened after the pilot programme?',
      N'Despite initial scepticism, the pilot programme exceeded expectations, prompting the board to approve a nationwide rollout.', NULL,
      N'"approve a nationwide rollout" = mở rộng toàn quốc.', 4, 2.0, 1),
 -- Placement: mức 5 (Luyện thi)
 (17, 'SINGLE_CHOICE', 'GRAMMAR',    5, 3, N'All employees are required to submit their expense reports ___ Friday.', NULL, NULL, N'"by" = trước hoặc đúng hạn chót (TOEIC Part 5).', 5, 2.0, 1),
 (18, 'SINGLE_CHOICE', 'GRAMMAR',    5, NULL, N'Had I known about the traffic, I ___ earlier.', NULL, NULL, N'Đảo ngữ câu điều kiện loại 3: Had + S + V3, S + would have + V3.', 5, 2.0, 1),
 (19, 'SINGLE_CHOICE', 'VOCABULARY', 5, NULL, N'The data strongly ___ the theory; no contradictory evidence was found.', NULL, NULL, N'"corroborate" = củng cố, xác nhận.', 5, 2.0, 1),
 (20, 'SINGLE_CHOICE', 'READING',    5, 6, N'The writer''s view of urbanisation is best described as ___.',
      N'While urbanisation has lifted millions out of poverty, it has also placed unprecedented strain on infrastructure, particularly in rapidly expanding megacities.', NULL,
      N'Nêu cả mặt tích cực và tiêu cực -> quan điểm cân bằng.', 5, 2.0, 1),

 -- Quiz bài 8: đủ các dạng tương tác
 (21, 'FILL_BLANK',    'GRAMMAR',    2, 1, N'She usually {{1}} (drink) tea, but today she {{2}} (drink) coffee.', NULL,
      N'[["drinks"],["is drinking","''s drinking"]]', N'Thói quen -> hiện tại đơn; "today" + hành động tạm thời -> hiện tại tiếp diễn.', 2, 2.0, 1),
 (22, 'MATCHING',      'VOCABULARY', 2, 1, N'Match the adverbs of frequency with their meanings.', NULL, NULL, NULL, 2, 2.0, 1),
 (23, 'ORDERING',      'GRAMMAR',    2, 1, N'Put the words in the correct order.', NULL, NULL, N'S + am/is/are + V-ing + O + trạng từ thời gian.', 2, 1.0, 1),
 (24, 'SINGLE_CHOICE', 'GRAMMAR',    2, 1, N'Listen! The baby ___.', NULL, NULL, N'"Listen!" báo hiệu hành động đang xảy ra.', 2, 1.0, 1),
 -- Speaking & Writing
 (25, 'SPEAKING',      'SPEAKING',   1, 1, N'Read the sentence aloud.', NULL, N'Hello, my name is Lan. I am a student.', NULL, 1, 1.0, 1),
 (26, 'ESSAY',         'WRITING',    2, 1, N'Write 80-100 words about your last weekend. Use the past simple.', NULL, NULL, NULL, 2, 10.0, 1);
SET IDENTITY_INSERT Questions OFF;

INSERT INTO QuestionOptions (QuestionId, Content, IsCorrect, SortOrder) VALUES
 (1, N'am', 0, 1), (1, N'is', 1, 2), (1, N'are', 0, 3), (1, N'be', 0, 4),
 (2, N'Table', 0, 1), (2, N'Green', 1, 2), (2, N'Run', 0, 3), (2, N'Happy', 0, 4),
 (3, N'is', 0, 1), (3, N'are', 0, 2), (3, N'am', 1, 3), (3, N'be', 0, 4),
 (4, N'small', 1, 1), (4, N'tall', 0, 2), (4, N'long', 0, 3), (4, N'old', 0, 4),
 (5, N'plays', 0, 1), (5, N'play', 1, 2), (5, N'playing', 0, 3), (5, N'is play', 0, 4),
 (6, N'go', 0, 1), (6, N'goes', 0, 2), (6, N'went', 1, 3), (6, N'gone', 0, 4),
 (7, N'in', 0, 1), (7, N'on', 0, 2), (7, N'at', 1, 3), (7, N'by', 0, 4),
 (8, N'A teacher', 0, 1), (8, N'A nurse', 1, 2), (8, N'A chef', 0, 3), (8, N'A pilot', 0, 4),
 (9, N'stay', 0, 1), (9, N'will stay', 1, 2), (9, N'would stay', 0, 3), (9, N'stayed', 0, 4),
 (10, N'for', 0, 1), (10, N'since', 1, 2), (10, N'from', 0, 3), (10, N'during', 0, 4),
 (11, N'make', 0, 1), (11, N'do', 1, 2), (11, N'give', 0, 3), (11, N'take', 0, 4),
 (12, N'Longer commutes', 0, 1), (12, N'Weaker teamwork', 1, 2), (12, N'Higher salaries', 0, 3), (12, N'Fewer holidays', 0, 4),
 (13, N'had been finished', 1, 1), (13, N'has finished', 0, 2), (13, N'was finishing', 0, 3), (13, N'finishes', 0, 4),
 (14, N'affect', 0, 1), (14, N'effect', 1, 2), (14, N'effort', 0, 3), (14, N'afford', 0, 4),
 (15, N'he arrived', 0, 1), (15, N'did he arrive', 1, 2), (15, N'he did arrive', 0, 3), (15, N'arrived he', 0, 4),
 (16, N'It was cancelled.', 0, 1), (16, N'It was expanded nationwide.', 1, 2), (16, N'It was postponed.', 0, 3), (16, N'It was criticised by the board.', 0, 4),
 (17, N'until', 0, 1), (17, N'by', 1, 2), (17, N'on', 0, 3), (17, N'within', 0, 4),
 (18, N'would leave', 0, 1), (18, N'would have left', 1, 2), (18, N'had left', 0, 3), (18, N'will leave', 0, 4),
 (19, N'corroborates', 1, 1), (19, N'contradicts', 0, 2), (19, N'neglects', 0, 3), (19, N'diminishes', 0, 4),
 (20, N'entirely positive', 0, 1), (20, N'entirely negative', 0, 2), (20, N'balanced', 1, 3), (20, N'indifferent', 0, 4),
 (24, N'cries', 0, 1), (24, N'is crying', 1, 2), (24, N'cry', 0, 3), (24, N'cried', 0, 4);

-- MATCHING: Content = vế trái, MatchText = vế phải đúng
INSERT INTO QuestionOptions (QuestionId, Content, MatchText, IsCorrect, SortOrder) VALUES
 (22, N'always',    N'luôn luôn',       1, 1),
 (22, N'usually',   N'thường xuyên',    1, 2),
 (22, N'sometimes', N'thỉnh thoảng',    1, 3),
 (22, N'never',     N'không bao giờ',   1, 4);

-- ORDERING: SortOrder = thứ tự hiển thị (đã xáo), CorrectOrder = vị trí đúng
INSERT INTO QuestionOptions (QuestionId, Content, CorrectOrder, IsCorrect, SortOrder) VALUES
 (23, N'cooking', 3, 1, 1),
 (23, N'now',     5, 1, 2),
 (23, N'She',     1, 1, 3),
 (23, N'dinner',  4, 1, 4),
 (23, N'is',      2, 1, 5);

/* ---------- Tests ---------- */
SET IDENTITY_INSERT Tests ON;
INSERT INTO Tests (Id, Title, Description, TestType, LessonId, LevelId, DurationMinutes, PassingPercent, ShuffleQuestions, MaxAttempts, IsPublished, CreatedBy) VALUES
 (1, N'Bài kiểm tra xếp lớp', N'20 câu hỏi từ dễ đến khó, khoảng 20 phút, giúp xác định trình độ hiện tại của bạn.',
     'PLACEMENT', NULL, NULL, 20, NULL, 0, NULL, 1, 1),
 (2, N'Quiz: Present simple vs present continuous', N'Củng cố kiến thức bài học.',
     'LESSON_QUIZ', 8, 2, 10, 70.00, 1, NULL, 1, 1);
SET IDENTITY_INSERT Tests OFF;

INSERT INTO TestQuestions (TestId, QuestionId, SortOrder)
SELECT 1, Id, Id FROM Questions WHERE Id BETWEEN 1 AND 20;

INSERT INTO TestQuestions (TestId, QuestionId, SortOrder) VALUES
 (2, 21, 1), (2, 22, 2), (2, 23, 3), (2, 24, 4);

/* ---------- Từ vựng ---------- */
SET IDENTITY_INSERT Words ON;
INSERT INTO Words (Id, Headword, PartOfSpeech, PhoneticUk, PhoneticUs, MeaningVi, DefinitionEn, Example, ExampleTranslation, LevelId) VALUES
 (1,  N'hello',       'interjection', N'/həˈləʊ/',          N'/həˈloʊ/',         N'xin chào',            N'used when meeting or greeting someone', N'Hello, nice to meet you.', N'Xin chào, rất vui được gặp bạn.', 1),
 (2,  N'name',        'noun',         N'/neɪm/',            N'/neɪm/',           N'tên',                 N'the word that someone or something is called', N'What''s your name?', N'Bạn tên là gì?', 1),
 (3,  N'family',      'noun',         N'/ˈfæm.əl.i/',       N'/ˈfæm.əl.i/',      N'gia đình',            N'a group of people related to each other', N'I have a big family.', N'Tôi có một gia đình lớn.', 1),
 (4,  N'friend',      'noun',         N'/frend/',           N'/frend/',          N'bạn bè',              N'a person you know well and like', N'She is my best friend.', N'Cô ấy là bạn thân nhất của tôi.', 1),
 (5,  N'introduce',   'verb',         N'/ˌɪn.trəˈdʒuːs/',   N'/ˌɪn.trəˈduːs/',   N'giới thiệu',          N'to tell someone another person''s name when they first meet', N'Let me introduce myself.', N'Cho phép tôi tự giới thiệu.', 1),
 (6,  N'nice',        'adjective',    N'/naɪs/',            N'/naɪs/',           N'tốt, dễ chịu',        N'pleasant or enjoyable', N'Have a nice day!', N'Chúc một ngày tốt lành!', 1),
 (7,  N'airport',     'noun',         N'/ˈeə.pɔːt/',        N'/ˈer.pɔːrt/',      N'sân bay',             N'a place where planes land and take off', N'We arrived at the airport early.', N'Chúng tôi đến sân bay sớm.', 2),
 (8,  N'passport',    'noun',         N'/ˈpɑːs.pɔːt/',      N'/ˈpæs.pɔːrt/',     N'hộ chiếu',            N'an official document needed to travel abroad', N'Don''t forget your passport.', N'Đừng quên hộ chiếu nhé.', 2),
 (9,  N'luggage',     'noun',         N'/ˈlʌɡ.ɪdʒ/',        N'/ˈlʌɡ.ɪdʒ/',       N'hành lý',             N'the bags you take when travelling', N'My luggage is too heavy.', N'Hành lý của tôi quá nặng.', 2),
 (10, N'reservation', 'noun',         N'/ˌrez.əˈveɪ.ʃən/',  N'/ˌrez.ɚˈveɪ.ʃən/', N'sự đặt chỗ',          N'an arrangement to have a room, seat, etc. kept for you', N'I have a reservation for two nights.', N'Tôi đã đặt phòng hai đêm.', 2),
 (11, N'departure',   'noun',         N'/dɪˈpɑː.tʃər/',     N'/dɪˈpɑːr.tʃɚ/',    N'sự khởi hành',        N'the act of leaving a place', N'The departure time is 9 a.m.', N'Giờ khởi hành là 9 giờ sáng.', 2),
 (12, N'sightseeing', 'noun',         N'/ˈsaɪtˌsiː.ɪŋ/',    N'/ˈsaɪtˌsiː.ɪŋ/',   N'tham quan',           N'visiting interesting places as a tourist', N'We went sightseeing in Hoi An.', N'Chúng tôi đi tham quan Hội An.', 2),
 (13, N'deadline',    'noun',         N'/ˈded.laɪn/',       N'/ˈded.laɪn/',      N'hạn chót',            N'a time by which something must be done', N'The deadline for the report is Friday.', N'Hạn chót nộp báo cáo là thứ Sáu.', 5),
 (14, N'invoice',     'noun',         N'/ˈɪn.vɔɪs/',        N'/ˈɪn.vɔɪs/',       N'hóa đơn',             N'a list of goods or services with the amount to pay', N'Please send us the invoice.', N'Vui lòng gửi hóa đơn cho chúng tôi.', 5),
 (15, N'colleague',   'noun',         N'/ˈkɒl.iːɡ/',        N'/ˈkɑː.liːɡ/',      N'đồng nghiệp',         N'a person you work with', N'My colleagues are very helpful.', N'Đồng nghiệp của tôi rất nhiệt tình.', 5),
 (16, N'submit',      'verb',         N'/səbˈmɪt/',         N'/səbˈmɪt/',        N'nộp, đệ trình',       N'to give a document for consideration', N'Submit your application online.', N'Hãy nộp đơn trực tuyến.', 5),
 (17, N'negotiate',   'verb',         N'/nəˈɡəʊ.ʃi.eɪt/',   N'/nəˈɡoʊ.ʃi.eɪt/',  N'đàm phán',            N'to discuss to reach an agreement', N'We negotiated a better price.', N'Chúng tôi đã đàm phán được giá tốt hơn.', 5),
 (18, N'revenue',     'noun',         N'/ˈrev.ən.juː/',     N'/ˈrev.ə.nuː/',     N'doanh thu',           N'the income a company receives', N'Revenue rose by 10% this year.', N'Doanh thu tăng 10% trong năm nay.', 5),
 (19, N'postpone',    'verb',         N'/pəʊstˈpəʊn/',      N'/poʊstˈpoʊn/',     N'hoãn lại',            N'to arrange for an event to happen later', N'The meeting was postponed until Monday.', N'Cuộc họp được hoãn đến thứ Hai.', 5),
 (20, N'agenda',      'noun',         N'/əˈdʒen.də/',       N'/əˈdʒen.də/',      N'chương trình nghị sự',N'a list of things to discuss at a meeting', N'What''s on the agenda today?', N'Hôm nay họp những nội dung gì?', 5);
SET IDENTITY_INSERT Words OFF;

SET IDENTITY_INSERT VocabularySets ON;
INSERT INTO VocabularySets (Id, Name, Slug, Description, LevelId, TopicId, OwnerUserId, IsPublished) VALUES
 (1, N'Chào hỏi & giới thiệu', 'greetings-introductions', N'Từ vựng cơ bản khi gặp gỡ người mới.', 1, 1, NULL, 1),
 (2, N'Du lịch',               'travel-essentials',       N'Từ vựng cần thiết ở sân bay và khách sạn.', 2, 2, NULL, 1),
 (3, N'TOEIC - Văn phòng',     'toeic-office',            N'Từ vựng thường gặp trong TOEIC chủ đề công sở.', 5, 3, NULL, 1);
SET IDENTITY_INSERT VocabularySets OFF;

INSERT INTO VocabularySetWords (SetId, WordId, SortOrder)
SELECT CASE WHEN Id <= 6 THEN 1 WHEN Id <= 12 THEN 2 ELSE 3 END, Id, Id
FROM Words WHERE Id BETWEEN 1 AND 20;

/* ---------- Dữ liệu demo cho học viên mẫu (để dashboard có số liệu) ---------- */
DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

INSERT INTO UserWords (UserId, WordId, Source, Status, EaseFactor, IntervalDays, Repetitions, DueAt, LastReviewedAt) VALUES
 (2, 1, 'VOCAB_SET', 'MASTERED', 2.70, 25, 5, DATEADD(DAY, 20, SYSUTCDATETIME()), DATEADD(DAY, -5, SYSUTCDATETIME())),
 (2, 2, 'VOCAB_SET', 'REVIEW',   2.50,  6, 2, DATEADD(DAY,  2, SYSUTCDATETIME()), DATEADD(DAY, -4, SYSUTCDATETIME())),
 (2, 3, 'VOCAB_SET', 'LEARNING', 2.36,  1, 1, SYSUTCDATETIME(),                   DATEADD(DAY, -1, SYSUTCDATETIME())),
 (2, 7, 'VOCAB_SET', 'NEW',      2.50,  0, 0, SYSUTCDATETIME(),                   NULL),
 (2, 8, 'VOCAB_SET', 'NEW',      2.50,  0, 0, SYSUTCDATETIME(),                   NULL),
 (2, 9, 'READING_LOOKUP', 'NEW', 2.50,  0, 0, SYSUTCDATETIME(),                   NULL);

INSERT INTO LessonProgresses (UserId, LessonId, Status, ProgressPercent, BestScore, TimeSpentSeconds, StartedAt, CompletedAt) VALUES
 (2, 5, 'COMPLETED',   100, 90.00, 720, DATEADD(DAY, -3, SYSUTCDATETIME()), DATEADD(DAY, -3, SYSUTCDATETIME())),
 (2, 6, 'IN_PROGRESS',  40, NULL,  300, DATEADD(DAY, -1, SYSUTCDATETIME()), NULL);

INSERT INTO DailyActivities (UserId, ActivityDate, StudySeconds, LessonsCompleted, WordsLearned, WordsReviewed, QuizzesTaken, TotalQuizScore, GoalReached) VALUES
 (2, DATEADD(DAY, -6, @today), 1500, 1, 3, 5, 1, 80, 1),
 (2, DATEADD(DAY, -5, @today),  900, 0, 2, 8, 0,  0, 0),
 (2, DATEADD(DAY, -3, @today), 1800, 1, 4, 6, 1, 90, 1),
 (2, DATEADD(DAY, -2, @today), 1260, 0, 2, 10, 1, 70, 1),
 (2, DATEADD(DAY, -1, @today), 1320, 1, 3, 7, 0,  0, 1);

INSERT INTO UserStreaks (UserId, CurrentStreak, LongestStreak, LastActivityDate) VALUES
 (2, 3, 3, DATEADD(DAY, -1, @today));

SET IDENTITY_INSERT LearningPaths ON;
INSERT INTO LearningPaths (Id, UserId, Name, GoalType, LevelId, Source, Status, DailyMinutes) VALUES
 (1, 2, N'Giao tiếp cơ bản trong 30 ngày', 'COMMUNICATION', 2, 'GOAL', 'ACTIVE', 20);
SET IDENTITY_INSERT LearningPaths OFF;

INSERT INTO LearningPathItems (PathId, LessonId, SortOrder, Status, CompletedAt) VALUES
 (1, 5, 1, 'COMPLETED',   DATEADD(DAY, -3, SYSUTCDATETIME())),
 (1, 6, 2, 'IN_PROGRESS', NULL),
 (1, 8, 3, 'LOCKED',      NULL),
 (1, 7, 4, 'LOCKED',      NULL);

INSERT INTO Notifications (UserId, Type, Title, Message, Link) VALUES
 (2, 'SYSTEM',     N'Chào mừng đến EnglishHub!', N'Hãy làm bài kiểm tra xếp lớp để nhận lộ trình phù hợp.', N'/app/placement'),
 (2, 'REVIEW_DUE', N'Bạn có 4 từ cần ôn hôm nay', N'Ôn tập đúng lúc giúp ghi nhớ lâu hơn.',              N'/app/vocabulary/review');
