package com.datecourse.domain.course.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.course.entity.StepRole;
import com.datecourse.domain.place.entity.PlaceCategory;

/**
 * 자동 코스 조합: 중심 장소(관광명소·문화시설) 하나에 걸어서 갈 만한 곳을 붙여 3~4단계로 만든다.
 *   중심 장소 → (구경할 곳 하나 더) → 카페 → 식당
 * 단계 사이는 직선 700m(걸어서 약 15분) 안, 모든 단계는 중심 장소에서 1km 안.
 * 후보는 점수로 고른다: 중심 장소와 분위기 키워드가 겹칠수록, TourAPI 에도 있는 곳(관광공사가 고른 곳),
 * 데이트에 어울리는 업종일수록 위, 전국에 같은 이름이 많은 곳(체인점)과 멀리 있는 곳은 아래.
 * 이미 다른 코스에 여러 번 넣은 곳도 조금씩 아래로 (명소가 몰린 곳에서 모든 코스가 같은 카페로 가지 않게).
 */
public final class CoursePlanner {

    /** 메모리에 올린 장소 하나 */
    public record Spot(long id, String name, String normalizedName, PlaceCategory category, String detail,
                       double lat, double lng, String sido, String sigungu, Set<String> keywords,
                       boolean tour, boolean franchise) {
    }

    public record Step(Spot place, StepRole role, int stayMinutes, Integer walkMinutes) {
    }

    public record Plan(Spot anchor, List<Step> steps, Set<String> keywords, int totalMinutes,
                       double centerLat, double centerLng, String title, String summary) {
    }

    static final double LEG_MAX_M = 700;
    /** 동네 코스는 이 거리 안에 코스가 하나도 없을 때만 만든다 */
    public static final double NEIGHBORHOOD_GAP_M = 1500;
    static final double FROM_ANCHOR_MAX_M = 1000;
    /** 같은 이름이 전국에 이만큼 넘게 있으면 체인점으로 본다 */
    public static final int FRANCHISE_MIN = 15;

    private static final Set<String> MOOD = Set.of("quiet", "romantic", "lively", "cozy", "unique");
    private static final Pattern TOUR_CODE = Pattern.compile("^[A-Z]{2}\\d{6}$");
    /** 이름에 연도가 든 명소는 지난 행사·축제 (예: 2025 OO페어) */
    private static final Pattern YEAR = Pattern.compile("(19|20)[0-9]{2}");
    /** 상가정보 "서점" 업종에는 학습지·교육 업체가 섞여 있어 이름으로 서점을 가린다 */
    private static final Pattern BOOKSTORE = Pattern.compile("책방|서점|문고|북|book");
    /** 다른 코스에 한 번 넣을 때마다 깎는 점수 */
    private static final double REUSE_PENALTY = 0.7;

    /** 중심 장소로 쓰지 않는 TourAPI 분류: 교통시설, 학교·어학당, 컨벤션·연회장, 어린이공원, 스포츠센터, 카지노, 약수터, 북한관광지, 의료관광 */
    private static final List<String> NOT_ANCHOR = List.of("VE11", "VE0905", "VE0906", "VE070400", "VE080600",
            "VE030300", "VE100200", "VE120200", "NA010500", "HS040300", "EX050800");

    /** 구경할 곳으로 넣을 놀거리 업종 (노래방·당구장·잡다한 오락 서비스업은 뺀다) */
    private static final Set<String> GOOD_ACTIVITY = Set.of("볼링장", "전자 게임장", "기타 오락장", "수상/해양 레저업");

    /** 식당 업종 점수: 데이트에 어울리면 +, 혼밥·배달 위주면 - */
    private static final Map<String, Integer> MEAL_BONUS = Map.ofEntries(
            Map.entry("파스타/스테이크", 3), Map.entry("경양식", 2), Map.entry("기타 서양식 음식점", 2),
            Map.entry("일식 회/초밥", 2), Map.entry("일식 카레/돈가스/덮밥", 2), Map.entry("일식 면 요리", 2),
            Map.entry("기타 일식 음식점", 1), Map.entry("베트남식 전문", 2), Map.entry("기타 동남아식 전문", 2),
            Map.entry("패밀리레스토랑", 1), Map.entry("피자", 1), Map.entry("버거", 1),
            Map.entry("토스트/샌드위치/샐러드", 1), Map.entry("마라탕/훠궈", 1), Map.entry("소고기 구이/찜", 1),
            Map.entry("돼지고기 구이/찜", 1), Map.entry("곱창 전골/구이", 1),
            Map.entry("백반/한정식", -1), Map.entry("중국집", -1), Map.entry("치킨", -1), Map.entry("뷔페", -1),
            Map.entry("국수/칼국수", -1), Map.entry("국/탕/찌개류", -2), Map.entry("김밥/만두/분식", -2),
            Map.entry("그 외 기타 간이 음식점", -2));

