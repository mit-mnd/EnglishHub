# Dữ liệu nội dung (JSON)

Thư mục này là "cơ sở dữ liệu" **nội dung học** của EnglishHub - chỉ đọc, được đóng gói vào ứng dụng khi build.
Dữ liệu **của người học** (hồ sơ, tiến độ, sổ từ...) không nằm ở đây mà lưu trong localStorage của trình duyệt
(xem `src/core/storage/db.js`), có thể xuất/nhập bằng file sao lưu ở trang *Hồ sơ & dữ liệu*.

Sau khi thêm/sửa file, chạy kiểm tra:

```bash
npm run validate:data
```

| File | Nội dung | Số lượng |
|---|---|---|
| `levels.json` | 5 trình độ + ngưỡng % xếp lớp | 5 |
| `courses.json` | Khóa học, bài học lồng bên trong (kèm phụ đề có mốc thời gian) | 6 khóa · 14 bài |
| `tests/placement.json` | Bài kiểm tra xếp lớp | 20 câu |
| `tests/lesson-exercises.json` | Bài tập / quiz gắn bài học (7 dạng câu hỏi) | 7 bài · 19 câu |
| `vocabulary/*words.json` | Kho từ: `words` (gốc), `everyday-words` (thông dụng), `toeic-words`, `toeic2-words` (TOEIC), `intermediate-words` (B1-B2), `phrases-words` (cụm từ), `basic-a/b`, `intermediate-new`, `toeic-new`, `advanced` (chủ đề mới), `*-plus`, `*-plus-a/b` (từ bổ sung cho bộ có sẵn) | 4.313 từ |
| `vocabulary/*sets.json` | Bộ từ theo chủ đề | 97 bộ |
| `sentences/patterns.json` | Mẫu câu theo tình huống | 22 chủ đề · 272 mẫu |
| `toeic/partN.json`, `partN-b.json` | Luyện thi TOEIC Listening (Part 2-4) & Reading (Part 5-7); các file cùng Part được gộp tự động | 788 câu (P2 105 · P3 87 · P4 69 · P5 270 · P6 104 · P7 153) |

Mọi file `*-words.json` / `*-sets.json` trong `vocabulary/` được tự nạp - thêm bộ từ mới chỉ cần thêm file.

**Mở rộng bộ có sẵn mà không sửa file gốc:** thêm vào bất kỳ `*-sets.json` một mục chỉ có `id` và `wordIds` (không có `name`), ví dụ `{ "id": 201, "wordIds": [5001, 5002] }`; các từ được nối vào bộ cùng `id`. Test dữ liệu kiểm tra mục mở rộng trỏ đúng bộ gốc và không có từ lặp.
Nội dung TOEIC, từ vựng mới và mẫu câu do EnglishHub tự biên soạn theo định dạng đề thi, **không** sao chép đề của ETS.

## Quy ước chung

- `id` là số nguyên, không trùng trong cùng loại. Dải id: từ vựng gốc 1-999, TOEIC 1001+, thông dụng 2001+;
  bộ từ TOEIC 101+, thông dụng 201+. Từ tra được từ từ điển ngoài (lưu trên máy người học) dùng id ≥ 1 000 000.
- `levelId` tham chiếu `levels.json` (1 Mất gốc · 2 Cơ bản · 3 Trung cấp · 4 Nâng cao · 5 Luyện thi).
- Kỹ năng (`skill`): `LISTENING | SPEAKING | READING | WRITING | VOCABULARY | GRAMMAR`.
- Đường dẫn media bắt đầu bằng `/media/...` (file trong `public/media`) hoặc `https://`.

## Khóa học & bài học (`courses.json`)

