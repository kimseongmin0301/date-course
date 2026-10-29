package com.datecourse.domain.account.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 비밀번호 해시 (PBKDF2-HMAC-SHA256, 힐링룸 관리자 로그인과 같은 형식). 원문은 어디에도 두지 않는다.
 * 저장 형식: pbkdf2_sha256$반복횟수$소금(Base64)$해시(Base64)
 */
public final class PasswordHasher {

    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] digest = derive(password, salt, ITERATIONS, HASH_BYTES);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2_sha256$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(digest);
    }

    /** 맞으면 true. 형식이 틀려도 false (예외를 내지 않음) */
    public static boolean matches(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, iterations, expected.length));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int bytes) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bytes * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 를 쓸 수 없음", e);
        }
    }

    /** 코드가 없을 때도 같은 시간이 걸리게 (걸린 시간으로 코드가 있는지 알 수 없게) */
    public static void burn(String password) {
        derive(password == null ? "" : password, "date-course".getBytes(StandardCharsets.UTF_8), ITERATIONS, HASH_BYTES);
    }
}
