package com.datecourse.domain.course.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.collector.util.Regions;
import com.datecourse.domain.course.dto.CustomPlaceRequest;
import com.datecourse.domain.course.dto.PlaceResponse;
import com.datecourse.domain.course.dto.PlanResponse;
import com.datecourse.domain.course.dto.PlanSaveRequest;
import com.datecourse.domain.course.dto.PlanStepRequest;
import com.datecourse.domain.course.dto.PlanStepResponse;
import com.datecourse.domain.course.dto.PlanSummary;
import com.datecourse.domain.course.exception.CourseException;
import com.datecourse.domain.course.repository.CourseQueryRepository;
import com.datecourse.domain.course.repository.PlanRepository;
import com.datecourse.domain.course.util.CoursePlanner;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 내 일정 저장 (로그인 없이 방문자 쿠키 id 로). 방문자마다 코스 하나에 일정 하나, 최대 MAX_PLANS 개.
 * 장소는 id 만 저장하고 읽을 때 지금 DB 의 장소와 이어 붙인다 (화면이 보낸 이름·주소를 믿지 않음).
 * 데이터에 없는 가게는 이름·주소·좌표를 그대로 둔다 (custom: 그 사람 일정에만 보이고 다른 곳에 쓰지 않음).
 */
@Service
public class PlanService {

    public static final int MAX_STEPS = 15;
    public static final int MAX_PLANS = 50;
    public static final int STAY_MAX = 720;
    /** 고정 도착 시간은 다음날 낮까지 (분) */
    public static final int FIXED_MAX = 2 * 1440 - 1;
    private static final Set<String> ROLES = Set.of("spot", "cafe", "meal", "mine");
    private static final Set<String> ORIGINS = Set.of("course", "swap", "added");
    public static final int CUSTOM_NAME_MAX = 30;
    public static final int CUSTOM_ADDRESS_MAX = 100;
    /** 직접 넣은 가게의 장소 id (DB 장소가 아님) */
    public static final long CUSTOM_ID = 0;

    private final PlanRepository plans;
    private final CourseQueryRepository places;
    private final JsonMapper json;

    public PlanService(PlanRepository plans, CourseQueryRepository places, JsonMapper json) {
        this.plans = plans;
        this.places = places;
        this.json = json;
    }

    /** DB 에 두는 단계 모양. custom 이 있으면 placeId 는 null */
    record Stored(Long placeId, String role, int stay, Integer fixed, String origin, Integer courseOrder, Custom custom) {

        Stored(long placeId, String role, int stay, Integer fixed, String origin, Integer courseOrder) {
            this(placeId, role, stay, fixed, origin, courseOrder, null);
        }
    }

    /** 직접 넣은 가게 */
    record Custom(String name, String address, double lat, double lng) {
    }

    public List<PlanSummary> list(UUID visitor) {
        List<PlanRepository.Row> rows = plans.list(visitor, MAX_PLANS);
        Map<Long, CourseQueryRepository.Candidate> byId = placesOf(rows.stream().flatMap(r -> parse(r.stepsJson()).stream()).toList());
        return rows.stream().map(r -> {
            List<Stored> steps = parse(r.stepsJson());
            List<int[]> times = timeline(steps, byId, r.startMinute());
            int end = times.isEmpty() ? r.startMinute() : times.get(times.size() - 1)[1];
            return new PlanSummary(r.courseId(), r.title(), r.sido(), r.sigungu(), r.date() == null ? null : r.date().toString(),
                    clock(r.startMinute()), clock(end),
                    steps.stream().map(s -> s.custom() != null ? s.custom().name()
                            : byId.containsKey(s.placeId()) ? byId.get(s.placeId()).name() : "(없어진 장소)").toList(),
                    r.updatedAt().toString());
        }).toList();
    }

    public PlanResponse get(UUID visitor, long courseId) {
        return plans.find(visitor, courseId).map(this::toResponse).orElseThrow(CourseException::notFound);
    }

