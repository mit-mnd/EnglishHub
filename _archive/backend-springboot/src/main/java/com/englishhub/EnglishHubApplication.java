package com.englishhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Xác thực hoàn toàn bằng JWT -> tắt user in-memory mặc định (mật khẩu ngẫu nhiên in ra log) của Spring Boot
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class EnglishHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnglishHubApplication.class, args);
    }
}