    private CoursePlanner() {
    }

    public static boolean isTourCode(String detail) {
        return detail != null && TOUR_CODE.matcher(detail).matches();
    }

    /** 자동 코스의 중심 장소가 될 수 있나 */
    public static boolean isAnchor(Spot s) {
        if (s.category() != PlaceCategory.ATTRACTION && s.category() != PlaceCategory.CULTURE) {
            return false;
        }
        if (YEAR.matcher(s.name()).find()) {
            return false;
        }
        return s.detail() == null || NOT_ANCHOR.stream().noneMatch(s.detail()::startsWith);
    }

    /** 명소가 없는 동네의 중심: 소품샵·서점·데이트에 어울리는 놀거리 (구경거리 규칙과 같고, 명소·문화시설은 위에서 이미 씀) */
    public static boolean isNeighborhoodAnchor(Spot s) {
        return !isAnchor(s) && isSecondSpot(s);
    }

    /** 동네 코스 중심을 고를 때의 점수: 분위기 키워드가 많을수록, TourAPI 에 있으면 +2, 체인점 −3 */
    public static double anchorRank(Spot s) {
        long mood = s.keywords().stream().filter(MOOD::contains).count();
        return mood + (s.tour() ? 2 : 0) - (s.franchise() ? 3 : 0);
    }

    /** 걸어서 걸리는 시간(분): 직선 거리에 길 굽음 1.3배, 분속 67m(시속 4km) */
    public static int walkMinutes(double meters) {
        return Math.max(1, (int) Math.round(meters * 1.3 / 67));
    }

    public static Optional<Plan> plan(Spot anchor, PlaceGrid grid) {
        return plan(anchor, grid, new HashMap<>());
    }

    /**
     * 카페와 식당이 근처에 없으면 코스를 만들지 않는다.
     * used: 장소 id → 지금까지 다른 코스에 넣은 횟수 (코스를 만들면 늘려 둔다)
     */
    public static Optional<Plan> plan(Spot anchor, PlaceGrid grid, Map<Long, Integer> used) {
        Set<String> taste = new LinkedHashSet<>(anchor.keywords());
        taste.retainAll(MOOD);

        List<Step> steps = new ArrayList<>();
        steps.add(new Step(anchor, StepRole.SPOT, stay(anchor, true), null));
        Set<String> usedNames = new LinkedHashSet<>(Set.of(anchor.normalizedName()));

        Spot prev = anchor;
        // 중심 장소와 같은 분류(공원 → 공원)는 조금 아래로
        Optional<GridPick> second = pick(grid, anchor, prev, taste, usedNames, used, CoursePlanner::isSecondSpot,
                s -> spotBonus(s) - (s.detail() != null && s.detail().equals(anchor.detail()) ? 1 : 0));
        if (second.isPresent()) {
            steps.add(new Step(second.get().spot(), StepRole.SPOT, stay(second.get().spot(), false), walkMinutes(second.get().meters())));
            usedNames.add(second.get().spot().normalizedName());
            prev = second.get().spot();
        }
        Optional<GridPick> cafe = pick(grid, anchor, prev, taste, usedNames, used, s -> s.category() == PlaceCategory.CAFE, CoursePlanner::cafeBonus);
        if (cafe.isEmpty()) {
            return Optional.empty();
        }
        steps.add(new Step(cafe.get().spot(), StepRole.CAFE, 60, walkMinutes(cafe.get().meters())));
        usedNames.add(cafe.get().spot().normalizedName());
        Optional<GridPick> meal = pick(grid, anchor, cafe.get().spot(), taste, usedNames, used,
                s -> s.category() == PlaceCategory.RESTAURANT, s -> MEAL_BONUS.getOrDefault(s.detail(), 0));
        if (meal.isEmpty()) {
            return Optional.empty();
        }
        steps.add(new Step(meal.get().spot(), StepRole.MEAL, 70, walkMinutes(meal.get().meters())));
        steps.stream().skip(1).forEach(step -> used.merge(step.place().id(), 1, Integer::sum));
        return Optional.of(toPlan(anchor, steps));
    }

    private record GridPick(Spot spot, double meters, double score) {
    }

