package com.englishhub.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Bật JPA auditing để tự điền CreatedAt / UpdatedAt. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
