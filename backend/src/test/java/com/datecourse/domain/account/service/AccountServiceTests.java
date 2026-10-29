package com.datecourse.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.datecourse.domain.account.exception.AccountException;
import com.datecourse.domain.account.repository.AccountRepository;
import com.datecourse.domain.account.util.PasswordHasher;
import com.datecourse.domain.course.service.PlanService;
import com.datecourse.global.cache.JsonCache;

class AccountServiceTests {

    AccountRepository accounts = mock(AccountRepository.class);
    PlanService plans = mock(PlanService.class);
    JsonCache cache = mock(JsonCache.class);
    AccountService service = new AccountService(accounts, plans, cache);

    UUID me = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    AccountRepository.Row account = new AccountRepository.Row(1, "K7QM29XD", owner, PasswordHasher.hash("비밀번호123"));

    @Test
    void 비밀번호_해시는_원문을_두지_않고_맞는지만_본다() {
        String hash = PasswordHasher.hash("비밀번호123");
        assertThat(hash).startsWith("pbkdf2_sha256$").doesNotContain("비밀번호123");
        assertThat(PasswordHasher.matches("비밀번호123", hash)).isTrue();
        assertThat(PasswordHasher.matches("비밀번호124", hash)).isFalse();
        assertThat(PasswordHasher.matches("x", "이상한형식")).isFalse();
    }

    @Test
    void 코드를_만들면_헷갈리는_글자_없는_8자를_이_브라우저에_묶는다() {
        given(accounts.byVisitor(me)).willReturn(Optional.empty());
        given(accounts.insert(anyString(), eq(me), anyString())).willReturn(true);

        String code = service.create(me, "123456");

        assertThat(code).hasSize(8).matches("[" + AccountService.CODE_CHARS + "]+");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(accounts).insert(eq(code), eq(me), hash.capture());
        assertThat(PasswordHasher.matches("123456", hash.getValue())).isTrue();
    }

    @Test
    void 비밀번호가_짧거나_이미_코드가_있으면_안_만든다() {
        assertThatThrownBy(() -> service.create(me, "12345")).isInstanceOf(AccountException.class);
        given(accounts.byVisitor(me)).willReturn(Optional.of(account));
        assertThatThrownBy(() -> service.create(me, "123456")).hasMessageContaining("이미 코드가 있어요");
    }

    @Test
    void 로그인하면_이_브라우저의_일정을_계정으로_옮긴다() {
        given(accounts.byCode("K7QM29XD")).willReturn(Optional.of(account));
        given(accounts.byVisitor(me)).willReturn(Optional.empty());
        given(plans.moveAll(me, owner)).willReturn(2);

        AccountService.Login login = service.login(me, "k7qm-29xd", "비밀번호123", "1.2.3.4");

        assertThat(login.visitorId()).isEqualTo(owner);   // 쿠키를 계정의 id 로
        assertThat(login.moved()).isEqualTo(2);
        verify(accounts).touchLogin(1);
    }

    @Test
    void 이_브라우저가_다른_계정이면_일정을_섞지_않는다() {
        given(accounts.byCode("K7QM29XD")).willReturn(Optional.of(account));
        given(accounts.byVisitor(me)).willReturn(Optional.of(new AccountRepository.Row(2, "AAAA2222", me, "x")));

        assertThat(service.login(me, "K7QM29XD", "비밀번호123", "1.2.3.4").moved()).isZero();
        verify(plans, never()).moveAll(any(), any());
    }

    @Test
    void 틀리면_세고_다섯_번_틀린_코드는_잠근다() {
        given(accounts.byCode("K7QM29XD")).willReturn(Optional.of(account));
        assertThatThrownBy(() -> service.login(me, "K7QM29XD", "틀린비번", "1.2.3.4")).hasMessageContaining("맞지 않아요");
        verify(cache).increment(eq("account-fail:code:K7QM29XD"), any());
        verify(cache).increment(eq("account-fail:ip:1.2.3.4"), any());

        given(cache.counter("account-fail:code:K7QM29XD")).willReturn((long) AccountService.CODE_FAIL_LIMIT);
        assertThatThrownBy(() -> service.login(me, "K7QM29XD", "비밀번호123", "1.2.3.4")).hasMessageContaining("15분 뒤");
        verify(plans, never()).moveAll(any(), any());
    }

    @Test
    void 코드를_지우려면_비밀번호가_맞아야_한다() {
        given(accounts.byVisitor(owner)).willReturn(Optional.of(account));
        assertThatThrownBy(() -> service.delete(owner, "틀린비번", "1.2.3.4")).hasMessageContaining("비밀번호가 맞지 않아요");
        verify(accounts, never()).delete(1);

        service.delete(owner, "비밀번호123", "1.2.3.4");
        verify(accounts).delete(1);
        assertThatThrownBy(() -> service.delete(me, "비밀번호123", "1.2.3.4")).hasMessageContaining("코드가 없어요");
    }

    @Test
    void 없는_코드도_같은_말로_거절한다() {
        assertThatThrownBy(() -> service.login(me, "ZZZZ9999", "비밀번호123", "1.2.3.4")).hasMessageContaining("맞지 않아요");
        verify(cache).increment(startsWith("account-fail:code:"), any());
    }
}
