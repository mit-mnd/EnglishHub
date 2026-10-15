/* =====================================================================
   EnglishHub - V1: Khởi tạo schema
   Database : EnglishLearningDB (SQL Server 2017+)
   Quy ước  : - Bảng PascalCase số nhiều, mỗi bảng có CreatedAt/UpdatedAt (UTC)
              - Mã enum lưu VARCHAR + CHECK; văn bản người dùng/tiếng Việt dùng NVARCHAR
              - UpdatedAt do ứng dụng (JPA auditing) cập nhật, không dùng trigger
   ===================================================================== */

/* =============================== 1. ĐỊNH DANH ============================= */

CREATE TABLE Roles (
    Id          INT IDENTITY(1,1)  NOT NULL,
    Code        VARCHAR(30)        NOT NULL,
    Name        NVARCHAR(100)      NOT NULL,
    CreatedAt   DATETIME2(3)       NOT NULL CONSTRAINT DF_Roles_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt   DATETIME2(3)       NOT NULL CONSTRAINT DF_Roles_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Roles PRIMARY KEY (Id),
    CONSTRAINT UQ_Roles_Code UNIQUE (Code)
);

CREATE TABLE Levels (
    Id                   INT IDENTITY(1,1) NOT NULL,
    Code                 VARCHAR(30)       NOT NULL,
    Name                 NVARCHAR(100)     NOT NULL,
    Description          NVARCHAR(500)     NULL,
    CefrRange            VARCHAR(20)       NULL,           -- vd: 'A1-A2'
    PlacementMinPercent  DECIMAL(5,2)      NOT NULL,
    PlacementMaxPercent  DECIMAL(5,2)      NOT NULL,
    SortOrder            INT               NOT NULL,
    CreatedAt            DATETIME2(3)      NOT NULL CONSTRAINT DF_Levels_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt            DATETIME2(3)      NOT NULL CONSTRAINT DF_Levels_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Levels PRIMARY KEY (Id),
    CONSTRAINT UQ_Levels_Code UNIQUE (Code),
    CONSTRAINT UQ_Levels_SortOrder UNIQUE (SortOrder),
    CONSTRAINT CK_Levels_PlacementRange CHECK (
        PlacementMinPercent >= 0 AND PlacementMaxPercent <= 100
        AND PlacementMinPercent <= PlacementMaxPercent)
);

CREATE TABLE Users (
    Id                        BIGINT IDENTITY(1,1) NOT NULL,
    Email                     VARCHAR(255)   NOT NULL,
    PasswordHash              VARCHAR(100)   NOT NULL,
    FullName                  NVARCHAR(150)  NOT NULL,
    AvatarUrl                 NVARCHAR(500)  NULL,
    Status                    VARCHAR(20)    NOT NULL CONSTRAINT DF_Users_Status DEFAULT 'ACTIVE',
    CurrentLevelId            INT            NULL,
    LearningGoal              VARCHAR(20)    NOT NULL CONSTRAINT DF_Users_LearningGoal DEFAULT 'GENERAL',
    DailyGoalMinutes          INT            NOT NULL CONSTRAINT DF_Users_DailyGoalMinutes DEFAULT 15,
    TimeZone                  VARCHAR(50)    NOT NULL CONSTRAINT DF_Users_TimeZone DEFAULT 'Asia/Ho_Chi_Minh',
    ReminderEnabled           BIT            NOT NULL CONSTRAINT DF_Users_ReminderEnabled DEFAULT 1,
    ReminderTime              TIME(0)        NULL,         -- giờ nhắc học theo TimeZone của user
    EmailNotificationEnabled  BIT            NOT NULL CONSTRAINT DF_Users_EmailNotif DEFAULT 0,
    PlacementCompletedAt      DATETIME2(3)   NULL,
    LastLoginAt               DATETIME2(3)   NULL,
    CreatedAt                 DATETIME2(3)   NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt                 DATETIME2(3)   NOT NULL CONSTRAINT DF_Users_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Users PRIMARY KEY (Id),
    CONSTRAINT UQ_Users_Email UNIQUE (Email),
    CONSTRAINT FK_Users_Levels FOREIGN KEY (CurrentLevelId) REFERENCES Levels(Id),
    CONSTRAINT CK_Users_Status CHECK (Status IN ('ACTIVE','LOCKED','DISABLED')),
    CONSTRAINT CK_Users_LearningGoal CHECK (LearningGoal IN ('GENERAL','COMMUNICATION','TOEIC','IELTS')),
    CONSTRAINT CK_Users_DailyGoalMinutes CHECK (DailyGoalMinutes BETWEEN 5 AND 480)
);
CREATE INDEX IX_Users_CurrentLevelId ON Users(CurrentLevelId);
CREATE INDEX IX_Users_Status_CreatedAt ON Users(Status, CreatedAt);

CREATE TABLE UserRoles (
    UserId     BIGINT        NOT NULL,
    RoleId     INT           NOT NULL,
    CreatedAt  DATETIME2(3)  NOT NULL CONSTRAINT DF_UserRoles_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt  DATETIME2(3)  NOT NULL CONSTRAINT DF_UserRoles_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_UserRoles PRIMARY KEY (UserId, RoleId),
    CONSTRAINT FK_UserRoles_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT FK_UserRoles_Roles FOREIGN KEY (RoleId) REFERENCES Roles(Id)
);
CREATE INDEX IX_UserRoles_RoleId ON UserRoles(RoleId);

-- Refresh token: chỉ lưu SHA-256 hash (hex 64 ký tự), không lưu token gốc
CREATE TABLE RefreshTokens (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    UserId      BIGINT        NOT NULL,
    TokenHash   CHAR(64)      NOT NULL,
    ExpiresAt   DATETIME2(3)  NOT NULL,
    RevokedAt   DATETIME2(3)  NULL,
    UserAgent   NVARCHAR(300) NULL,
    IpAddress   VARCHAR(45)   NULL,
    CreatedAt   DATETIME2(3)  NOT NULL CONSTRAINT DF_RefreshTokens_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt   DATETIME2(3)  NOT NULL CONSTRAINT DF_RefreshTokens_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_RefreshTokens PRIMARY KEY (Id),
    CONSTRAINT UQ_RefreshTokens_TokenHash UNIQUE (TokenHash),
    CONSTRAINT FK_RefreshTokens_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE
);
CREATE INDEX IX_RefreshTokens_UserId ON RefreshTokens(UserId) INCLUDE (ExpiresAt, RevokedAt);