    public PlanResponse save(UUID visitor, long courseId, PlanSaveRequest req) {
        if (!plans.courseExists(courseId)) {
            throw CourseException.notFound();
        }
        if (req == null || req.steps() == null || req.steps().isEmpty() || req.steps().size() > MAX_STEPS) {
            throw CourseException.badRequest("일정에는 장소가 1~" + MAX_STEPS + "곳 있어야 해요.");
        }
        int start = minuteOf(req.start());
        LocalDate date = dateOf(req.date());
        List<Stored> steps = new ArrayList<>();
        for (PlanStepRequest s : req.steps()) {
            steps.add(validate(s));
        }
        Map<Long, CourseQueryRepository.Candidate> found = placesOf(steps);
        if (!found.keySet().containsAll(steps.stream().map(Stored::placeId).filter(Objects::nonNull).toList())) {
            throw CourseException.badRequest("없는 장소가 들어 있어요. 새로고침한 뒤 다시 저장해 주세요.");
        }
        if (plans.find(visitor, courseId).isEmpty() && plans.count(visitor) >= MAX_PLANS) {
            throw CourseException.badRequest("일정은 " + MAX_PLANS + "개까지 저장할 수 있어요. 내 저장에서 지난 일정을 지워 주세요.");
        }
        plans.upsert(visitor, courseId, date, start, json.writeValueAsString(steps));
        return get(visitor, courseId);
    }

    public void delete(UUID visitor, long courseId) {
        if (!plans.delete(visitor, courseId)) {
            throw CourseException.notFound();
        }
    }

    private Stored validate(PlanStepRequest s) {
        if (s != null && s.custom() != null) {
            return new Stored(null, "mine", stayOf(s), fixedOf(s), "added", null, custom(s.custom()));
        }
        if (s == null || s.placeId() == null || s.role() == null || !ROLES.contains(s.role())) {
            throw CourseException.badRequest("일정의 단계가 올바르지 않아요.");
        }
        int stay = stayOf(s);
        Integer fixed = fixedOf(s);
        String origin = s.origin() == null ? "course" : s.origin();
        if (!ORIGINS.contains(origin)) {
            throw CourseException.badRequest("일정의 단계가 올바르지 않아요.");
        }
        Integer order = "added".equals(origin) ? null : s.courseOrder();
        return new Stored(s.placeId(), s.role(), stay, fixed, origin, order, null);
    }

    private static int stayOf(PlanStepRequest s) {
        int stay = s.stayMinutes() == null ? 60 : s.stayMinutes();
        if (stay < 1 || stay > STAY_MAX) {
            throw CourseException.badRequest("머무는 시간은 1분에서 " + STAY_MAX / 60 + "시간 사이로 적어 주세요.");
        }
        return stay;
    }

    private static Integer fixedOf(PlanStepRequest s) {
        if (s.fixedArrive() != null && (s.fixedArrive() < 0 || s.fixedArrive() > FIXED_MAX)) {
            throw CourseException.badRequest("도착 시간이 올바르지 않아요.");
        }
        return s.fixedArrive();
    }

    /** 직접 넣은 가게: 이름·주소는 제어 문자를 지우고 공백을 하나로, 좌표는 국내만 */
    static Custom custom(CustomPlaceRequest c) {
        String name = tidy(c.name());
        String address = tidy(c.address());
        if (name.isEmpty() || name.length() > CUSTOM_NAME_MAX) {
            throw CourseException.badRequest("직접 넣는 가게 이름은 1~" + CUSTOM_NAME_MAX + "자로 적어 주세요.");
        }
        if (address.isEmpty() || address.length() > CUSTOM_ADDRESS_MAX) {
            throw CourseException.badRequest("직접 넣는 가게는 주소 찾기로 주소를 골라 주세요.");
        }
        if (c.lat() == null || c.lng() == null || !Geo.inKorea(c.lat(), c.lng())) {
            throw CourseException.badRequest("직접 넣는 가게는 국내 주소만 넣을 수 있어요.");
        }
        return new Custom(name, address, c.lat(), c.lng());
    }

    static String tidy(String text) {
        return text == null ? "" : String.join(" ", text.replaceAll("\\p{Cntrl}", " ").strip().split("\\s+")).strip();
    }

