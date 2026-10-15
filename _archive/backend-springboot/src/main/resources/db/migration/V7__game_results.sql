-- EnglishHub - V7: Kết quả trò chơi từ vựng (kỷ lục cá nhân, thống kê lượt chơi)
CREATE TABLE GameResults (
    Id               BIGINT IDENTITY(1,1) NOT NULL,
    UserId           BIGINT        NOT NULL,
    GameType         VARCHAR(20)   NOT NULL,
    Score            INT           NOT NULL,
    CorrectCount     INT           NOT NULL,
    TotalCount       INT           NOT NULL,
    DurationSeconds  INT           NOT NULL,
    CreatedAt        DATETIME2(3)  NOT NULL CONSTRAINT DF_GameResults_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt        DATETIME2(3)  NOT NULL CONSTRAINT DF_GameResults_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_GameResults PRIMARY KEY (Id),
    CONSTRAINT FK_GameResults_Users FOREIGN KEY (UserId) REFERENCES Users(Id) ON DELETE CASCADE,
    CONSTRAINT CK_GameResults_Type CHECK (GameType IN ('MATCHING','SCRAMBLE','DICTATION')),
    CONSTRAINT CK_GameResults_Score CHECK (Score BETWEEN 0 AND 100),
    CONSTRAINT CK_GameResults_Counts CHECK (TotalCount > 0 AND CorrectCount BETWEEN 0 AND TotalCount),
    CONSTRAINT CK_GameResults_Duration CHECK (DurationSeconds >= 0)
);
CREATE INDEX IX_GameResults_User_Type ON GameResults(UserId, GameType, Score DESC);
