package com.datecourse.domain.account.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.datecourse.domain.account.exception.AccountException;
import com.datecourse.domain.account.repository.AccountRepository;
import com.datecourse.domain.account.util.PasswordHasher;
import com.datecourse.domain.course.service.PlanService;
import com.datecourse.global.cache.JsonCache;

/**
 * 다른 기기에서도 내 일정 보기 (고를 때만). 로그인 없이 쓰다가, 원하면 비밀번호를 정해 코드를 받는다.
 *   - 만들기: 지금 브라우저의 방문자 id 에 코드 + 비밀번호(해시)를 묶는다. 저장한 일정은 그대로.
 *   - 로그인: 코드 + 비밀번호가 맞으면, 그 브라우저에 저장돼 있던 일정을 계정으로 옮기고(같은 코스는 나중에 저장한 쪽)
 *     컨트롤러가 쿠키를 계정의 방문자 id 로 바꾼다. 이미 다른 계정으로 로그인한 브라우저면 옮기지 않고 바꾸기만.
 *   - 틀리면 코드마다 5번, IP 마다 20번에서 15분 잠금 (Redis 가 안 되면 잠금 없이, 해시가 느려서 대입이 어려움).
 * 이메일·전화를 모으지 않으므로 코드나 비밀번호를 잊으면 되찾을 수 없다.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 64;
    static final int CODE_LENGTH = 8;
    /** 헷갈리는 0·O·1·I·L 을 뺀 글자 */
    static final String CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int CODE_FAIL_LIMIT = 5;
    static final int IP_FAIL_LIMIT = 20;
    static final Duration LOCK = Duration.ofMinutes(15);

    private final AccountRepository accounts;
    private final PlanService plans;
    private final JsonCache cache;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accounts, PlanService plans, JsonCache cache) {
        this.accounts = accounts;
        this.plans = plans;
        this.cache = cache;
    }

    /** 이 브라우저(방문자 id)의 코드. 없으면 비어 있다 */
    public Optional<String> codeOf(UUID visitor) {
        return accounts.byVisitor(visitor).map(AccountRepository.Row::code);
    }

    /** 코드 만들기: 비밀번호를 정하면 이 방문자 id 에 코드를 묶어 돌려준다 */
    public String create(UUID visitor, String password) {
        checkPassword(password);
        if (accounts.byVisitor(visitor).isPresent()) {
            throw AccountException.alreadyLinked();
        }
        String hash = PasswordHasher.hash(password);
        for (int i = 0; i < 5; i++) {
            String code = newCode();
            if (accounts.insert(code, visitor, hash)) {
                return code;
            }
        }
        throw new IllegalStateException("코드를 만들지 못함");
    }

    /** 로그인 결과: 쿠키에 넣을 방문자 id, 코드, 이 브라우저에서 옮겨 온 일정 수 */
    public record Login(UUID visitorId, String code, int moved) {
    }

    @Transactional
    public Login login(UUID current, String rawCode, String password, String ip) {
        String code = normalize(rawCode);
        String codeKey = "account-fail:code:" + code;
        String ipKey = "account-fail:ip:" + (ip == null ? "-" : ip);
        if (cache.counter(codeKey) >= CODE_FAIL_LIMIT || cache.counter(ipKey) >= IP_FAIL_LIMIT) {
            throw AccountException.locked();
        }
        Optional<AccountRepository.Row> account = code.length() == CODE_LENGTH ? accounts.byCode(code) : Optional.empty();
        boolean ok;
        if (account.isPresent()) {
            ok = PasswordHasher.matches(password, account.get().passwordHash());
        } else {
            PasswordHasher.burn(password);   // 코드가 없어도 같은 시간이 걸리게
            ok = false;
        }
        if (!ok) {
            cache.increment(codeKey, LOCK);
            cache.increment(ipKey, LOCK);
            throw AccountException.wrong();
        }
        AccountRepository.Row row = account.get();
        int moved = 0;
        // 로그인 전 이 브라우저에 저장한 일정은 계정으로 옮긴다 (이 브라우저가 다른 계정이면 섞지 않는다)
        if (current != null && !current.equals(row.visitorId()) && accounts.byVisitor(current).isEmpty()) {
            moved = plans.moveAll(current, row.visitorId());
        }
        accounts.touchLogin(row.id());
        cache.evict(codeKey);
        log.info("내 일정 계정 로그인 (옮긴 일정 {}개)", moved);
        return new Login(row.visitorId(), row.code(), moved);
    }

    /** "K7QM-29xd" → "K7QM29XD" (띄어쓰기·줄표를 빼고 대문자로) */
    static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[^0-9A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw AccountException.badPassword(PASSWORD_MIN, PASSWORD_MAX);
        }
    }
}
