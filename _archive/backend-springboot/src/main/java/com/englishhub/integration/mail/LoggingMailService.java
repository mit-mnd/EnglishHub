package com.englishhub.integration.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Bản dùng khi phát triển: in nội dung email ra log thay vì gửi thật. */
@Slf4j
@Service
public class LoggingMailService implements MailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        log.info("""
                [DEV MAIL] To: {} ({})
                Subject: Đặt lại mật khẩu EnglishHub
                Link (hết hạn sau 30 phút): {}""", toEmail, fullName, resetLink);
    }
}