    static PlaceResponse toPlace(Custom c) {
        Regions.Region region = Regions.parse(c.address());
        String sigungu = region == null ? null : region.sigungu();
        return new PlaceResponse(CUSTOM_ID, c.name(), null, "custom", null, c.lat(), c.lng(),
                region == null ? null : region.sido(), sigungu, c.address(), null, null, null, null,
                CourseQueryService.naverMapUrl(c.name(), null, sigungu));
    }

    private PlanResponse toResponse(PlanRepository.Row r) {
        List<Stored> steps = parse(r.stepsJson());
        Map<Long, CourseQueryRepository.Candidate> byId = placesOf(steps);
        List<PlanStepResponse> out = steps.stream()
                .filter(s -> s.custom() != null || byId.containsKey(s.placeId()))   // 장소가 DB 에서 아예 사라졌으면 뺀다
                .map(s -> {
                    if (s.custom() != null) {
                        return new PlanStepResponse(s.role(), s.stay(), s.fixed(), s.origin(), null, toPlace(s.custom()), List.of("직접 넣은 곳"));
                    }
                    CourseQueryRepository.Candidate c = byId.get(s.placeId());
                    return new PlanStepResponse(s.role(), s.stay(), s.fixed(), s.origin(), s.courseOrder(), CourseQueryService.toPlace(c),
                            CourseQueryService.reasons(c.badge(), c.tour(), c.sbiz(), c.franchise(), c.category()));
                }).toList();
        return new PlanResponse(r.courseId(), r.title(), r.date() == null ? null : r.date().toString(), clock(r.startMinute()),
                r.updatedAt().toString(), out);
    }

    private Map<Long, CourseQueryRepository.Candidate> placesOf(List<Stored> steps) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(steps.stream().map(Stored::placeId).filter(Objects::nonNull).toList()));
        return places.placesByIds(ids).stream().collect(Collectors.toMap(CourseQueryRepository.Candidate::id, Function.identity()));
    }

    private List<Stored> parse(String stepsJson) {
        return json.readValue(stepsJson, new TypeReference<List<Stored>>() { });
    }

    /** 단계마다 [도착, 출발] (분). 도착 = max(앞 출발 + 걷는 시간, 고정 도착) — 화면(lib/plan.ts)과 같은 식 */
    static List<int[]> timeline(List<Stored> steps, Map<Long, CourseQueryRepository.Candidate> byId, int start) {
        List<int[]> out = new ArrayList<>();
        int clock = start;
        double[] prev = null;
        for (Stored s : steps) {
            CourseQueryRepository.Candidate found = s.placeId() == null ? null : byId.get(s.placeId());
            double[] c = s.custom() != null ? new double[] {s.custom().lat(), s.custom().lng()}
                    : found != null ? new double[] {found.lat(), found.lng()} : null;
            int walk = prev != null && c != null ? CoursePlanner.walkMinutes(Geo.distanceMeters(prev[0], prev[1], c[0], c[1])) : 0;
            int arrive = clock + walk;
            if (s.fixed() != null) {
                arrive = Math.max(arrive, s.fixed());
            }
            int depart = arrive + s.stay();
            out.add(new int[] {arrive, depart});
            clock = depart;
            if (c != null) {
                prev = c;
            }
        }
        return out;
    }

    static int minuteOf(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) {
            throw CourseException.badRequest("출발 시간을 HH:MM 으로 적어 주세요.");
        }
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        if (h > 23 || m > 59) {
            throw CourseException.badRequest("출발 시간을 HH:MM 으로 적어 주세요.");
        }
        return h * 60 + m;
    }

    static LocalDate dateOf(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw CourseException.badRequest("날짜를 YYYY-MM-DD 로 적어 주세요.");
        }
    }

    /** 분 → "18:05" (자정을 넘기면 "다음날 00:30") */
    static String clock(int minutes) {
        int m = minutes % 1440;
        String text = String.format("%02d:%02d", m / 60, m % 60);
        return minutes >= 1440 ? "다음날 " + text : text;
    }
}
