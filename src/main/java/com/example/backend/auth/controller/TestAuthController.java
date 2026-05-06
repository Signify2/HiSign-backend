package com.example.backend.auth.controller;


import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.auth.config.CookieProperties;
import com.example.backend.auth.util.CookieUtil;
import com.example.backend.auth.util.JwtUtil;
import com.example.backend.member.entity.Member;
import com.example.backend.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestAuthController {
    private final MemberRepository memberRepository;
    private final CookieUtil cookieUtil;
    private final CookieProperties cookieProperties;

    @Value("${custom.jwt.secret}")
    private String secretKey;

    @GetMapping("/login")
    public ResponseEntity<?> testLogin(@RequestParam(required = false) String user) {
        boolean hasSelectedUser = user != null && !user.trim().isEmpty();
        Member member = !hasSelectedUser
                ? memberRepository.findAll()
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("테스트용 멤버가 없습니다."))
                : memberRepository.findByUniqueIdOrEmail(user, user)
                .orElseThrow(() -> new RuntimeException("선택한 테스트 사용자를 찾을 수 없습니다: " + user));
        String accessToken = JwtUtil.createToken(member, secretKey,
                cookieProperties.getAccessTokenMaxAge());
        String refreshToken = JwtUtil.createRefreshToken(member.getUniqueId(), secretKey,
                cookieProperties.getRefreshTokenMaxAge());
        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie(accessToken);
        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(refreshToken);
        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "테스트 로그인 성공");
        responseBody.put("name", member.getName());
        responseBody.put("uniqueId", member.getUniqueId());
        responseBody.put("selectedUser", hasSelectedUser ? user : "first-user");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(responseBody);
    }
}