CREATE TABLE PasswordResetTokens (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    UserId      BIGINT        NOT NULL,
    TokenHash   CHAR(64)      NOT NULL,
    ExpiresAt   DATETIME2(3)  NOT NULL,
    UsedAt      DATETIME2(3)  NULL,
    CreatedAt   DATETIME2(3)  NOT NULL CONSTRAINT DF_PwdReset_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt   DATETIME2(3)  NOT NULL CONSTRAINT DF_PwdReset_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_PasswordResetTokens PRIMARY KEY (Id),
    CONSTRAINT UQ_PasswordResetTokens_TokenHash UNIQUE (TokenHash),
    CONSTRAINT FK_PasswordResetTokens_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE
);
CREATE INDEX IX_PasswordResetTokens_UserId ON PasswordResetTokens(UserId);

/* =============================== 2. NỘI DUNG HỌC ========================== */

CREATE TABLE Topics (
    Id           INT IDENTITY(1,1) NOT NULL,
    Name         NVARCHAR(100)  NOT NULL,
    Slug         VARCHAR(120)   NOT NULL,
    Description  NVARCHAR(500)  NULL,
    IconUrl      NVARCHAR(500)  NULL,
    CreatedAt    DATETIME2(3)   NOT NULL CONSTRAINT DF_Topics_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(3)   NOT NULL CONSTRAINT DF_Topics_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Topics PRIMARY KEY (Id),
    CONSTRAINT UQ_Topics_Slug UNIQUE (Slug)
);

CREATE TABLE Courses (
    Id            BIGINT IDENTITY(1,1) NOT NULL,
    Title         NVARCHAR(200)  NOT NULL,
    Slug          VARCHAR(220)   NOT NULL,
    Description   NVARCHAR(MAX)  NULL,
    LevelId       INT            NOT NULL,
    GoalType      VARCHAR(20)    NOT NULL CONSTRAINT DF_Courses_GoalType DEFAULT 'GENERAL',
    ThumbnailUrl  NVARCHAR(500)  NULL,
    IsPublished   BIT            NOT NULL CONSTRAINT DF_Courses_IsPublished DEFAULT 0,
    SortOrder     INT            NOT NULL CONSTRAINT DF_Courses_SortOrder DEFAULT 0,
    CreatedBy     BIGINT         NULL,
    CreatedAt     DATETIME2(3)   NOT NULL CONSTRAINT DF_Courses_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt     DATETIME2(3)   NOT NULL CONSTRAINT DF_Courses_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Courses PRIMARY KEY (Id),
    CONSTRAINT UQ_Courses_Slug UNIQUE (Slug),
    CONSTRAINT FK_Courses_Levels FOREIGN KEY (LevelId) REFERENCES Levels(Id),
    CONSTRAINT FK_Courses_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES Users(Id),
    CONSTRAINT CK_Courses_GoalType CHECK (GoalType IN ('GENERAL','COMMUNICATION','TOEIC','IELTS'))
);
CREATE INDEX IX_Courses_Level_Goal ON Courses(LevelId, GoalType, IsPublished) INCLUDE (SortOrder);

CREATE TABLE Lessons (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    CourseId          BIGINT         NOT NULL,
    TopicId           INT            NULL,
    LevelId           INT            NOT NULL,
    Title             NVARCHAR(200)  NOT NULL,
    Slug              VARCHAR(220)   NOT NULL,
    Skill             VARCHAR(20)    NOT NULL,
    Summary           NVARCHAR(500)  NULL,
    Content           NVARCHAR(MAX)  NULL,          -- HTML/Markdown: bài đọc, lý thuyết, đề bài viết
    MediaType         VARCHAR(10)    NOT NULL CONSTRAINT DF_Lessons_MediaType DEFAULT 'NONE',
    MediaUrl          NVARCHAR(500)  NULL,
    DurationSeconds   INT            NULL,          -- độ dài audio/video
    EstimatedMinutes  INT            NOT NULL CONSTRAINT DF_Lessons_EstimatedMinutes DEFAULT 10,
    SortOrder         INT            NOT NULL CONSTRAINT DF_Lessons_SortOrder DEFAULT 0,
    IsPublished       BIT            NOT NULL CONSTRAINT DF_Lessons_IsPublished DEFAULT 0,
    CreatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_Lessons_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_Lessons_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Lessons PRIMARY KEY (Id),
    CONSTRAINT UQ_Lessons_Course_Slug UNIQUE (CourseId, Slug),
    CONSTRAINT FK_Lessons_Courses FOREIGN KEY (CourseId) REFERENCES Courses(Id) ON DELETE CASCADE,
    CONSTRAINT FK_Lessons_Topics FOREIGN KEY (TopicId) REFERENCES Topics(Id),
    CONSTRAINT FK_Lessons_Levels FOREIGN KEY (LevelId) REFERENCES Levels(Id),
    CONSTRAINT CK_Lessons_Skill CHECK (Skill IN ('LISTENING','SPEAKING','READING','WRITING','VOCABULARY','GRAMMAR')),
    CONSTRAINT CK_Lessons_MediaType CHECK (MediaType IN ('NONE','AUDIO','VIDEO')),
    CONSTRAINT CK_Lessons_Media CHECK (MediaType = 'NONE' OR MediaUrl IS NOT NULL),
    CONSTRAINT CK_Lessons_EstimatedMinutes CHECK (EstimatedMinutes > 0)
);
CREATE INDEX IX_Lessons_Course_Sort ON Lessons(CourseId, SortOrder);
CREATE INDEX IX_Lessons_Level_Skill ON Lessons(LevelId, Skill, IsPublished);
CREATE INDEX IX_Lessons_TopicId ON Lessons(TopicId);

