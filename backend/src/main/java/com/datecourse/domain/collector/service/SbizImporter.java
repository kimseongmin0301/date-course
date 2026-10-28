package com.datecourse.domain.collector.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.collector.util.CsvLine;
import com.datecourse.domain.collector.util.PlaceMatcher;
import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.place.entity.PlaceCategory;

/**
 * 상가(상권)정보 CSV 를 넣는다. 시도 하나에 십만 단위라 PlaceUpsertService 처럼 한 줄씩 DB 에 묻지 않고,
 * 시도의 기존 장소를 메모리에 올려 같은 규칙(PlaceMatcher)으로 비교한 뒤 묶어서(JDBC batch) 넣는다.
 */
@Service
public class SbizImporter {

    private static final Logger log = LoggerFactory.getLogger(SbizImporter.class);
    private static final int CHUNK = 5000;
    private static final int ID_BLOCK = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SbizImporter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** path 가 폴더면 그 안의 CSV 전부, only 가 있으면 파일 이름에 그 말이 든 것만 (예: 서울) */
    public Map<PlaceUpsertService.Result, Integer> importPath(Path path, String only) {
        List<Path> files = csvFiles(path, only);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("CSV 파일이 없습니다: " + path.toAbsolutePath() + (only == null ? "" : " (" + only + ")"));
        }
        Map<PlaceUpsertService.Result, Integer> total = new EnumMap<>(PlaceUpsertService.Result.class);
        for (Path file : files) {
            importFile(file).forEach((k, v) -> total.merge(k, v, Integer::sum));
        }
        log.info("상가정보 수집 끝 (파일 {}개): {}", files.size(), total);
        return total;
    }

    private static List<Path> csvFiles(Path path, String only) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        try (Stream<Path> list = Files.list(path)) {
            return list.filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .filter(p -> only == null || p.getFileName().toString().contains(only))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Map<PlaceUpsertService.Result, Integer> importFile(Path file) {
        long started = System.currentTimeMillis();
        Map<String, PlaceCandidate> byId = new LinkedHashMap<>();
        int lines = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SbizRowMapper mapper = new SbizRowMapper(CsvLine.parse(reader.readLine()));
            String line;
            while ((line = reader.readLine()) != null) {
                lines++;
                mapper.toCandidate(CsvLine.parse(line)).ifPresent(c -> byId.putIfAbsent(c.externalId(), c));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<PlaceCandidate> candidates = new ArrayList<>(byId.values());
        log.info("{}: {}줄 중 데이트 업종 {}곳", file.getFileName(), lines, candidates.size());

        Set<String> seen = knownSbizIds(candidates);
        Map<String, List<PlaceMatcher.Known>> index = loadIndex(candidates);
        IdAllocator ids = new IdAllocator();
        Map<PlaceUpsertService.Result, Integer> counts = new EnumMap<>(PlaceUpsertService.Result.class);
        for (int from = 0; from < candidates.size(); from += CHUNK) {
            List<PlaceCandidate> chunk = candidates.subList(from, Math.min(from + CHUNK, candidates.size()));
            tx.executeWithoutResult(status -> processChunk(chunk, seen, index, ids, counts));
            log.info("{}: {}/{} {}", file.getFileName(), Math.min(from + CHUNK, candidates.size()), candidates.size(), counts);
        }
        log.info("{}: 끝 ({}초)", file.getFileName(), (System.currentTimeMillis() - started) / 1000);
        return counts;
    }

    /** 이미 넣은 상가업소번호 (다시 받은 분기 파일이면 대부분) */
    private Set<String> knownSbizIds(List<PlaceCandidate> candidates) {
        Set<String> seen = new HashSet<>();
        for (int from = 0; from < candidates.size(); from += 10_000) {
            String ids = String.join(",", candidates.subList(from, Math.min(from + 10_000, candidates.size()))
                    .stream().map(PlaceCandidate::externalId).toList());
            seen.addAll(jdbc.queryForList(
                    "SELECT external_id FROM place_source WHERE source = 'sbiz' AND external_id = ANY (string_to_array(?, ','))",
                    String.class, ids));
        }
        return seen;
    }

    /** 이 파일에 나오는 시도의 기존 장소: 정리한 이름 → 장소들 */
    private Map<String, List<PlaceMatcher.Known>> loadIndex(List<PlaceCandidate> candidates) {
        Map<String, List<PlaceMatcher.Known>> index = new HashMap<>();
        Set<String> sidos = new HashSet<>();
        candidates.forEach(c -> sidos.add(c.sido()));
        for (String sido : sidos) {
            jdbc.query("SELECT id, normalized_name, branch, category, lat, lng FROM place WHERE sido = ?", rs -> {
                index.computeIfAbsent(rs.getString("normalized_name"), k -> new ArrayList<>())
                        .add(new PlaceMatcher.Known(rs.getLong("id"), rs.getString("branch"),
                                PlaceCategory.valueOf(rs.getString("category").toUpperCase()), rs.getDouble("lat"), rs.getDouble("lng")));
            }, sido);
        }
        return index;
    }

    private void processChunk(List<PlaceCandidate> chunk, Set<String> seen, Map<String, List<PlaceMatcher.Known>> index,
                              IdAllocator ids, Map<PlaceUpsertService.Result, Integer> counts) {
        List<Object[]> touch = new ArrayList<>();
        List<Object[]> newPlaces = new ArrayList<>();
        List<Object[]> newSources = new ArrayList<>();
        List<Object[]> fills = new ArrayList<>();
        List<Object[]> mergeCandidates = new ArrayList<>();

        for (PlaceCandidate c : chunk) {
            if (!seen.add(c.externalId())) {
                touch.add(new Object[] {cut(c.rawName(), 200), c.externalId()});
                counts.merge(PlaceUpsertService.Result.SEEN_AGAIN, 1, Integer::sum);
                continue;
            }
            String normalized = PlaceNames.normalize(c.name());
            List<PlaceMatcher.Known> sameName = index.computeIfAbsent(normalized, k -> new ArrayList<>());
            PlaceMatcher.Match match = PlaceMatcher.match(sameName, c.branch(), c.category(), c.lat(), c.lng());
            if (match.sameId() != null) {
                newSources.add(new Object[] {match.sameId(), c.externalId(), cut(c.rawName(), 200)});
                fills.add(new Object[] {c.dong(), cut(c.roadAddress(), 300), cut(c.lotAddress(), 300), match.sameId()});
                counts.merge(PlaceUpsertService.Result.MERGED, 1, Integer::sum);
                continue;
            }
            long id = ids.next();
            newPlaces.add(new Object[] {id, cut(c.name(), 200), cut(c.branch(), 100), cut(normalized, 200),
                    c.category().name().toLowerCase(), cut(c.detailCategory(), 100), c.lat(), c.lng(),
                    c.sido(), c.sigungu(), c.dong(), cut(c.roadAddress(), 300), cut(c.lotAddress(), 300)});
            newSources.add(new Object[] {id, c.externalId(), cut(c.rawName(), 200)});
            sameName.add(new PlaceMatcher.Known(id, c.branch(), c.category(), c.lat(), c.lng()));
            if (match.nearId() != null) {
                mergeCandidates.add(new Object[] {Math.min(id, match.nearId()), Math.max(id, match.nearId()),
                        (int) Math.round(match.nearDistance())});
                counts.merge(PlaceUpsertService.Result.CREATED_WITH_CANDIDATE, 1, Integer::sum);
            } else {
                counts.merge(PlaceUpsertService.Result.CREATED, 1, Integer::sum);
            }
        }

        jdbc.batchUpdate("UPDATE place_source SET last_seen_at = now(), raw_name = ? WHERE source = 'sbiz' AND external_id = ?", touch);
        jdbc.batchUpdate("""
                INSERT INTO place (id, name, branch, normalized_name, category, detail_category, lat, lng,
                                   sido, sigungu, dong, road_address, lot_address)
                OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", newPlaces);
        jdbc.batchUpdate("INSERT INTO place_source (place_id, source, external_id, raw_name) VALUES (?, 'sbiz', ?, ?)", newSources);
        jdbc.batchUpdate("""
                UPDATE place SET dong = COALESCE(dong, ?), road_address = COALESCE(road_address, ?),
                                 lot_address = COALESCE(lot_address, ?), closed_at = NULL, updated_at = now()
                WHERE id = ?""", fills);
        jdbc.batchUpdate("""
                INSERT INTO place_merge_candidate (place_a_id, place_b_id, distance_m) VALUES (?, ?, ?)
                ON CONFLICT (place_a_id, place_b_id) DO NOTHING""", mergeCandidates);
    }

    private static String cut(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

    /** place.id 를 시퀀스에서 1,000개씩 미리 받아 둔다 (묶어서 넣을 때 출처·후보가 새 장소 id 를 알아야 해서) */
    private class IdAllocator {
        private Iterator<Long> block = List.<Long>of().iterator();

        long next() {
            if (!block.hasNext()) {
                block = jdbc.queryForList("SELECT nextval(pg_get_serial_sequence('place', 'id')) FROM generate_series(1, ?)",
                        Long.class, ID_BLOCK).iterator();
            }
            return block.next();
        }
    }
}
