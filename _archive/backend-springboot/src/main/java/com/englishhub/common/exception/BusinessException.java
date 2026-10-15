package com.englishhub.common.exception;

import lombok.Getter;

/** Lỗi nghiệp vụ có mã lỗi xác định; GlobalExceptionHandler chuyển thành ApiError. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException notFound(String resource, Object id) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "Không tìm thấy " + resource + " với id = " + id);
    }
}
