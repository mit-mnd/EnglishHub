# EnglishHub – ERD (EnglishLearningDB)

30 bảng, chia 8 nhóm. Mọi bảng đều có `CreatedAt`, `UpdatedAt` (DATETIME2, UTC) – lược bớt trong sơ đồ cho gọn.

## 1. Định danh, nội dung & đánh giá

```mermaid
erDiagram
    Roles ||--o{ UserRoles : ""
    Users ||--o{ UserRoles : ""
    Levels |o--o{ Users : "CurrentLevel"
    Users ||--o{ RefreshTokens : ""
    Users ||--o{ PasswordResetTokens : ""

    Levels ||--o{ Courses : ""
    Courses ||--o{ Lessons : "chứa"
    Topics |o--o{ Lessons : ""
    Levels ||--o{ Lessons : ""
    Lessons ||--o{ LessonTranscriptSegments : "phụ đề"

    Levels |o--o{ Questions : ""
    Topics |o--o{ Questions : ""
    Questions ||--o{ QuestionOptions : ""
    Lessons |o--o{ Tests : "quiz của bài"
    Tests ||--o{ TestQuestions : ""
    Questions ||--o{ TestQuestions : ""
    Users ||--o{ TestAttempts : ""
    Tests ||--o{ TestAttempts : ""
    Levels |o--o{ TestAttempts : "ResultLevel"
    TestAttempts ||--o{ AttemptAnswers : ""
    Questions ||--o{ AttemptAnswers : ""

    Users {
        bigint Id PK
        varchar Email UK
        varchar PasswordHash "BCrypt"
        nvarchar FullName
        varchar Status "ACTIVE|LOCKED|DISABLED"
        int CurrentLevelId FK
        varchar LearningGoal "GENERAL|COMMUNICATION|TOEIC|IELTS"
        int DailyGoalMinutes
        time ReminderTime
    }
    Levels {
        int Id PK
        varchar Code UK "FOUNDATION..EXAM_PREP"
        nvarchar Name
        decimal PlacementMinPercent
        decimal PlacementMaxPercent
    }
    Courses {
        bigint Id PK
        nvarchar Title
        varchar Slug UK
        int LevelId FK
        varchar GoalType
        bit IsPublished
    }
    Lessons {
        bigint Id PK
        bigint CourseId FK
        varchar Skill "LISTENING|SPEAKING|READING|WRITING|VOCABULARY|GRAMMAR"
        nvarchar Content
        varchar MediaType "NONE|AUDIO|VIDEO"
        nvarchar MediaUrl
        int SortOrder
    }
    LessonTranscriptSegments {
        bigint Id PK
        bigint LessonId FK
        int StartMs
        int EndMs
        nvarchar Text
        nvarchar Translation
    }
    Questions {
        bigint Id PK
        varchar QuestionType "SINGLE_CHOICE|MULTIPLE_CHOICE|FILL_BLANK|MATCHING|ORDERING|SPEAKING|ESSAY"
        varchar Skill
        nvarchar Content
        nvarchar Passage
        nvarchar AnswerKey "JSON cho FILL_BLANK"
        tinyint Difficulty "1-5"
        decimal DefaultPoints
    }
    QuestionOptions {
        bigint Id PK
        bigint QuestionId FK
        nvarchar Content
        bit IsCorrect
        nvarchar MatchText "MATCHING"
        int CorrectOrder "ORDERING"
    }
    Tests {
        bigint Id PK
        varchar TestType "PLACEMENT|LESSON_QUIZ|PRACTICE|MOCK_EXAM"
        bigint LessonId FK
        int DurationMinutes
        decimal PassingPercent
    }
    TestAttempts {
        bigint Id PK
        bigint UserId FK
        bigint TestId FK
        varchar Status "IN_PROGRESS|SUBMITTED|EXPIRED"
        decimal Percentage
        int ResultLevelId FK
        nvarchar SkillScoresJson
    }
    AttemptAnswers {
        bigint Id PK
        bigint AttemptId FK
        bigint QuestionId FK
        varchar SelectedOptionIds
        nvarchar AnswerText
        bit IsCorrect
        decimal Score
    }
```

## 2. Lộ trình, kỹ năng, từ vựng, tiến độ, thông báo