-- Phụ đề tương tác: mỗi câu có mốc thời gian, bấm vào để tua đến StartMs
CREATE TABLE LessonTranscriptSegments (
    Id           BIGINT IDENTITY(1,1) NOT NULL,
    LessonId     BIGINT          NOT NULL,
    SortOrder    INT             NOT NULL,
    StartMs      INT             NOT NULL,
    EndMs        INT             NOT NULL,
    Text         NVARCHAR(1000)  NOT NULL,
    Translation  NVARCHAR(1000)  NULL,
    CreatedAt    DATETIME2(3)    NOT NULL CONSTRAINT DF_LTS_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(3)    NOT NULL CONSTRAINT DF_LTS_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_LessonTranscriptSegments PRIMARY KEY (Id),
    CONSTRAINT UQ_LTS_Lesson_Sort UNIQUE (LessonId, SortOrder),
    CONSTRAINT FK_LTS_Lessons FOREIGN KEY (LessonId) REFERENCES Lessons(Id) ON DELETE CASCADE,
    CONSTRAINT CK_LTS_Time CHECK (StartMs >= 0 AND EndMs > StartMs)
);

/* =========================== 3. NGÂN HÀNG CÂU HỎI & BÀI TEST =============== */

/*  Cách lưu đáp án theo QuestionType:
    - SINGLE_CHOICE / MULTIPLE_CHOICE : QuestionOptions.IsCorrect
    - FILL_BLANK   : Questions.AnswerKey = JSON mảng các blank, mỗi blank là mảng đáp án chấp nhận
                     vd: [["went"],["has been","'s been"]]; trong Content đánh dấu blank bằng {{1}}, {{2}}
    - MATCHING     : QuestionOptions.Content (vế trái) + MatchText (vế phải đúng)
    - ORDERING     : QuestionOptions.Content (mảnh câu) + CorrectOrder (vị trí đúng, từ 1)
    - SPEAKING     : Questions.AnswerKey = câu mẫu cần đọc (chấm bởi PronunciationScorer)
    - ESSAY        : không có đáp án cố định (chấm bởi WritingEvaluator / admin)                        */
CREATE TABLE Questions (
    Id            BIGINT IDENTITY(1,1) NOT NULL,
    QuestionType  VARCHAR(20)     NOT NULL,
    Skill         VARCHAR(20)     NOT NULL,
    LevelId       INT             NULL,
    TopicId       INT             NULL,
    Content       NVARCHAR(MAX)   NOT NULL,
    Passage       NVARCHAR(MAX)   NULL,           -- đoạn văn dùng chung cho câu hỏi đọc hiểu
    MediaUrl      NVARCHAR(500)   NULL,
    AnswerKey     NVARCHAR(MAX)   NULL,
    Explanation   NVARCHAR(MAX)   NULL,
    Difficulty    TINYINT         NOT NULL CONSTRAINT DF_Questions_Difficulty DEFAULT 1,
    DefaultPoints DECIMAL(5,2)    NOT NULL CONSTRAINT DF_Questions_DefaultPoints DEFAULT 1,
    IsActive      BIT             NOT NULL CONSTRAINT DF_Questions_IsActive DEFAULT 1,
    CreatedBy     BIGINT          NULL,
    CreatedAt     DATETIME2(3)    NOT NULL CONSTRAINT DF_Questions_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt     DATETIME2(3)    NOT NULL CONSTRAINT DF_Questions_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Questions PRIMARY KEY (Id),
    CONSTRAINT FK_Questions_Levels FOREIGN KEY (LevelId) REFERENCES Levels(Id),
    CONSTRAINT FK_Questions_Topics FOREIGN KEY (TopicId) REFERENCES Topics(Id),
    CONSTRAINT FK_Questions_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES Users(Id),
    CONSTRAINT CK_Questions_Type CHECK (QuestionType IN
        ('SINGLE_CHOICE','MULTIPLE_CHOICE','FILL_BLANK','MATCHING','ORDERING','SPEAKING','ESSAY')),
    CONSTRAINT CK_Questions_Skill CHECK (Skill IN ('LISTENING','SPEAKING','READING','WRITING','VOCABULARY','GRAMMAR')),
    CONSTRAINT CK_Questions_Difficulty CHECK (Difficulty BETWEEN 1 AND 5),
    CONSTRAINT CK_Questions_DefaultPoints CHECK (DefaultPoints > 0),
    CONSTRAINT CK_Questions_AnswerKeyJson CHECK (
        QuestionType <> 'FILL_BLANK' OR (AnswerKey IS NOT NULL AND ISJSON(AnswerKey) = 1))
);
CREATE INDEX IX_Questions_Filter ON Questions(Skill, LevelId, QuestionType, IsActive);
CREATE INDEX IX_Questions_TopicId ON Questions(TopicId);

CREATE TABLE QuestionOptions (
    Id            BIGINT IDENTITY(1,1) NOT NULL,
    QuestionId    BIGINT          NOT NULL,
    Content       NVARCHAR(1000)  NOT NULL,
    IsCorrect     BIT             NOT NULL CONSTRAINT DF_QOptions_IsCorrect DEFAULT 0,
    MatchText     NVARCHAR(1000)  NULL,           -- MATCHING
    CorrectOrder  INT             NULL,           -- ORDERING
    SortOrder     INT             NOT NULL CONSTRAINT DF_QOptions_SortOrder DEFAULT 0,
    CreatedAt     DATETIME2(3)    NOT NULL CONSTRAINT DF_QOptions_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt     DATETIME2(3)    NOT NULL CONSTRAINT DF_QOptions_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_QuestionOptions PRIMARY KEY (Id),
    CONSTRAINT FK_QuestionOptions_Questions FOREIGN KEY (QuestionId) REFERENCES Questions(Id) ON DELETE CASCADE,
    CONSTRAINT CK_QuestionOptions_CorrectOrder CHECK (CorrectOrder IS NULL OR CorrectOrder >= 1)
);
CREATE INDEX IX_QuestionOptions_QuestionId ON QuestionOptions(QuestionId, SortOrder);

