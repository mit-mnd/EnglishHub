package com.englishhub.modules.user.dto;

import com.englishhub.modules.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull(message = "Trạng thái không được để trống") UserStatus status) {
}
