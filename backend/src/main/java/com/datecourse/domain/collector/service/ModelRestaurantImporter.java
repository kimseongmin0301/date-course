package com.datecourse.domain.collector.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.datecourse.domain.collector.util.AddressKey;
import com.datecourse.domain.collector.util.CsvLine;
import com.datecourse.domain.collector.util.PlaceNames;

/**
 * 모범음식점 (행정안전부 전국모범음식점표준데이터 CSV) → place_badge(model_restaurant).
 * 원본에는 좌표가 없어서 주소(AddressKey: 시도를 뗀 "시군구 길 번호")와 이름으로 우리 장소(식당·카페·술집)에 맞춘다.
 *  - 지금 유효한 것만: 영업상태명 = 영업, 지정취소일자 없음 (파일에는 폐업·취소된 옛 기록이 섞여 있음)
 *  - 도로명 주소 → 지번 주소 순으로 같은 주소의 장소를 찾고, 이름이 같거나 한쪽이 다른 쪽을 품으면 같은 가게
 * 넣을 때마다 모범음식점 표시를 모두 지우고 다시 넣는다 (지정 취소·폐업이 반영되게).
 */
@Service
public class ModelRestaurantImporter {

    private static final Logger log = LoggerFactory.getLogger(ModelRestaurantImporter.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public ModelRestaurantImporter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** 원본 한 곳 */
    public record Designation(String id, String name, String roadAddress, String lotAddress, LocalDate designatedOn) {
    }

    record Known(long id, String normalizedName) {
    }

    /** 맞춘 장소 수 */
    public int importPath(Path path) {
        Path file = csvFile(path);
        List<Designation> designations = read(file);
        log.info("모범음식점 {}: 지금 유효한 곳 {}", file.getFileName(), designations.size());

        Map<String, List<Known>> byAddress = new HashMap<>();
        jdbc.query("""
                SELECT id, normalized_name, road_address, lot_address FROM place
                WHERE closed_at IS NULL AND category IN ('restaurant', 'cafe', 'bar')""", rs -> {
            Known k = new Known(rs.getLong("id"), rs.getString("normalized_name"));
            for (String address : new String[] {rs.getString("road_address"), rs.getString("lot_address")}) {
                String key = AddressKey.of(address);
                if (key != null) {
                    byAddress.computeIfAbsent(key, x -> new ArrayList<>()).add(k);
                }
            }
        });

        Map<Long, Designation> matched = new LinkedHashMap<>();
        for (Designation d : designations) {
            Long placeId = match(d, byAddress);
            if (placeId != null) {
                matched.putIfAbsent(placeId, d);
            }
        }
        List<Object[]> rows = new ArrayList<>();
        matched.forEach((placeId, d) -> rows.add(new Object[] {placeId, d.id(), d.designatedOn() == null ? null : java.sql.Date.valueOf(d.designatedOn())}));
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM place_badge WHERE kind = 'model_restaurant'");
            jdbc.batchUpdate("""
                    INSERT INTO place_badge (place_id, kind, source_id, designated_on) VALUES (?, 'model_restaurant', ?, ?)
                    ON CONFLICT (place_id, kind) DO NOTHING""", rows);
        });
        log.info("모범음식점: {}곳 중 {}곳을 우리 장소에 맞춤 ({}%)", designations.size(), matched.size(),
                designations.isEmpty() ? 0 : matched.size() * 100 / designations.size());
        return matched.size();
    }

    /** 같은 주소에 이름이 같거나(먼저) 한쪽이 다른 쪽을 품는 장소. 없으면 null */
    static Long match(Designation d, Map<String, List<Known>> byAddress) {
        String name = PlaceNames.normalize(PlaceNames.splitBranch(d.name()).name());
        String full = PlaceNames.normalize(d.name());
        if (name.length() < 2) {
            return null;
        }
        Long similar = null;
        for (String address : new String[] {d.roadAddress(), d.lotAddress()}) {
            String key = AddressKey.of(address);
            if (key == null) {
                continue;
            }
            for (Known k : byAddress.getOrDefault(key, List.of())) {
                String other = k.normalizedName();
                if (other.equals(name) || other.equals(full)) {
                    return k.id();
                }
                if (similar == null && other.length() >= 2 && (full.contains(other) || other.contains(name))) {
                    similar = k.id();
                }
            }
        }
        return similar;
    }

    static List<Designation> read(Path file) {
        String text = decode(file);
        List<Designation> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(text))) {
            Map<String, Integer> col = header(CsvLine.parse(reader.readLine()));
            String record;
            while ((record = nextRecord(reader)) != null) {
                String[] f = CsvLine.parse(record);
                if (f.length < col.size()) {
                    continue;
                }
                if (!"영업".equals(f[col.get("영업상태명")].strip()) || !f[col.get("지정취소일자")].isBlank()) {
                    continue;
                }
                String re = f[col.get("재지정일자")].strip();
                String first = f[col.get("지정일자")].strip();
                result.add(new Designation(f[col.get("관리번호")].strip(), f[col.get("업소명")].strip(),
                        f[col.get("도로명주소")], f[col.get("소재지주소")], date(re.isEmpty() ? first : re)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private static final List<String> REQUIRED = List.of("관리번호", "업소명", "도로명주소", "소재지주소", "영업상태명", "지정일자", "지정취소일자", "재지정일자");

    private static Map<String, Integer> header(String[] names) {
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            col.put(names[i].replace("﻿", "").strip(), i);
        }
        for (String required : REQUIRED) {
            if (!col.containsKey(required)) {
                throw new IllegalArgumentException("모범음식점 CSV 에 '" + required + "' 열이 없어요. 머리줄: " + col.keySet());
            }
        }
        return col;
    }

    /** 큰따옴표 안에 줄바꿈이 든 칸이 있어서, 따옴표 짝이 맞을 때까지 다음 줄을 붙인다 */
    private static String nextRecord(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        StringBuilder record = new StringBuilder(line);
        while (line != null && record.chars().filter(c -> c == '"').count() % 2 == 1) {
            line = reader.readLine();
            if (line != null) {
                record.append(' ').append(line);
            }
        }
        return record.toString();
    }

    private static LocalDate date(String v) {
        try {
            return v == null || v.isBlank() ? null : LocalDate.parse(v.strip().substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 공공데이터포털 파일은 CP949 인 경우가 많아서, UTF-8 로 읽어 보고 안 되면 CP949 */
    private static String decode(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                return new String(bytes, Charset.forName("MS949"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path csvFile(Path path) {
        if (Files.isRegularFile(path)) {
            return path;
        }
        try (Stream<Path> list = Files.list(path)) {
            return list.filter(p -> p.getFileName().toString().endsWith(".csv")).sorted().reduce((a, b) -> b)
                    .orElseThrow(() -> new IllegalArgumentException("CSV 파일이 없습니다: " + path.toAbsolutePath()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