```jsonc
{
  "id": 1, "slug": "english-foundation", "title": "...", "description": "...",
  "levelId": 1, "goalType": "GENERAL",            // GENERAL | COMMUNICATION | TOEIC | IELTS
  "sortOrder": 1,
  "lessons": [{
    "id": 1, "slug": "...", "title": "...", "skill": "LISTENING", "levelId": 1,
    "summary": "...", "content": "<p>HTML bài đọc / lý thuyết</p>",
    "mediaType": "AUDIO",                         // NONE | AUDIO | VIDEO
    "mediaUrl": "/media/lessons/greetings.wav", "durationSeconds": 23, "estimatedMinutes": 10,
    "transcript": [{ "startMs": 0, "endMs": 4615, "text": "Hi! I'm Anna.", "translation": "Chào! Mình là Anna." }]
  }]
}
```

## Câu hỏi (`tests/*.json`)

```jsonc
{ "id": 2, "title": "...", "testType": "LESSON_QUIZ", "lessonId": 8, "passingPercent": 60, "questions": [ ... ] }
```

| `type` | Đáp án |
|---|---|
| `SINGLE_CHOICE` / `MULTIPLE_CHOICE` | `options[].correct = true` |
| `FILL_BLANK` | ô trống `{{1}}`, `{{2}}` trong `content`; `answerKey` = JSON `[["went"],["has been","'s been"]]` |
| `MATCHING` | `options[].content` (vế trái) + `options[].matchText` (vế phải) |
| `ORDERING` | `options[].correctOrder` = 1..n |
| `SPEAKING` | `answerKey` = câu mẫu cần đọc |
| `ESSAY` | `answerKey` = `{"minWords":80,"maxWords":120}` (tùy chọn) |

Mỗi câu có `id`, `skill`, `content`, `points`, tùy chọn `passage`, `mediaUrl`, `explanation`.

## Từ vựng (`vocabulary/*words.json`, `*sets.json`)

```jsonc
{ "id": 1001, "headword": "stationery", "partOfSpeech": "noun", "phoneticUk": "/ˈsteɪʃənri/", "phoneticUs": "/ˈsteɪʃəneri/",
  "meaningVi": "văn phòng phẩm", "definitionEn": "...", "example": "...", "exampleTranslation": "...",
  "levelId": 4, "topics": ["office"] }
{ "id": 101, "slug": "toeic-office-2", "name": "TOEIC - Văn phòng & hành chính", "description": "...", "levelId": 5, "wordIds": [1001, 1002] }
```

`partOfSpeech`: `noun, verb, adjective, adverb, pronoun, preposition, conjunction, interjection, phrase, other`.
Cặp (headword, partOfSpeech) không được trùng giữa các file.

## Mẫu câu (`sentences/patterns.json`)

```jsonc
{ "id": 1, "slug": "greetings", "name": "Chào hỏi & làm quen", "description": "...", "category": "daily", // daily | work | toeic
  "levelId": 1,
  "patterns": [{ "id": 101, "pattern": "How's it going?", "meaning": "Dạo này thế nào?", "example": "...",
                 "exampleTranslation": "...", "note": "mẹo dùng (tùy chọn)", "formality": "informal" }] }  // formal | neutral | informal
```

## TOEIC (`toeic/partN.json`)

Mỗi file: `{ "part", "title", "description", "items": [...] }`. Đáp án là chỉ số (0 = A).

| Part | Một item |
|---|---|
| 2 | `{ id, question, questionVoice: "male"/"female", responses: [3], answer, type, explanation, translation }` |
| 3, 4 | `{ id, title, speakers: [{name, voice}], script: [{speaker, text}], graphic?: {title, text}, questions: [{id, question, options: [4], answer, explanation}], translation }` |
| 5 | `{ id, sentence (ô trống là "-------"), options: [4], answer, category, difficulty, translation, explanation }` |
| 6 | `{ id, type, title, text (ô trống {{1}}..{{4}}), questions: [{blank, options: [4], answer, explanation}], translation }` |
| 7 | `{ id, format: single/double/triple, documents: [{type, title, text}], questions: [{id, question, options: [4], answer, type, explanation}], translation }` |

Phần Nghe không có file ghi âm: ứng dụng đọc `script` bằng giọng tổng hợp của trình duyệt, chọn giọng theo `voice`.
Viết số và ký hiệu thành chữ (`fifteen percent` thay cho `15%`) để giọng đọc tự nhiên.
