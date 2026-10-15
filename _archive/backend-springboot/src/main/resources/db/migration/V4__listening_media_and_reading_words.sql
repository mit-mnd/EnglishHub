/* =====================================================================
   EnglishHub - V4: Audio demo cho bài Listening + từ vựng cho bài Reading
   - Audio tạo bằng scripts/generate-demo-audio.ps1 (giọng TTS Windows), phục vụ tại /media/lessons/<tên>.wav
   - LƯU Ý: không viết chuỗi "/" + "*" bên trong block comment - T-SQL hỗ trợ comment lồng nhau,
     chuỗi đó mở một comment mới chưa đóng và biến cả file thành comment (Flyway vẫn báo thành công).
   - Mốc thời gian phụ đề khớp với file audio đã sinh (chạy lại script => phải cập nhật lại các mốc này)
   ===================================================================== */

-- Bài 1: Greetings and introductions
UPDATE Lessons SET MediaType = 'AUDIO', MediaUrl = N'/media/lessons/greetings.wav', DurationSeconds = 23,
                   UpdatedAt = SYSUTCDATETIME()
WHERE Id = 1;

DELETE FROM LessonTranscriptSegments WHERE LessonId = 1;
INSERT INTO LessonTranscriptSegments (LessonId, SortOrder, StartMs, EndMs, Text, Translation) VALUES
 (1, 1,     0,  4615, N'Hi! I''m Anna. What''s your name?',           N'Chào! Mình là Anna. Bạn tên gì?'),
 (1, 2,  4965,  9190, N'Hello, Anna. My name is Nam.',                N'Chào Anna. Mình tên là Nam.'),
 (1, 3,  9540, 14355, N'Nice to meet you, Nam. Where are you from?',  N'Rất vui được gặp bạn, Nam. Bạn đến từ đâu?'),
 (1, 4, 14705, 18270, N'I''m from Vietnam. And you?',                 N'Mình đến từ Việt Nam. Còn bạn?'),
 (1, 5, 18620, 22730, N'I''m from Canada. Nice to meet you too.',     N'Mình đến từ Canada. Mình cũng rất vui được gặp bạn.');

-- Bài 5: At the coffee shop
UPDATE Lessons SET MediaType = 'AUDIO', MediaUrl = N'/media/lessons/coffee-shop.wav', DurationSeconds = 23,
                   UpdatedAt = SYSUTCDATETIME()
WHERE Id = 5;

DELETE FROM LessonTranscriptSegments WHERE LessonId = 5;
INSERT INTO LessonTranscriptSegments (LessonId, SortOrder, StartMs, EndMs, Text, Translation) VALUES
 (5, 1,     0,  3865, N'Good morning! What can I get for you?',   N'Chào buổi sáng! Tôi có thể lấy gì cho bạn?'),
 (5, 2,  4215,  7915, N'Can I have a large iced latte, please?',  N'Cho tôi một ly latte đá cỡ lớn nhé.'),
 (5, 3,  8265, 12240, N'Sure. Would you like anything to eat?',   N'Được ạ. Bạn có muốn ăn gì không?'),
 (5, 4, 12590, 16510, N'Yes, a chocolate muffin, please.',        N'Có, một cái bánh muffin sô-cô-la.'),
 (5, 5, 16860, 19365, N'That''s six dollars fifty.',              N'Tổng cộng sáu đô năm mươi xu.'),
 (5, 6, 19715, 22935, N'Here you are. Thank you!',                N'Gửi bạn. Cảm ơn nhé!');

-- Bài 9: A job interview (chuyển từ video placeholder sang audio thật)
UPDATE Lessons SET MediaType = 'AUDIO', MediaUrl = N'/media/lessons/job-interview.wav', DurationSeconds = 44,
                   Summary = N'Nghe đoạn phỏng vấn xin việc và học cách trả lời các câu hỏi thường gặp.',
                   UpdatedAt = SYSUTCDATETIME()
WHERE Id = 9;

DELETE FROM LessonTranscriptSegments WHERE LessonId = 9;
INSERT INTO LessonTranscriptSegments (LessonId, SortOrder, StartMs, EndMs, Text, Translation) VALUES
 (9, 1,     0,  4510, N'Good afternoon. Thank you for coming in today.', N'Chào buổi chiều. Cảm ơn bạn đã đến hôm nay.'),
 (9, 2,  4860,  9370, N'Thank you for having me. I''m glad to be here.', N'Cảm ơn anh đã mời tôi. Tôi rất vui được có mặt ở đây.'),
 (9, 3,  9720, 12960, N'Could you tell me a little about yourself?',     N'Bạn có thể giới thiệu đôi chút về bản thân không?'),
 (9, 4, 13310, 21435, N'Sure. I graduated in marketing two years ago, and since then I have worked for a small tech company.',
                      N'Vâng. Tôi tốt nghiệp ngành marketing hai năm trước và từ đó làm việc cho một công ty công nghệ nhỏ.'),
 (9, 5, 21785, 24490, N'Why do you want to work for us?',                N'Vì sao bạn muốn làm việc cho chúng tôi?'),
 (9, 6, 24840, 31115, N'I admire your products, and I want to grow in a larger, international team.',
                      N'Tôi ngưỡng mộ các sản phẩm của công ty và muốn phát triển trong một đội ngũ lớn hơn, mang tính quốc tế.'),
 (9, 7, 31465, 34055, N'What is your greatest strength?',                N'Điểm mạnh lớn nhất của bạn là gì?'),
 (9, 8, 34405, 38820, N'I am a good listener, and I always meet my deadlines.', N'Tôi là người biết lắng nghe và luôn hoàn thành đúng hạn.'),
 (9, 9, 39170, 43835, N'Great. We will contact you by the end of the week.', N'Tuyệt. Chúng tôi sẽ liên hệ với bạn trước cuối tuần.');