CREATE TABLE Tests (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    Title             NVARCHAR(200)  NOT NULL,
    Description       NVARCHAR(MAX)  NULL,
    TestType          VARCHAR(20)    NOT NULL,
    LessonId          BIGINT         NULL,         -- quiz gắn với bài học
    LevelId           INT            NULL,
    DurationMinutes   INT            NULL,         -- NULL = không giới hạn
    PassingPercent    DECIMAL(5,2)   NULL,
    ShuffleQuestions  BIT            NOT NULL CONSTRAINT DF_Tests_Shuffle DEFAULT 0,
    MaxAttempts       INT            NULL,         -- NULL = không giới hạn
    IsPublished       BIT            NOT NULL CONSTRAINT DF_Tests_IsPublished DEFAULT 0,
    CreatedBy         BIGINT         NULL,
    CreatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_Tests_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_Tests_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Tests PRIMARY KEY (Id),
    CONSTRAINT FK_Tests_Lessons FOREIGN KEY (LessonId) REFERENCES Lessons(Id) ON DELETE SET NULL,
    CONSTRAINT FK_Tests_Levels FOREIGN KEY (LevelId) REFERENCES Levels(Id),
    CONSTRAINT FK_Tests_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES Users(Id),
    CONSTRAINT CK_Tests_Type CHECK (TestType IN ('PLACEMENT','LESSON_QUIZ','PRACTICE','MOCK_EXAM')),
    CONSTRAINT CK_Tests_Duration CHECK (DurationMinutes IS NULL OR DurationMinutes > 0),
    CONSTRAINT CK_Tests_PassingPercent CHECK (PassingPercent IS NULL OR PassingPercent BETWEEN 0 AND 100)
);
CREATE INDEX IX_Tests_Type_Published ON Tests(TestType, IsPublished);
CREATE INDEX IX_Tests_LessonId ON Tests(LessonId);

CREATE TABLE TestQuestions (
    TestId      BIGINT        NOT NULL,
    QuestionId  BIGINT        NOT NULL,
    SortOrder   INT           NOT NULL,
    Points      DECIMAL(5,2)  NULL,               -- NULL = dùng Questions.DefaultPoints
    CreatedAt   DATETIME2(3)  NOT NULL CONSTRAINT DF_TestQuestions_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt   DATETIME2(3)  NOT NULL CONSTRAINT DF_TestQuestions_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_TestQuestions PRIMARY KEY (TestId, QuestionId),
    CONSTRAINT FK_TestQuestions_Tests FOREIGN KEY (TestId) REFERENCES Tests(Id) ON DELETE CASCADE,
    CONSTRAINT FK_TestQuestions_Questions FOREIGN KEY (QuestionId) REFERENCES Questions(Id),
    CONSTRAINT CK_TestQuestions_Points CHECK (Points IS NULL OR Points > 0)
);
CREATE INDEX IX_TestQuestions_QuestionId ON TestQuestions(QuestionId);

CREATE TABLE TestAttempts (
    Id               BIGINT IDENTITY(1,1) NOT NULL,
    UserId           BIGINT          NOT NULL,
    TestId           BIGINT          NOT NULL,
    Status           VARCHAR(20)     NOT NULL CONSTRAINT DF_TestAttempts_Status DEFAULT 'IN_PROGRESS',
    StartedAt        DATETIME2(3)    NOT NULL CONSTRAINT DF_TestAttempts_StartedAt DEFAULT SYSUTCDATETIME(),
    ExpiresAt        DATETIME2(3)    NULL,        -- StartedAt + DurationMinutes
    SubmittedAt      DATETIME2(3)    NULL,
    Score            DECIMAL(7,2)    NULL,
    MaxScore         DECIMAL(7,2)    NULL,
    Percentage       DECIMAL(5,2)    NULL,
    ResultLevelId    INT             NULL,        -- chỉ dùng cho PLACEMENT
    SkillScoresJson  NVARCHAR(MAX)   NULL,        -- [{"skill":"GRAMMAR","score":6.0,"maxScore":8.0,"percentage":75.00},...]
    CreatedAt        DATETIME2(3)    NOT NULL CONSTRAINT DF_TestAttempts_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt        DATETIME2(3)    NOT NULL CONSTRAINT DF_TestAttempts_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_TestAttempts PRIMARY KEY (Id),
    CONSTRAINT FK_TestAttempts_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT FK_TestAttempts_Tests FOREIGN KEY (TestId) REFERENCES Tests(Id),
    CONSTRAINT FK_TestAttempts_Levels FOREIGN KEY (ResultLevelId) REFERENCES Levels(Id),
    CONSTRAINT CK_TestAttempts_Status CHECK (Status IN ('IN_PROGRESS','SUBMITTED','EXPIRED')),
    CONSTRAINT CK_TestAttempts_Percentage CHECK (Percentage IS NULL OR Percentage BETWEEN 0 AND 100),
    CONSTRAINT CK_TestAttempts_SkillJson CHECK (SkillScoresJson IS NULL OR ISJSON(SkillScoresJson) = 1)
);
CREATE INDEX IX_TestAttempts_User_Test ON TestAttempts(UserId, TestId, StartedAt DESC);
CREATE INDEX IX_TestAttempts_Test_Status ON TestAttempts(TestId, Status) INCLUDE (Percentage);

CREATE TABLE AttemptAnswers (
    Id                 BIGINT IDENTITY(1,1) NOT NULL,
    AttemptId          BIGINT          NOT NULL,
    QuestionId         BIGINT          NOT NULL,
    SelectedOptionIds  VARCHAR(200)    NULL,      -- JSON mảng id, vd: [12,13]
    AnswerText         NVARCHAR(MAX)   NULL,      -- JSON cho FILL_BLANK/MATCHING/ORDERING, text cho ESSAY
    IsCorrect          BIT             NULL,      -- NULL = chưa chấm (ESSAY/SPEAKING chờ AI)
    Score              DECIMAL(5,2)    NULL,
    Feedback           NVARCHAR(MAX)   NULL,
    AnsweredAt         DATETIME2(3)    NULL,
    CreatedAt          DATETIME2(3)    NOT NULL CONSTRAINT DF_AttemptAnswers_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt          DATETIME2(3)    NOT NULL CONSTRAINT DF_AttemptAnswers_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_AttemptAnswers PRIMARY KEY (Id),
    CONSTRAINT UQ_AttemptAnswers_Attempt_Question UNIQUE (AttemptId, QuestionId),
    CONSTRAINT FK_AttemptAnswers_Attempts FOREIGN KEY (AttemptId) REFERENCES TestAttempts(Id) ON DELETE CASCADE,
    CONSTRAINT FK_AttemptAnswers_Questions FOREIGN KEY (QuestionId) REFERENCES Questions(Id)
);
CREATE INDEX IX_AttemptAnswers_QuestionId ON AttemptAnswers(QuestionId) INCLUDE (IsCorrect);

