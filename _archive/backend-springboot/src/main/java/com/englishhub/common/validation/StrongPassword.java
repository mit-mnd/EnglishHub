package com.englishhub.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chính sách mật khẩu: 8-72 ký tự (giới hạn 72 byte của BCrypt), không khoảng trắng,
 * có ít nhất một chữ cái và một chữ số.
 */
@Documented
@NotBlank
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,72}$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Mật khẩu 8-72 ký tự, không chứa khoảng trắng, gồm ít nhất 1 chữ cái và 1 chữ số";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
