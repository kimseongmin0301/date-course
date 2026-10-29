package com.datecourse.domain.account.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.account.dto.AccountCreateRequest;
import com.datecourse.domain.account.dto.AccountLoginRequest;
import com.datecourse.domain.account.dto.AccountLoginResponse;
import com.datecourse.domain.account.dto.AccountStatus;
import com.datecourse.domain.account.service.AccountService;
import com.datecourse.global.util.VisitorCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 다른 기기에서도 내 일정 보기: 코드 만들기·로그인·로그아웃 (쿠키 dc_vid 를 계정의 방문자 id 로) */
@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/api/account")
    public AccountStatus status(HttpServletRequest request) {
        return VisitorCookie.read(request).flatMap(accountService::codeOf)
                .map(code -> new AccountStatus(true, display(code)))
                .orElse(new AccountStatus(false, null));
    }

    /** 코드 만들기: 이 브라우저(없으면 쿠키를 만들어)에 코드 + 비밀번호를 묶는다 */
    @PostMapping("/api/account")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountStatus create(@RequestBody(required = false) AccountCreateRequest body,
                                HttpServletRequest request, HttpServletResponse response) {
        UUID visitor = VisitorCookie.ensure(request, response);
        return new AccountStatus(true, display(accountService.create(visitor, body == null ? null : body.password())));
    }

    /** 로그인: 이 브라우저의 일정을 계정으로 옮기고 쿠키를 계정 것으로 바꾼다 */
    @PostMapping("/api/account/login")
    public AccountLoginResponse login(@RequestBody(required = false) AccountLoginRequest body,
                                      HttpServletRequest request, HttpServletResponse response) {
        AccountService.Login login = accountService.login(VisitorCookie.read(request).orElse(null),
                body == null ? null : body.code(), body == null ? null : body.password(), VisitorCookie.clientIp(request));
        VisitorCookie.set(request, response, login.visitorId());
        return new AccountLoginResponse(display(login.code()), login.moved());
    }

    /** 로그아웃: 이 브라우저는 새 방문자로 (계정의 일정은 그대로, 이 브라우저에서만 안 보임) */
    @PostMapping("/api/account/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        VisitorCookie.set(request, response, UUID.randomUUID());
    }

    /** "K7QM29XD" → "K7QM-29XD" */
    static String display(String code) {
        return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }
}