```mermaid
erDiagram
    Users ||--o{ LearningPaths : ""
    Levels ||--o{ LearningPaths : ""
    TestAttempts |o--o{ LearningPaths : "sinh từ placement"
    LearningPaths ||--o{ LearningPathItems : ""
    Lessons ||--o{ LearningPathItems : ""

    Users ||--o{ SpeakingSubmissions : ""
    Users ||--o{ WritingSubmissions : ""
    Lessons |o--o{ SpeakingSubmissions : ""
    Lessons |o--o{ WritingSubmissions : ""

    Words ||--o{ VocabularySetWords : ""
    VocabularySets ||--o{ VocabularySetWords : ""
    Users |o--o{ VocabularySets : "bộ từ cá nhân"
    Users ||--o{ UserWords : "sổ từ"
    Words ||--o{ UserWords : ""
    UserWords ||--o{ FlashcardReviews : "lịch sử ôn"

    Users ||--o{ LessonProgresses : ""
    Lessons ||--o{ LessonProgresses : ""
    Users ||--o{ StudySessions : ""
    Users ||--o{ DailyActivities : "1 dòng/ngày"
    Users ||--o| UserStreaks : ""
    Users ||--o{ Notifications : ""

    LearningPaths {
        bigint Id PK
        bigint UserId FK "UNIQUE khi Status=ACTIVE"
        varchar GoalType
        int LevelId FK
        varchar Source "PLACEMENT|GOAL|MANUAL"
        varchar Status "ACTIVE|COMPLETED|ARCHIVED"
    }
    LearningPathItems {
        bigint Id PK
        bigint PathId FK
        bigint LessonId FK
        int SortOrder
        varchar Status "LOCKED|AVAILABLE|IN_PROGRESS|COMPLETED|SKIPPED"
        bit IsCustom
    }
    SpeakingSubmissions {
        bigint Id PK
        nvarchar ReferenceText
        nvarchar AudioUrl
        decimal PronunciationScore
        nvarchar DetailJson
        varchar Provider
    }
    WritingSubmissions {
        bigint Id PK
        nvarchar Content
        decimal Score
        nvarchar FeedbackJson
        varchar Provider
    }
    Words {
        bigint Id PK
        nvarchar Headword "UK(Headword, PartOfSpeech)"
        varchar PartOfSpeech
        nvarchar PhoneticUk
        nvarchar PhoneticUs
        nvarchar MeaningVi
        nvarchar Example
    }
    UserWords {
        bigint Id PK
        bigint UserId FK "UK(UserId, WordId)"
        bigint WordId FK
        varchar Status "NEW|LEARNING|REVIEW|MASTERED|SUSPENDED"
        decimal EaseFactor ">= 1.3"
        int IntervalDays
        int Repetitions
        datetime2 DueAt
    }
    FlashcardReviews {
        bigint Id PK
        bigint UserWordId FK
        tinyint Quality "0-5"
        decimal NewEaseFactor
        int NewIntervalDays
    }
    DailyActivities {
        bigint Id PK
        bigint UserId FK "UK(UserId, ActivityDate)"
        date ActivityDate
        int StudySeconds
        int WordsLearned
        int QuizzesTaken
        decimal TotalQuizScore
    }
    UserStreaks {
        bigint UserId PK
        int CurrentStreak
        int LongestStreak
        date LastActivityDate
    }
    Notifications {
        bigint Id PK
        bigint UserId FK
        varchar Type
        nvarchar Title
        bit IsRead
    }
```

## 3. Ghi chú thiết kế

| Vấn đề | Giải pháp |
|---|---|
| Một bảng câu hỏi cho nhiều dạng bài | `QuestionType` + cột phụ (`MatchText`, `CorrectOrder`, `AnswerKey` JSON) thay vì mỗi dạng một bảng – admin CRUD một chỗ, test tái dùng câu hỏi qua `TestQuestions`. |
| Placement Test và Quiz | Dùng chung `Tests`/`TestAttempts`, phân biệt bằng `TestType`; chỉ placement ghi `ResultLevelId`. |
| Chỉ 1 lộ trình đang chạy | Filtered unique index `UX_LearningPaths_User_Active ... WHERE Status = 'ACTIVE'`. |
| Truy vấn thẻ đến hạn ôn (SRS) | Filtered index `IX_UserWords_User_Due (UserId, DueAt) WHERE Status <> 'SUSPENDED'`. |
| Dashboard nhanh | Bảng tổng hợp `DailyActivities` (1 dòng/user/ngày) thay vì `SUM` trên log mỗi lần mở dashboard. |
| Lỗi "multiple cascade paths" của SQL Server | Chỉ cascade theo nhánh sở hữu chính (Users → dữ liệu cá nhân, Courses → Lessons → phụ đề). FK phụ (`FlashcardReviews.UserId`, `LearningPaths.PlacementAttemptId`, `UserWords.WordId`…) để NO ACTION. Đã kiểm tra: xóa một user xóa sạch dữ liệu liên quan. |
| Không xóa nhầm nội dung đang dùng | `LearningPathItems.LessonId`, `TestQuestions.QuestionId`, `AttemptAnswers.QuestionId` là NO ACTION → không thể xóa bài/câu hỏi đã nằm trong lộ trình hay bài làm; admin dùng `IsPublished`/`IsActive = 0` để ẩn. |
| Token bảo mật | `RefreshTokens`, `PasswordResetTokens` chỉ lưu SHA-256 hash. |
| Enum | `VARCHAR` + `CHECK` (map `@Enumerated(EnumType.STRING)` trong JPA). |