/* =============================== 4. LỘ TRÌNH HỌC ========================== */

CREATE TABLE LearningPaths (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    UserId            BIGINT         NOT NULL,
    Name              NVARCHAR(200)  NOT NULL,
    GoalType          VARCHAR(20)    NOT NULL,
    LevelId           INT            NOT NULL,
    Source            VARCHAR(20)    NOT NULL,
    Status            VARCHAR(20)    NOT NULL CONSTRAINT DF_LearningPaths_Status DEFAULT 'ACTIVE',
    DailyMinutes      INT            NOT NULL CONSTRAINT DF_LearningPaths_DailyMinutes DEFAULT 15,
    TargetDate        DATE           NULL,
    PlacementAttemptId BIGINT        NULL,
    CreatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_LearningPaths_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_LearningPaths_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_LearningPaths PRIMARY KEY (Id),
    CONSTRAINT FK_LearningPaths_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT FK_LearningPaths_Levels FOREIGN KEY (LevelId) REFERENCES Levels(Id),
    CONSTRAINT FK_LearningPaths_Attempts FOREIGN KEY (PlacementAttemptId) REFERENCES TestAttempts(Id),
    CONSTRAINT CK_LearningPaths_GoalType CHECK (GoalType IN ('GENERAL','COMMUNICATION','TOEIC','IELTS')),
    CONSTRAINT CK_LearningPaths_Source CHECK (Source IN ('PLACEMENT','GOAL','MANUAL')),
    CONSTRAINT CK_LearningPaths_Status CHECK (Status IN ('ACTIVE','COMPLETED','ARCHIVED'))
);
-- Mỗi học viên chỉ có tối đa 1 lộ trình ACTIVE
CREATE UNIQUE INDEX UX_LearningPaths_User_Active ON LearningPaths(UserId) WHERE Status = 'ACTIVE';

CREATE TABLE LearningPathItems (
    Id            BIGINT IDENTITY(1,1) NOT NULL,
    PathId        BIGINT        NOT NULL,
    LessonId      BIGINT        NOT NULL,
    SortOrder     INT           NOT NULL,
    Status        VARCHAR(20)   NOT NULL CONSTRAINT DF_LPItems_Status DEFAULT 'LOCKED',
    IsCustom      BIT           NOT NULL CONSTRAINT DF_LPItems_IsCustom DEFAULT 0,  -- học viên tự thêm
    PlannedDate   DATE          NULL,
    CompletedAt   DATETIME2(3)  NULL,
    CreatedAt     DATETIME2(3)  NOT NULL CONSTRAINT DF_LPItems_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt     DATETIME2(3)  NOT NULL CONSTRAINT DF_LPItems_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_LearningPathItems PRIMARY KEY (Id),
    CONSTRAINT UQ_LPItems_Path_Lesson UNIQUE (PathId, LessonId),
    CONSTRAINT FK_LPItems_Paths FOREIGN KEY (PathId) REFERENCES LearningPaths(Id) ON DELETE CASCADE,
    CONSTRAINT FK_LPItems_Lessons FOREIGN KEY (LessonId) REFERENCES Lessons(Id),
    CONSTRAINT CK_LPItems_Status CHECK (Status IN ('LOCKED','AVAILABLE','IN_PROGRESS','COMPLETED','SKIPPED'))
);
CREATE INDEX IX_LPItems_Path_Sort ON LearningPathItems(PathId, SortOrder);
CREATE INDEX IX_LPItems_LessonId ON LearningPathItems(LessonId);

/* =========================== 5. BÀI NỘP SPEAKING / WRITING ================ */

CREATE TABLE SpeakingSubmissions (
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    UserId              BIGINT          NOT NULL,
    LessonId            BIGINT          NULL,
    QuestionId          BIGINT          NULL,
    ReferenceText       NVARCHAR(1000)  NOT NULL,
    AudioUrl            NVARCHAR(500)   NOT NULL,
    RecognizedText      NVARCHAR(1000)  NULL,
    PronunciationScore  DECIMAL(5,2)    NULL,
    AccuracyScore       DECIMAL(5,2)    NULL,
    FluencyScore        DECIMAL(5,2)    NULL,
    CompletenessScore   DECIMAL(5,2)    NULL,
    DetailJson          NVARCHAR(MAX)   NULL,     -- kết quả thô theo từng từ/âm vị từ provider
    Provider            VARCHAR(30)     NOT NULL,
    Status              VARCHAR(20)     NOT NULL CONSTRAINT DF_Speaking_Status DEFAULT 'PENDING',
    ErrorMessage        NVARCHAR(500)   NULL,
    CreatedAt           DATETIME2(3)    NOT NULL CONSTRAINT DF_Speaking_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt           DATETIME2(3)    NOT NULL CONSTRAINT DF_Speaking_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_SpeakingSubmissions PRIMARY KEY (Id),
    CONSTRAINT FK_Speaking_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT FK_Speaking_Lessons FOREIGN KEY (LessonId) REFERENCES Lessons(Id),
    CONSTRAINT FK_Speaking_Questions FOREIGN KEY (QuestionId) REFERENCES Questions(Id),
    CONSTRAINT CK_Speaking_Status CHECK (Status IN ('PENDING','SCORED','FAILED')),
    CONSTRAINT CK_Speaking_Score CHECK (PronunciationScore IS NULL OR PronunciationScore BETWEEN 0 AND 100),
    CONSTRAINT CK_Speaking_DetailJson CHECK (DetailJson IS NULL OR ISJSON(DetailJson) = 1)
);
CREATE INDEX IX_Speaking_User_CreatedAt ON SpeakingSubmissions(UserId, CreatedAt DESC);
CREATE INDEX IX_Speaking_LessonId ON SpeakingSubmissions(LessonId);