    /** prev 에서 700m, 중심 장소에서 1km 안의 후보 중 점수가 가장 높은 곳 (같으면 id 작은 곳) */
    private static Optional<GridPick> pick(PlaceGrid grid, Spot anchor, Spot prev, Set<String> taste, Set<String> usedNames,
                                           Map<Long, Integer> used, Predicate<Spot> allowed, ToIntFunction<Spot> bonus) {
        return grid.near(prev.lat(), prev.lng(), LEG_MAX_M).stream()
                .filter(n -> n.spot().id() != anchor.id() && !usedNames.contains(n.spot().normalizedName()))
                .filter(n -> allowed.test(n.spot()))
                .filter(n -> Geo.distanceMeters(anchor.lat(), anchor.lng(), n.spot().lat(), n.spot().lng()) <= FROM_ANCHOR_MAX_M)
                .map(n -> new GridPick(n.spot(), n.meters(), score(n.spot(), n.meters(), taste) + bonus.applyAsInt(n.spot())
                        - REUSE_PENALTY * used.getOrDefault(n.spot().id(), 0)))
                .max(Comparator.comparingDouble(GridPick::score).thenComparing(p -> -p.spot().id()));
    }

    static double score(Spot s, double meters, Set<String> taste) {
        long sameMood = s.keywords().stream().filter(taste::contains).count();
        return 2.0 * sameMood + (s.tour() ? 2 : 0) - (s.franchise() ? 3 : 0) - meters / 250;
    }

    /** 중심 장소 다음에 들를 구경거리: 소품샵 등(꽃집·문구점 제외, 서점은 이름이 서점인 곳만), 다른 명소·문화시설, 데이트에 어울리는 놀거리 */
    static boolean isSecondSpot(Spot s) {
        return switch (s.category()) {
            case SHOP -> switch (s.detail() == null ? "" : s.detail()) {
                case "꽃집", "문구/회화용품 소매업" -> false;
                case "서점" -> BOOKSTORE.matcher(s.normalizedName()).find();
                default -> true;
            };
            case ATTRACTION, CULTURE -> isAnchor(s);
            case ACTIVITY -> isTourCode(s.detail()) || GOOD_ACTIVITY.contains(s.detail()) || s.keywords().contains("unique");
            default -> false;
        };
    }

    private static int spotBonus(Spot s) {
        return s.category() == PlaceCategory.SHOP && s.keywords().contains("cozy") ? 1 : 0;
    }

    private static int cafeBonus(Spot s) {
        if ("아이스크림/빙수".equals(s.detail())) {
            return -1;
        }
        return "카페".equals(s.detail()) || (s.detail() != null && s.detail().startsWith("FD05")) ? 1 : 0;
    }

    private static int stay(Spot s, boolean anchor) {
        return switch (s.category()) {
            case SHOP -> 30;
            case ACTIVITY -> anchor ? 90 : 60;
            default -> anchor ? 60 : 50;
        };
    }

    private static Plan toPlan(Spot anchor, List<Step> steps) {
        Set<String> keywords = new LinkedHashSet<>();
        for (Step step : steps) {
            for (String code : step.place().keywords()) {
                boolean spotTag = step.role() == StepRole.SPOT && !code.startsWith("budget-") && !code.equals("indoor") && !code.equals("outdoor");
                // 식당·카페는 분위기만 (맛집 투어·예산은 아래에서 따로)
                if (spotTag || (step.role() != StepRole.SPOT && MOOD.contains(code))) {
                    keywords.add(code);
                }
            }
        }
        // 실내외는 중심 장소를 따른다
        anchor.keywords().stream().filter(c -> c.equals("indoor") || c.equals("outdoor")).findFirst().ifPresent(keywords::add);
        Spot meal = steps.get(steps.size() - 1).place();
        keywords.add(meal.keywords().stream().filter(c -> c.startsWith("budget-")).findFirst().orElse("budget-mid"));
        if (meal.tour()) {
            keywords.add("food-tour");
        }

        int total = 0;
        double lat = 0;
        double lng = 0;
        List<String> names = new ArrayList<>();
        for (Step step : steps) {
            total += step.stayMinutes() + (step.walkMinutes() == null ? 0 : step.walkMinutes());
            lat += step.place().lat();
            lng += step.place().lng();
            names.add(step.place().name());
        }
        return new Plan(anchor, steps, keywords, total, lat / steps.size(), lng / steps.size(),
                anchor.name() + " 데이트 코스", String.join(" → ", names));
    }
}