-- Từ vựng xuất hiện trong các bài đọc (tra từ trên màn hình không cần gọi dịch vụ ngoài)
INSERT INTO Words (Headword, PartOfSpeech, PhoneticUk, PhoneticUs, MeaningVi, DefinitionEn, Example, ExampleTranslation, LevelId) VALUES
 (N'souvenir',     'noun',      N'/ˌsuː.vənˈɪər/',     N'/ˌsuː.vəˈnɪr/',      N'quà lưu niệm',        N'something you buy to remember a place', N'She bought some souvenirs at the market.', N'Cô ấy mua vài món quà lưu niệm ở chợ.', 2),
 (N'museum',       'noun',      N'/mjuːˈziː.əm/',      N'/mjuːˈziː.əm/',      N'bảo tàng',            N'a building where objects of historical or artistic interest are shown', N'The museum was crowded.', N'Bảo tàng rất đông người.', 2),
 (N'crowded',      'adjective', N'/ˈkraʊ.dɪd/',        N'/ˈkraʊ.dɪd/',        N'đông đúc',            N'full of people', N'The bus was very crowded.', N'Xe buýt rất đông.', 2),
 (N'exhibition',   'noun',      N'/ˌek.sɪˈbɪʃ.ən/',    N'/ˌek.səˈbɪʃ.ən/',    N'triển lãm',           N'a show of art or other objects for the public', N'The exhibition was interesting.', N'Buổi triển lãm rất thú vị.', 2),
 (N'commute',      'verb',      N'/kəˈmjuːt/',         N'/kəˈmjuːt/',         N'đi lại (giữa nhà và nơi làm)', N'to travel regularly between home and work', N'I commute to work by bus.', N'Tôi đi làm bằng xe buýt.', 3),
 (N'productive',   'adjective', N'/prəˈdʌk.tɪv/',      N'/prəˈdʌk.tɪv/',      N'năng suất, hiệu quả', N'achieving a lot', N'I feel more productive at home.', N'Tôi thấy làm việc ở nhà hiệu quả hơn.', 3),
 (N'hybrid',       'adjective', N'/ˈhaɪ.brɪd/',        N'/ˈhaɪ.brɪd/',        N'kết hợp, lai',        N'mixing two different things', N'Many companies choose a hybrid model.', N'Nhiều công ty chọn mô hình kết hợp.', 3),
 (N'employee',     'noun',      N'/ɪmˈplɔɪ.iː/',       N'/ɪmˈplɔɪ.iː/',       N'nhân viên',           N'a person who is paid to work for someone', N'The company has 200 employees.', N'Công ty có 200 nhân viên.', 3),
 (N'vulnerable',   'adjective', N'/ˈvʌl.nər.ə.bəl/',   N'/ˈvʌl.nɚ.ə.bəl/',    N'dễ bị tổn thương',    N'easily hurt or damaged', N'Coastal cities are vulnerable to floods.', N'Các thành phố ven biển dễ bị lũ lụt.', 4),
 (N'wetland',      'noun',      N'/ˈwet.lənd/',        N'/ˈwet.lænd/',        N'vùng đất ngập nước',  N'an area of land covered with water', N'Restoring wetlands helps reduce flooding.', N'Phục hồi đất ngập nước giúp giảm lũ.', 4),
 (N'biodiversity', 'noun',      N'/ˌbaɪ.əʊ.daɪˈvɜː.sə.ti/', N'/ˌbaɪ.oʊ.dɪˈvɝː.sə.t̬i/', N'đa dạng sinh học', N'the variety of plants and animals in an area', N'Wetlands improve biodiversity.', N'Đất ngập nước làm tăng đa dạng sinh học.', 4),
 (N'beach',        'noun',      N'/biːtʃ/',            N'/biːtʃ/',            N'bãi biển',            N'an area of sand next to the sea', N'We love going to the beach.', N'Chúng tôi thích đi biển.', 1),
 (N'doctor',       'noun',      N'/ˈdɒk.tər/',         N'/ˈdɑːk.tɚ/',         N'bác sĩ',              N'a person who treats sick people', N'My father is a doctor.', N'Bố tôi là bác sĩ.', 1),
 (N'teacher',      'noun',      N'/ˈtiː.tʃər/',        N'/ˈtiː.tʃɚ/',         N'giáo viên',           N'a person who teaches', N'My mother is a teacher.', N'Mẹ tôi là giáo viên.', 1);