CREATE TABLE WritingSubmissions (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    UserId            BIGINT          NOT NULL,
    LessonId          BIGINT          NULL,
    QuestionId        BIGINT          NULL,
    Prompt            NVARCHAR(2000)  NULL,
    Content           NVARCHAR(MAX)   NOT NULL,
    WordCount         INT             NOT NULL CONSTRAINT DF_Writing_WordCount DEFAULT 0,
    Score             DECIMAL(5,2)    NULL,       -- thang 0-100
    CorrectedContent  NVARCHAR(MAX)   NULL,
    FeedbackJson      NVARCHAR(MAX)   NULL,       -- [{"start":10,"end":14,"type":"GRAMMAR","suggestion":"...","message":"..."}]
    Provider          VARCHAR(30)     NOT NULL,
    Status            VARCHAR(20)     NOT NULL CONSTRAINT DF_Writing_Status DEFAULT 'PENDING',
    ErrorMessage      NVARCHAR(500)   NULL,
    CreatedAt         DATETIME2(3)    NOT NULL CONSTRAINT DF_Writing_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)    NOT NULL CONSTRAINT DF_Writing_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_WritingSubmissions PRIMARY KEY (Id),
    CONSTRAINT FK_Writing_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT FK_Writing_Lessons FOREIGN KEY (LessonId) REFERENCES Lessons(Id),
    CONSTRAINT FK_Writing_Questions FOREIGN KEY (QuestionId) REFERENCES Questions(Id),
    CONSTRAINT CK_Writing_Status CHECK (Status IN ('PENDING','SCORED','FAILED')),
    CONSTRAINT CK_Writing_Score CHECK (Score IS NULL OR Score BETWEEN 0 AND 100),
    CONSTRAINT CK_Writing_FeedbackJson CHECK (FeedbackJson IS NULL OR ISJSON(FeedbackJson) = 1)
);
CREATE INDEX IX_Writing_User_CreatedAt ON WritingSubmissions(UserId, CreatedAt DESC);
CREATE INDEX IX_Writing_LessonId ON WritingSubmissions(LessonId);

/* =============================== 6. TỪ VỰNG & SRS ========================= */

CREATE TABLE Words (
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    Headword            NVARCHAR(100)   NOT NULL,
    PartOfSpeech        VARCHAR(20)     NOT NULL CONSTRAINT DF_Words_POS DEFAULT 'other',
    PhoneticUk          NVARCHAR(100)   NULL,
    PhoneticUs          NVARCHAR(100)   NULL,
    AudioUrlUk          NVARCHAR(500)   NULL,
    AudioUrlUs          NVARCHAR(500)   NULL,
    MeaningVi           NVARCHAR(500)   NOT NULL,
    DefinitionEn        NVARCHAR(1000)  NULL,
    Example             NVARCHAR(1000)  NULL,
    ExampleTranslation  NVARCHAR(1000)  NULL,
    ImageUrl            NVARCHAR(500)   NULL,
    LevelId             INT             NULL,
    Source              VARCHAR(20)     NOT NULL CONSTRAINT DF_Words_Source DEFAULT 'ADMIN',
    CreatedAt           DATETIME2(3)    NOT NULL CONSTRAINT DF_Words_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt           DATETIME2(3)    NOT NULL CONSTRAINT DF_Words_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Words PRIMARY KEY (Id),
    CONSTRAINT UQ_Words_Headword_POS UNIQUE (Headword, PartOfSpeech),
    CONSTRAINT FK_Words_Levels FOREIGN KEY (LevelId) REFERENCES Levels(Id),
    CONSTRAINT CK_Words_POS CHECK (PartOfSpeech IN
        ('noun','verb','adjective','adverb','pronoun','preposition','conjunction','interjection','phrase','other')),
    CONSTRAINT CK_Words_Source CHECK (Source IN ('ADMIN','DICTIONARY_API','USER'))
);
CREATE INDEX IX_Words_LevelId ON Words(LevelId);

CREATE TABLE VocabularySets (
    Id            BIGINT IDENTITY(1,1) NOT NULL,
    Name          NVARCHAR(150)  NOT NULL,
    Slug          VARCHAR(170)   NOT NULL,
    Description   NVARCHAR(500)  NULL,
    LevelId       INT            NULL,
    TopicId       INT            NULL,
    OwnerUserId   BIGINT         NULL,           -- NULL = bộ từ hệ thống do admin tạo
    IsPublished   BIT            NOT NULL CONSTRAINT DF_VocabSets_IsPublished DEFAULT 0,
    CoverImageUrl NVARCHAR(500)  NULL,
    CreatedAt     DATETIME2(3)   NOT NULL CONSTRAINT DF_VocabSets_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt     DATETIME2(3)   NOT NULL CONSTRAINT DF_VocabSets_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_VocabularySets PRIMARY KEY (Id),
    CONSTRAINT UQ_VocabularySets_Slug UNIQUE (Slug),
    CONSTRAINT FK_VocabSets_Levels FOREIGN KEY (LevelId) REFERENCES Levels(Id),
    CONSTRAINT FK_VocabSets_Topics FOREIGN KEY (TopicId) REFERENCES Topics(Id),
    CONSTRAINT FK_VocabSets_Owner FOREIGN KEY (OwnerUserId) REFERENCES Users(Id) ON DELETE CASCADE
);
CREATE INDEX IX_VocabSets_Level_Topic ON VocabularySets(LevelId, TopicId, IsPublished);
CREATE INDEX IX_VocabSets_OwnerUserId ON VocabularySets(OwnerUserId);

CREATE TABLE VocabularySetWords (
    SetId      BIGINT        NOT NULL,
    WordId     BIGINT        NOT NULL,
    SortOrder  INT           NOT NULL CONSTRAINT DF_VSW_SortOrder DEFAULT 0,
    CreatedAt  DATETIME2(3)  NOT NULL CONSTRAINT DF_VSW_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt  DATETIME2(3)  NOT NULL CONSTRAINT DF_VSW_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_VocabularySetWords PRIMARY KEY (SetId, WordId),
    CONSTRAINT FK_VSW_Sets FOREIGN KEY (SetId) REFERENCES VocabularySets(Id) ON DELETE CASCADE,
    CONSTRAINT FK_VSW_Words FOREIGN KEY (WordId) REFERENCES Words(Id) ON DELETE CASCADE
);
CREATE INDEX IX_VSW_WordId ON VocabularySetWords(WordId);

/*  Sổ từ vựng cá nhân + trạng thái SRS (SM-2) của từng từ với từng học viên.
    EaseFactor >= 1.3; IntervalDays = khoảng cách ôn hiện tại; DueAt = thời điểm cần ôn tiếp. */
