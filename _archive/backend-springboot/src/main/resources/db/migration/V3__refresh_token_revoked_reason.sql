/* =====================================================================
   EnglishHub - V3: Lưu lý do thu hồi refresh token.
   Chỉ token bị thu hồi do xoay vòng (ROTATED) mà vẫn được dùng lại mới bị coi là bị đánh cắp;
   token bị thu hồi do LOGOUT/ADMIN... chỉ đơn giản là không hợp lệ.
   ===================================================================== */
ALTER TABLE RefreshTokens ADD RevokedReason VARCHAR(30) NULL;
GO

ALTER TABLE RefreshTokens ADD CONSTRAINT CK_RefreshTokens_RevokedReason CHECK (
    RevokedReason IS NULL
    OR RevokedReason IN ('ROTATED','LOGOUT','PASSWORD_CHANGED','ACCOUNT_DEACTIVATED','REUSE_DETECTED'));
GO

UPDATE RefreshTokens SET RevokedReason = 'LOGOUT' WHERE RevokedAt IS NOT NULL AND RevokedReason IS NULL;
