package com.englishhub.modules.user.entity;

public enum UserStatus {
    ACTIVE,
    /** Bị admin khóa tạm thời. */
    LOCKED,
    /** Vô hiệu hóa (thay cho xóa cứng). */
    DISABLED
}