CREATE TABLE UserWords (
    Id              BIGINT IDENTITY(1,1) NOT NULL,
    UserId          BIGINT          NOT NULL,
    WordId          BIGINT          NOT NULL,
    Source          VARCHAR(20)     NOT NULL CONSTRAINT DF_UserWords_Source DEFAULT 'MANUAL',
    SourceLessonId  BIGINT          NULL,         -- bài đọc nơi tra từ
    ContextSentence NVARCHAR(1000)  NULL,         -- câu chứa từ khi tra
    Note            NVARCHAR(500)   NULL,
    Status          VARCHAR(20)     NOT NULL CONSTRAINT DF_UserWords_Status DEFAULT 'NEW',
    EaseFactor      DECIMAL(4,2)    NOT NULL CONSTRAINT DF_UserWords_EaseFactor DEFAULT 2.50,
    IntervalDays    INT             NOT NULL CONSTRAINT DF_UserWords_IntervalDays DEFAULT 0,
    Repetitions     INT             NOT NULL CONSTRAINT DF_UserWords_Repetitions DEFAULT 0,
    LapseCount      INT             NOT NULL CONSTRAINT DF_UserWords_LapseCount DEFAULT 0,
    DueAt           DATETIME2(3)    NOT NULL CONSTRAINT DF_UserWords_DueAt DEFAULT SYSUTCDATETIME(),
    LastReviewedAt  DATETIME2(3)    NULL,
    CreatedAt       DATETIME2(3)    NOT NULL CONSTRAINT DF_UserWords_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt       DATETIME2(3)    NOT NULL CONSTRAINT DF_UserWords_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_UserWords PRIMARY KEY (Id),
    CONSTRAINT UQ_UserWords_User_Word UNIQUE (UserId, WordId),
    CONSTRAINT FK_UserWords_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT FK_UserWords_Words FOREIGN KEY (WordId) REFERENCES Words(Id),
    CONSTRAINT FK_UserWords_Lessons FOREIGN KEY (SourceLessonId) REFERENCES Lessons(Id) ON DELETE SET NULL,
    CONSTRAINT CK_UserWords_Source CHECK (Source IN ('MANUAL','READING_LOOKUP','VOCAB_SET','LESSON')),
    CONSTRAINT CK_UserWords_Status CHECK (Status IN ('NEW','LEARNING','REVIEW','MASTERED','SUSPENDED')),
    CONSTRAINT CK_UserWords_EaseFactor CHECK (EaseFactor >= 1.30),
    CONSTRAINT CK_UserWords_Counters CHECK (IntervalDays >= 0 AND Repetitions >= 0 AND LapseCount >= 0)
);
-- Truy vấn chính của SRS: "các thẻ đến hạn của user X"
CREATE INDEX IX_UserWords_User_Due ON UserWords(UserId, DueAt) INCLUDE (Status) WHERE Status <> 'SUSPENDED';
CREATE INDEX IX_UserWords_User_Status ON UserWords(UserId, Status);
CREATE INDEX IX_UserWords_WordId ON UserWords(WordId);

CREATE TABLE FlashcardReviews (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    UserWordId        BIGINT        NOT NULL,
    UserId            BIGINT        NOT NULL,     -- phi chuẩn hóa để thống kê nhanh theo user
    Quality           TINYINT       NOT NULL,     -- 0..5 theo SM-2
    PrevEaseFactor    DECIMAL(4,2)  NOT NULL,
    NewEaseFactor     DECIMAL(4,2)  NOT NULL,
    PrevIntervalDays  INT           NOT NULL,
    NewIntervalDays   INT           NOT NULL,
    ResponseTimeMs    INT           NULL,
    ReviewedAt        DATETIME2(3)  NOT NULL CONSTRAINT DF_FlashcardReviews_ReviewedAt DEFAULT SYSUTCDATETIME(),
    CreatedAt         DATETIME2(3)  NOT NULL CONSTRAINT DF_FlashcardReviews_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)  NOT NULL CONSTRAINT DF_FlashcardReviews_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_FlashcardReviews PRIMARY KEY (Id),
    -- FK tới Users để NO ACTION nhằm tránh lỗi "multiple cascade paths":
    -- xóa user -> cascade UserWords -> cascade FlashcardReviews.
    CONSTRAINT FK_FlashcardReviews_UserWords FOREIGN KEY (UserWordId) REFERENCES UserWords(Id) ON DELETE CASCADE,
    CONSTRAINT FK_FlashcardReviews_Users FOREIGN KEY (UserId) REFERENCES Users(Id),
    CONSTRAINT CK_FlashcardReviews_Quality CHECK (Quality BETWEEN 0 AND 5)
);
CREATE INDEX IX_FlashcardReviews_User_ReviewedAt ON FlashcardReviews(UserId, ReviewedAt);
CREATE INDEX IX_FlashcardReviews_UserWordId ON FlashcardReviews(UserWordId);

/* =============================== 7. TIẾN ĐỘ ================================ */

CREATE TABLE LessonProgresses (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    UserId            BIGINT         NOT NULL,
    LessonId          BIGINT         NOT NULL,
    Status            VARCHAR(20)    NOT NULL CONSTRAINT DF_LessonProgresses_Status DEFAULT 'IN_PROGRESS',
    ProgressPercent   DECIMAL(5,2)   NOT NULL CONSTRAINT DF_LessonProgresses_Percent DEFAULT 0,
    BestScore         DECIMAL(5,2)   NULL,
    LastPositionMs    INT            NULL,        -- vị trí audio/video lần cuối
    TimeSpentSeconds  INT            NOT NULL CONSTRAINT DF_LessonProgresses_TimeSpent DEFAULT 0,
    StartedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_LessonProgresses_StartedAt DEFAULT SYSUTCDATETIME(),
    CompletedAt       DATETIME2(3)   NULL,
    CreatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_LessonProgresses_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_LessonProgresses_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_LessonProgresses PRIMARY KEY (Id),
    CONSTRAINT UQ_LessonProgresses_User_Lesson UNIQUE (UserId, LessonId),
    CONSTRAINT FK_LessonProgresses_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT FK_LessonProgresses_Lessons FOREIGN KEY (LessonId) REFERENCES Lessons(Id) ON DELETE CASCADE,
    CONSTRAINT CK_LessonProgresses_Status CHECK (Status IN ('IN_PROGRESS','COMPLETED')),
    CONSTRAINT CK_LessonProgresses_Percent CHECK (ProgressPercent BETWEEN 0 AND 100),
    CONSTRAINT CK_LessonProgresses_Completed CHECK (Status <> 'COMPLETED' OR CompletedAt IS NOT NULL)
);
CREATE INDEX IX_LessonProgresses_Lesson_Status ON LessonProgresses(LessonId, Status);

