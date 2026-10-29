package com.datecourse.global.util;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 방문자 id 쿠키 (로그인 없음). 브라우저마다 무작위 UUID 하나를 쿠키 dc_vid 에 1년 두고, 내 일정 저장을 이 id 로 구분한다.
 * HttpOnly(화면 스크립트가 못 읽음)·SameSite=Lax, https 로 들어오면 Secure. 쿠키에는 id 만 두고 일정은 서버(saved_plan)에 둔다
 * (쿠키는 4KB 까지라 장소 목록을 담을 수 없고, 요청마다 따라가므로 작게 둔다).
 */
public final class VisitorCookie {

    public static final String NAME = "dc_vid";
    static final Duration MAX_AGE = Duration.ofDays(365);

    private VisitorCookie() {
    }

    public static Optional<UUID> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                try {
                    return Optional.of(UUID.fromString(cookie.getValue()));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** 있으면 그 id, 없으면 새로 만들어 쿠키를 내려보낸다. 있어도 다시 내려보내 1년을 늘린다 */
    public static UUID ensure(HttpServletRequest request, HttpServletResponse response) {
        UUID id = read(request).orElseGet(UUID::randomUUID);
        set(request, response, id);
        return id;
    }

    /** 쿠키를 이 id 로 바꾼다 (다른 기기에서 로그인·로그아웃할 때) */
    public static void set(HttpServletRequest request, HttpServletResponse response, UUID id) {
        ResponseCookie cookie = ResponseCookie.from(NAME, id.toString())
                .path("/")
                .httpOnly(true)
                .secure(isHttps(request))
                .sameSite("Lax")
                .maxAge(MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 요청한 사람의 IP. 앱은 127.0.0.1 에만 열려 있어 nginx 가 넣은 X-Real-IP 를 믿되, 직접 온 요청(로컬 개발)이면 그 주소 */
    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String real = request.getHeader("X-Real-IP");
        boolean fromProxy = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        return fromProxy && real != null && !real.isBlank() ? real.strip() : remote;
    }

    /** nginx 뒤에서는 X-Forwarded-Proto 로 안다 (앱은 127.0.0.1 에만 열려 있어 nginx 만 이 헤더를 보낼 수 있음) */
    static boolean isHttps(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
