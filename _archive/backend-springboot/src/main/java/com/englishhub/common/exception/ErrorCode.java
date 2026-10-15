package com.englishhub.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Danh mục mã lỗi trả về cho client (trường "code" trong ApiError). */
@Getter
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request không đúng định dạng"),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"),
    QUESTION_NOT_IN_TEST(HttpStatus.BAD_REQUEST, "Câu hỏi không thuộc bài kiểm tra này"),
    INVALID_AUDIO(HttpStatus.BAD_REQUEST, "File ghi âm không hợp lệ (cần WAV, dài 0,5 - 60 giây)"),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để tiếp tục"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Token không hợp lệ"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa"),

    // 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    PLACEMENT_TEST_NOT_AVAILABLE(HttpStatus.NOT_FOUND, "Hiện chưa có bài kiểm tra xếp lớp"),
    LEARNING_PATH_NOT_FOUND(HttpStatus.NOT_FOUND, "Bạn chưa có lộ trình học"),
    NO_LESSONS_FOR_PATH(HttpStatus.NOT_FOUND, "Chưa có bài học phù hợp với trình độ và mục tiêu này"),
    WORD_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy từ trong từ điển"),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ"),

    // 409
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột hoặc vi phạm ràng buộc"),
    ATTEMPT_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "Bài làm đã được nộp"),
    ATTEMPT_EXPIRED(HttpStatus.CONFLICT, "Đã hết thời gian làm bài"),
    ATTEMPT_NOT_SUBMITTED(HttpStatus.CONFLICT, "Bài làm chưa được nộp"),
    SELF_MODIFICATION_NOT_ALLOWED(HttpStatus.CONFLICT, "Không thể tự khóa hoặc tự gỡ quyền ADMIN của chính mình"),
    LESSON_ALREADY_IN_PATH(HttpStatus.CONFLICT, "Bài học đã có trong lộ trình"),
    ANSWER_ALREADY_CHECKED(HttpStatus.CONFLICT, "Câu này đã được chấm, hãy làm lại bài nếu muốn trả lời lại"),
    CONTENT_IN_USE(HttpStatus.CONFLICT, "Nội dung đã có học viên sử dụng nên không thể xóa, hãy ẩn (bỏ xuất bản) thay vì xóa"),
    TEST_HAS_ATTEMPTS(HttpStatus.CONFLICT, "Bài test đã có lượt làm nên không thể thay đổi danh sách câu hỏi, hãy nhân bản bài test"),

    // 413
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File vượt quá dung lượng cho phép"),

    // 502
    SPEECH_SCORING_FAILED(HttpStatus.BAD_GATEWAY, "Dịch vụ chấm phát âm đang gặp sự cố, vui lòng thử lại"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
