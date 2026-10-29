package com.datecourse.domain.suggestion.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 욕설·음란어 찾기. 목록은 resources/moderation/badwords.txt, 예외(평범한 말)는 allow.txt.
 *
 * <p>비교 방법: 소문자로 바꾸고 전각 영문·숫자(ａ１)를 반각으로, 한글·자음·모음·영문·숫자·띄어쓰기만 남긴다(기호 끼워 넣기를 무시).
 * 띄어쓴 단어마다 보고, <b>한 글자 단어는 이웃 단어와 붙여서도</b> 본다(한 글자씩 띄어 쓴 우회).
 * 띄어쓰기를 모두 지우면 앞 단어의 끝과 뒷 단어의 처음이 이어져 멀쩡한 문장이 걸려서 이렇게 한다.
 * 숫자를 지운 글자로도 한 번 더 본다(숫자 끼워 넣기). 예외 말(allow.txt)은 검사 전에 지운다.
 * 실제 예시는 ProfanityFilterTests 에만 둔다 (문서·주석·커밋 메시지에는 목록의 단어를 쓰지 않는다).
 *
 * <p>목록을 고치면 ProfanityFilterTests 를 돌린다. 걸리면 30분 이용 제한이라 오탐이 없게 다듬은 목록만 쓴다.
 *
 * <p>평범한 말에도 들어 있는 애매한 말(mask.txt)은 제한하지 않고 {@link #mask} 로 * 처럼 가리기만 한다.
 */
@Component
public class ProfanityFilter {

    private static final Pattern NOT_ALLOWED = Pattern.compile("[^0-9a-z가-힣ㄱ-ㅎㅏ-ㅣ ]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern DIGITS = Pattern.compile("[0-9]");

    private final List<String> words;
    private final List<String> allowed;
    private final List<String> masked;

    public ProfanityFilter() {
        this(read("moderation/badwords.txt"), read("moderation/allow.txt"), read("moderation/mask.txt"));
    }

    ProfanityFilter(List<String> words, List<String> allowed) {
        this(words, allowed, List.of());
    }

    ProfanityFilter(List<String> words, List<String> allowed, List<String> masked) {
        this.masked = masked.stream().map(ProfanityFilter::compact).filter(w -> !w.isEmpty()).distinct().toList();
        this.words = words.stream().map(ProfanityFilter::compact).filter(w -> !w.isEmpty()).distinct().toList();
        // 긴 예외부터 지운다 (긴 예외 안에 짧은 예외가 들어 있을 수 있어서)
        this.allowed = allowed.stream().map(ProfanityFilter::compact).filter(w -> !w.isEmpty()).distinct()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    /** 처음 걸린 말 (없으면 비어 있다) */
    public Optional<String> find(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        for (String group : groups(text)) {
            for (String allow : allowed) {
                group = group.replace(allow, "_");
            }
            for (String form : List.of(group, DIGITS.matcher(group).replaceAll(""))) {
                for (String word : words) {
                    if (form.contains(word)) {
                        return Optional.of(word);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 애매한 말을 * 로 가린다. 원래 글자·띄어쓰기·기호는 그대로 두고 그 글자만 바꾼다.
     * 기호는 건너뛰어 보고(기호를 끼워 넣어도 가림), 띄어쓰기는 넘지 않는다.
     */
    public String mask(String text) {
        if (text == null || text.isEmpty() || masked.isEmpty()) {
            return text;
        }
        // 비교용 글자와 그 글자가 원래 글의 몇 번째인지 (띄어쓰기는 -1 로 남겨 단어를 끊는다)
        StringBuilder view = new StringBuilder();
        List<Integer> origin = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            }
            c = Character.toLowerCase(c);
            if (Character.isWhitespace(c)) {
                view.append(' ');
                origin.add(-1);
            } else if (!NOT_ALLOWED.matcher(String.valueOf(c)).matches()) {
                view.append(c);
                origin.add(i);
            }
        }
        char[] out = text.toCharArray();
        String haystack = view.toString();
        for (String word : masked) {
            for (int at = haystack.indexOf(word); at >= 0; at = haystack.indexOf(word, at + 1)) {
                for (int k = at; k < at + word.length(); k++) {
                    out[origin.get(k)] = '*';
                }
            }
        }
        return new String(out);
    }

    /** 띄어쓴 단어들 + 한 글자 단어를 이웃과 붙인 묶음 */
    static List<String> groups(String text) {
        String[] parts = normalize(text).trim().split(" ");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        List<String> tokens = List.copyOf(out);
        int i = 0;
        while (i < tokens.size()) {
            int j = i;
            while (j + 1 < tokens.size() && (tokens.get(j).length() == 1 || tokens.get(j + 1).length() == 1)) {
                j++;
            }
            if (j > i) {
                out.add(String.join("", tokens.subList(i, j + 1)));
            }
            i = j + 1;
        }
        return out;
    }

    /** 소문자, 전각 → 반각, 한글·자모·영문·숫자·띄어쓰기만. (NFKC 는 겹자음 같은 자모를 다른 코드로 바꿔 버려서 쓰지 않는다) */
    static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            sb.append(c >= 0xFF01 && c <= 0xFF5E ? (char) (c - 0xFEE0) : c);
        }
        String lower = SPACES.matcher(sb.toString().toLowerCase(Locale.ROOT)).replaceAll(" ");
        return NOT_ALLOWED.matcher(lower).replaceAll("");
    }

    private static String compact(String word) {
        return normalize(word).replace(" ", "");
    }

    /** 한 줄에 하나, # 뒤는 설명 */
    private static List<String> read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            List<String> lines = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                int hash = line.indexOf('#');
                String word = (hash >= 0 ? line.substring(0, hash) : line).strip();
                if (!word.isEmpty()) {
                    lines.add(word);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("비속어 목록을 읽지 못했어요: " + path, e);
        }
    }
}
