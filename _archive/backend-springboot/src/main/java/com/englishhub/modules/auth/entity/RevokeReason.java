package com.englishhub.modules.auth.entity;

/** Lý do refresh token bị thu hồi. */
public enum RevokeReason {
    /** Đã được thay bằng token mới khi refresh - dùng lại token này = dấu hiệu bị đánh cắp. */
    ROTATED,
    LOGOUT,
    PASSWORD_CHANGED,
    ACCOUNT_DEACTIVATED,
    REUSE_DETECTED
}
