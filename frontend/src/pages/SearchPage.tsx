import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import KeywordPicker from '../components/KeywordPicker'
import LocationSearch from '../components/LocationSearch'
import { api } from '../lib/api'
import { formatDistance, formatMinutes, parseKeywords, ROLE_LABEL } from '../lib/format'
import { loadKeywords } from '../lib/keywords'
import { distanceMeters, jumpTo, rankIcon, stepIcon, visibleRadius, zoomForRadius } from '../lib/naverMap'
import type { CourseCard, CourseSearchResponse, LocationResult } from '../lib/types'
import { useMap } from '../map/context'

// 검색 결과: 왼쪽 코스 순위 카드, 지도에는 코스 가운데 순위 핀. 카드를 고르면 그 코스 단계와 동선을 그린다
// 주소 예: /search?lat=37.4979&lng=127.0276&r=2000&kw=quiet,cozy
export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  // 근처에 코스가 없어 반경을 넓혔으면 원래 반경을 기억해 안내한다
  const widenedFrom = (useLocation().state as { widenedFrom?: number } | null)?.widenedFrom
  const navigate = useNavigate()
  const { map, overlay } = useMap()

  const lat = Number(params.get('lat'))
  const lng = Number(params.get('lng'))
  const radius = Number(params.get('r') ?? 3000)
  const kwParam = params.get('kw')
  const keywords = useMemo(() => parseKeywords(kwParam), [kwParam])
  const valid = Number.isFinite(lat) && Number.isFinite(lng) && lat !== 0 && lng !== 0

  const [courses, setCourses] = useState<CourseCard[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [moved, setMoved] = useState(false)
  const [names, setNames] = useState<Record<string, string>>({})
  const cardRefs = useRef(new Map<number, HTMLElement>())
  // 화면이 지도를 옮긴 것(검색 위치로, 고른 코스로)은 "지도를 옮겼다"로 치지 않는다
  const programmatic = useRef(false)

  useEffect(() => {
    loadKeywords()
      .then((list) => setNames(Object.fromEntries(list.map((k) => [k.code, k.name]))))
      .catch(() => {})
  }, [])

  // 검색 조건이 바뀌면 첫 쪽부터 다시
  useEffect(() => {
    if (!valid) return
    let cancelled = false
    setLoading(true)
    setError(null)
    setSelected(null)
    const q = new URLSearchParams({ lat: String(lat), lng: String(lng), radius: String(radius), page: '0' })
    if (keywords.length) q.set('kw', keywords.join(','))
    api<CourseSearchResponse>(`/courses?${q}`)
      .then((res) => {
        if (cancelled) return
        if (res.widened) {
          // 넓힌 반경을 주소에 넣어 지도·더 보기가 같은 반경을 쓰게 한다
          setParams(
            (prev) => {
              const next = new URLSearchParams(prev)
              next.set('r', String(res.radius))
              return next
            },
            { replace: true, state: { widenedFrom: radius } },
          )
          return
        }
        setCourses(res.courses)
        setTotal(res.total)
        setPage(0)
      })
      .catch((e: Error) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [valid, lat, lng, radius, keywords, setParams])

  function loadMore() {
    const next = page + 1
    const q = new URLSearchParams({ lat: String(lat), lng: String(lng), radius: String(radius), page: String(next) })
    if (keywords.length) q.set('kw', keywords.join(','))
    setLoading(true)
    api<CourseSearchResponse>(`/courses?${q}`)
      .then((res) => {
        setCourses((prev) => [...prev, ...res.courses])
        setPage(next)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }

  // 지도를 검색한 곳으로 (이미 거기 있으면 그대로)
  useEffect(() => {
    if (!map || !valid) return
    const c = map.getCenter()
    if (distanceMeters(c.lat(), c.lng(), lat, lng) > 50 || Math.abs(visibleRadius(map) - radius) > radius * 0.5) {
      programmatic.current = true
      map.setCenter(new naver.maps.LatLng(lat, lng))
      map.setZoom(zoomForRadius(radius))
    }
    setMoved(false)
  }, [map, valid, lat, lng, radius])

  // 지도를 꽤 옮기면 "이 지역에서 다시 찾기"
  useEffect(() => {
    if (!map || !valid) return
    const listener = naver.maps.Event.addListener(map, 'idle', () => {
      if (programmatic.current) {
        programmatic.current = false
        return
      }
      const c = map.getCenter()
      const far = distanceMeters(c.lat(), c.lng(), lat, lng) > Math.max(300, radius * 0.25)
      const zoomed = Math.abs(visibleRadius(map) - radius) > radius * 0.5
      setMoved(far || zoomed)
    })
    return () => naver.maps.Event.removeListener(listener)
  }, [map, valid, lat, lng, radius])

  // 순위 핀 (고른 코스는 그 코스의 단계 핀과 동선)
  useEffect(() => {
    if (!map) return
    const overlays: { setMap(m: naver.maps.Map | null): void }[] = []
    const listeners: naver.maps.MapEventListener[] = []
    const chosen = courses.find((c) => c.id === selected)
    courses.forEach((c, i) => {
      if (c.id === selected) return
      const marker = new naver.maps.Marker({
        map,
        position: new naver.maps.LatLng(c.centerLat, c.centerLng),
        icon: rankIcon(i + 1, !!chosen),
        title: c.title,
        zIndex: chosen ? 50 - i : 100 - i,
      })
      listeners.push(naver.maps.Event.addListener(marker, 'click', () => setSelected(c.id)))
      overlays.push(marker)
    })
    if (chosen) {
      const path = chosen.steps.map((s) => new naver.maps.LatLng(s.lat, s.lng))
      overlays.push(new naver.maps.Polyline({ map, path, strokeColor: '#e8546b', strokeWeight: 4, strokeOpacity: 0.85, strokeStyle: 'shortdash' }))
      chosen.steps.forEach((s) => {
        overlays.push(new naver.maps.Marker({ map, position: new naver.maps.LatLng(s.lat, s.lng), icon: stepIcon(s.order, ROLE_LABEL[s.role]), title: s.name, zIndex: 200 }))
      })
    }
    return () => {
      listeners.forEach((l) => naver.maps.Event.removeListener(l))
      overlays.forEach((o) => o.setMap(null))
    }
  }, [map, courses, selected])

  // 코스를 고르면 지도를 그 코스로, 목록도 그 카드로
  useEffect(() => {
    const chosen = courses.find((c) => c.id === selected)
    if (!chosen) return
    if (map) {
      const bounds = chosen.steps.reduce(
        (b, s) => b.extend(new naver.maps.LatLng(s.lat, s.lng)),
        new naver.maps.LatLngBounds(new naver.maps.LatLng(chosen.centerLat, chosen.centerLng), new naver.maps.LatLng(chosen.centerLat, chosen.centerLng)),
      )
      programmatic.current = true
      map.fitBounds(bounds, { top: 60, right: 60, bottom: 60, left: 60 })
    }
    cardRefs.current.get(chosen.id)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [map, courses, selected])

  function changeKeywords(codes: string[]) {
    const next = new URLSearchParams(params)
    if (codes.length) next.set('kw', codes.join(','))
    else next.delete('kw')
    setParams(next, { replace: true })
  }

  // 다른 지역을 찾으면 그곳에서 바로 다시 찾는다
  function searchAt(l: LocationResult) {
    // 명소·주소는 반경이 좁으므로 코스는 1.5km 이상에서 찾는다 (지도는 검색 위치로 맞춘 뒤 반경에 맞게 넓어짐)
    const next = new URLSearchParams({ lat: l.lat.toFixed(5), lng: l.lng.toFixed(5), r: String(Math.max(1500, l.radius)) })
    if (keywords.length) next.set('kw', keywords.join(','))
    navigate(`/search?${next}`)
  }

  const chosenIndex = courses.findIndex((c) => c.id === selected)

  // 고른 코스를 풀고 검색한 넓이로 돌아가 모든 순위 핀을 본다
  function showAll() {
    setSelected(null)
    if (map) {
      programmatic.current = true
      jumpTo(map, lat, lng, zoomForRadius(radius))
    }
  }

  function searchHere() {
    if (!map) return
    const c = map.getCenter()
    const next = new URLSearchParams({ lat: c.lat().toFixed(5), lng: c.lng().toFixed(5), r: String(visibleRadius(map)) })
    if (keywords.length) next.set('kw', keywords.join(','))
    navigate(`/search?${next}`)
  }

  if (!valid) {
    return (
      <section>
        <h1>코스 순위</h1>
        <p className="muted">지역을 먼저 골라 주세요.</p>
        <Link to="/">지역 고르기</Link>
      </section>
    )
  }

  return (
    <section className="search">
      <div className="search-head">
        <h1>
          코스 순위 <span className="muted small">반경 {formatDistance(radius)} · {total}개</span>
        </h1>
        <LocationSearch onPick={searchAt} />
        <KeywordPicker value={keywords} onChange={changeKeywords} compact />
      </div>

      {error && <p className="notice">{error}</p>}
      {widenedFrom && widenedFrom < radius && courses.length > 0 && (
        <p className="notice-soft">
          반경 {formatDistance(widenedFrom)} 안에는 아직 코스가 없어서, {formatDistance(radius)}까지 넓혀 가까운 코스를 찾았어요.
        </p>
      )}
      {!loading && !error && courses.length === 0 && (
        <p className="empty muted">이 근처에는 아직 코스가 없어요. 지도를 넓히거나 옮겨서 다시 찾아 주세요.</p>
      )}

      <ol className="course-list">
        {courses.map((c, i) => (
          <li
            key={c.id}
            ref={(el) => {
              if (el) cardRefs.current.set(c.id, el)
              else cardRefs.current.delete(c.id)
            }}
            className={c.id === selected ? 'course-card selected' : 'course-card'}
            onClick={() => setSelected(c.id)}
          >
            <span className="rank">{i + 1}</span>
            <div className="course-body">
              <h2>{c.title}</h2>
              <p className="steps-line">{c.steps.map((s) => s.name).join(' → ')}</p>
              <p className="meta muted">
                {[c.sigungu, formatMinutes(c.totalMinutes), `지도 가운데에서 ${formatDistance(c.distanceMeters)}`, c.kind === 'curated' ? '추천 코스' : null]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
              <div className="tags">
                {c.keywords.map((code) => (
                  <span key={code} className={c.matchedKeywords.includes(code) ? 'tag matched' : 'tag'}>
                    {names[code] ?? code}
                  </span>
                ))}
              </div>
              <Link to={`/courses/${c.id}`} className="more" onClick={(e) => e.stopPropagation()}>
                코스 자세히 보기 →
              </Link>
            </div>
          </li>
        ))}
      </ol>

      {loading && <p className="muted">불러오는 중…</p>}
      {!loading && courses.length < total && (
        <button type="button" className="secondary wide" onClick={loadMore}>
          더 보기 ({courses.length}/{total})
        </button>
      )}

      {overlay &&
        createPortal(
          <>
            {moved && (
              <button type="button" className="map-button" onClick={searchHere}>
                ↻ 이 지역에서 다시 찾기
              </button>
            )}
            {chosenIndex >= 0 && (
              <div className="course-nav" role="group" aria-label="코스 넘겨 보기">
                <button type="button" disabled={chosenIndex === 0} onClick={() => setSelected(courses[chosenIndex - 1].id)} aria-label="이전 코스">
                  ◀
                </button>
                <span className="nav-rank">
                  {chosenIndex + 1}위 <span className="muted small">/ {total}</span>
                </span>
                <button type="button" disabled={chosenIndex === courses.length - 1} onClick={() => setSelected(courses[chosenIndex + 1].id)} aria-label="다음 코스">
                  ▶
                </button>
                <button type="button" className="nav-all" onClick={showAll}>
                  전체 보기
                </button>
              </div>
            )}
          </>,
          overlay,
        )}
    </section>
  )
}