CREATE TABLE StudySessions (
    Id               BIGINT IDENTITY(1,1) NOT NULL,
    UserId           BIGINT        NOT NULL,
    ActivityType     VARCHAR(20)   NOT NULL,
    ReferenceId      BIGINT        NULL,         -- LessonId / TestId / VocabularySetId tùy ActivityType
    StartedAt        DATETIME2(3)  NOT NULL,
    EndedAt          DATETIME2(3)  NULL,
    DurationSeconds  INT           NOT NULL CONSTRAINT DF_StudySessions_Duration DEFAULT 0,
    CreatedAt        DATETIME2(3)  NOT NULL CONSTRAINT DF_StudySessions_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt        DATETIME2(3)  NOT NULL CONSTRAINT DF_StudySessions_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_StudySessions PRIMARY KEY (Id),
    CONSTRAINT FK_StudySessions_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT CK_StudySessions_Type CHECK (ActivityType IN ('LESSON','FLASHCARD','QUIZ','PLACEMENT','SPEAKING','WRITING')),
    CONSTRAINT CK_StudySessions_Time CHECK (EndedAt IS NULL OR EndedAt >= StartedAt),
    CONSTRAINT CK_StudySessions_Duration CHECK (DurationSeconds >= 0)
);
CREATE INDEX IX_StudySessions_User_StartedAt ON StudySessions(UserId, StartedAt);

-- Bảng tổng hợp theo ngày (theo múi giờ của user) phục vụ dashboard và streak
CREATE TABLE DailyActivities (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    UserId            BIGINT        NOT NULL,
    ActivityDate      DATE          NOT NULL,
    StudySeconds      INT           NOT NULL CONSTRAINT DF_DailyAct_StudySeconds DEFAULT 0,
    LessonsCompleted  INT           NOT NULL CONSTRAINT DF_DailyAct_Lessons DEFAULT 0,
    WordsLearned      INT           NOT NULL CONSTRAINT DF_DailyAct_WordsLearned DEFAULT 0,
    WordsReviewed     INT           NOT NULL CONSTRAINT DF_DailyAct_WordsReviewed DEFAULT 0,
    QuizzesTaken      INT           NOT NULL CONSTRAINT DF_DailyAct_Quizzes DEFAULT 0,
    TotalQuizScore    DECIMAL(9,2)  NOT NULL CONSTRAINT DF_DailyAct_TotalScore DEFAULT 0,  -- điểm TB = Total / QuizzesTaken
    GoalReached       BIT           NOT NULL CONSTRAINT DF_DailyAct_GoalReached DEFAULT 0,
    CreatedAt         DATETIME2(3)  NOT NULL CONSTRAINT DF_DailyAct_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)  NOT NULL CONSTRAINT DF_DailyAct_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_DailyActivities PRIMARY KEY (Id),
    CONSTRAINT UQ_DailyActivities_User_Date UNIQUE (UserId, ActivityDate),
    CONSTRAINT FK_DailyActivities_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT CK_DailyActivities_NonNegative CHECK (
        StudySeconds >= 0 AND LessonsCompleted >= 0 AND WordsLearned >= 0
        AND WordsReviewed >= 0 AND QuizzesTaken >= 0)
);
CREATE INDEX IX_DailyActivities_Date ON DailyActivities(ActivityDate) INCLUDE (UserId, StudySeconds);

CREATE TABLE UserStreaks (
    UserId            BIGINT        NOT NULL,
    CurrentStreak     INT           NOT NULL CONSTRAINT DF_UserStreaks_Current DEFAULT 0,
    LongestStreak     INT           NOT NULL CONSTRAINT DF_UserStreaks_Longest DEFAULT 0,
    LastActivityDate  DATE          NULL,
    CreatedAt         DATETIME2(3)  NOT NULL CONSTRAINT DF_UserStreaks_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)  NOT NULL CONSTRAINT DF_UserStreaks_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_UserStreaks PRIMARY KEY (UserId),
    CONSTRAINT FK_UserStreaks_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT CK_UserStreaks_Values CHECK (CurrentStreak >= 0 AND LongestStreak >= CurrentStreak)
);

/* =============================== 8. THÔNG BÁO ============================== */

CREATE TABLE Notifications (
    Id         BIGINT IDENTITY(1,1) NOT NULL,
    UserId     BIGINT          NOT NULL,
    Type       VARCHAR(30)     NOT NULL,
    Title      NVARCHAR(200)   NOT NULL,
    Message    NVARCHAR(1000)  NOT NULL,
    Link       NVARCHAR(300)   NULL,          -- route frontend, vd: /app/vocabulary/review
    IsRead     BIT             NOT NULL CONSTRAINT DF_Notifications_IsRead DEFAULT 0,
    ReadAt     DATETIME2(3)    NULL,
    Channel    VARCHAR(10)     NOT NULL CONSTRAINT DF_Notifications_Channel DEFAULT 'IN_APP',
    CreatedAt  DATETIME2(3)    NOT NULL CONSTRAINT DF_Notifications_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt  DATETIME2(3)    NOT NULL CONSTRAINT DF_Notifications_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Notifications PRIMARY KEY (Id),
    CONSTRAINT FK_Notifications_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT CK_Notifications_Type CHECK (Type IN
        ('STUDY_REMINDER','REVIEW_DUE','STREAK','ACHIEVEMENT','PATH_UPDATED','SYSTEM')),
    CONSTRAINT CK_Notifications_Channel CHECK (Channel IN ('IN_APP','EMAIL'))
);
CREATE INDEX IX_Notifications_User_Read_Created ON Notifications(UserId, IsRead, CreatedAt DESC);
