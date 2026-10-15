package com.englishhub.integration.mail;

/** Cổng gửi email; bản mặc định chỉ ghi log, sau này thay bằng SMTP/SendGrid mà không sửa nghiệp vụ. */
public interface MailService {

    void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);
}
