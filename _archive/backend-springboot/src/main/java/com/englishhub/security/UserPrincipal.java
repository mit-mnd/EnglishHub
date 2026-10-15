package com.englishhub.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

/** Thông tin người dùng đã xác thực, dựng từ claim của access token (không query DB mỗi request). */
public record UserPrincipal(Long id, String email, Set<String> roles) {

    public List<GrantedAuthority> authorities() {
        return roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
