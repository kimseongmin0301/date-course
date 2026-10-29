import { Fragment, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { CATEGORY_LABEL, formatDistance, formatMinutes, ROLE_LABEL } from '../lib/format'
import { loadKeywords } from '../lib/keywords'
import { altIcon, distanceMeters, jumpTo, stepIcon, walkMinutes } from '../lib/naverMap'
import { readJson, writeJson } from '../lib/storage'
import type { AlternativeResponse, CourseDetail, Place, StepDetail } from '../lib/types'
import { useMap } from '../map/context'

const LETTERS = 'ABCDE'
const ALT_LABEL: Record<StepDetail['role'], string> = { spot: '다른 구경거리 보기', cafe: '다른 카페 보기', meal: '다른 식당 보기' }

/** 바꾼 단계: 단계 순서 → 새 장소 (이 브라우저에만 저장) */
type Swaps = Record<number, { place: Place; reasons: string[] }>

const swapKey = (id: string | undefined) => `course-swaps:${id}`

/** 바꾼 장소를 넣고, 바뀐 구간의 걷는 시간을 다시 어림한다 */
function applySwaps(steps: StepDetail[], swaps: Swaps): StepDetail[] {
  const swapped = steps.map((s) => (swaps[s.order] ? { ...s, place: swaps[s.order].place, reasons: swaps[s.order].reasons } : s))
  return swapped.map((s, i) => {
    if (i === 0) return s
    const prev = swapped[i - 1]
    if (!swaps[s.order] && !swaps[prev.order]) return s
    return { ...s, walkMinutes: walkMinutes(distanceMeters(prev.place.lat, prev.place.lng, s.place.lat, s.place.lng)) }
  })
}

// 코스 상세: 지도에 단계 이름표와 동선, 왼쪽에 단계별 장소 카드.
// 단계마다 "다른 곳 보기"로 근처 대안(같은 역할 5곳)을 보고 "이걸로 바꾸기"로 바꿀 수 있다 (이 브라우저에만 저장)
export default function CoursePage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { map } = useMap()
  const [course, setCourse] = useState<CourseDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [names, setNames] = useState<Record<string, string>>({})
  const [swaps, setSwaps] = useState<Swaps>(() => readJson<Swaps>(swapKey(id), {}))
  const [openOrder, setOpenOrder] = useState<number | null>(null)
  const [alts, setAlts] = useState<AlternativeResponse[] | null>(null)
  const [altError, setAltError] = useState<string | null>(null)

  useEffect(() => {
    loadKeywords()
      .then((list) => setNames(Object.fromEntries(list.map((k) => [k.code, k.name]))))
      .catch(() => {})
  }, [])

  useEffect(() => {
    let cancelled = false
    setCourse(null)
    setError(null)
    setOpenOrder(null)
    setSwaps(readJson<Swaps>(swapKey(id), {}))
    api<CourseDetail>(`/courses/${id}`)
      .then((c) => !cancelled && setCourse(c))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [id])

  const steps = useMemo(() => (course ? applySwaps(course.steps, swaps) : []), [course, swaps])
  const totalMinutes = steps.reduce((sum, s) => sum + (s.stayMinutes ?? 0) + (s.walkMinutes ?? 0), 0)
  const changed = Object.keys(swaps).length > 0

  // 단계 이름표·동선 (+ 열어 둔 근처 대안 A~E)
  useEffect(() => {
    if (!map || steps.length === 0) return
    const path = steps.map((s) => new naver.maps.LatLng(s.place.lat, s.place.lng))
    const overlays: { setMap(m: naver.maps.Map | null): void }[] = [
      new naver.maps.Polyline({ map, path, strokeColor: '#e8546b', strokeWeight: 4, strokeOpacity: 0.85, strokeStyle: 'shortdash' }),
      ...steps.map(
        (s) =>
          new naver.maps.Marker({
            map,
            position: new naver.maps.LatLng(s.place.lat, s.place.lng),
            icon: stepIcon(s.order, ROLE_LABEL[s.role]),
            title: s.place.name,
            zIndex: 200,
          }),
      ),
    ]
    let bounds = path.slice(1).reduce((b, p) => b.extend(p), new naver.maps.LatLngBounds(path[0], path[0]))
    ;(alts ?? []).forEach((a, i) => {
      const position = new naver.maps.LatLng(a.place.lat, a.place.lng)
      overlays.push(new naver.maps.Marker({ map, position, icon: altIcon(LETTERS[i]), title: a.place.name, zIndex: 100 }))
      bounds = bounds.extend(position)
    })
    map.fitBounds(bounds, { top: 60, right: 60, bottom: 60, left: 60 })
    return () => overlays.forEach((o) => o.setMap(null))
  }, [map, steps, alts])

  function focus(lat: number, lng: number) {
    if (map) jumpTo(map, lat, lng, 17)
  }

  function toggleAlternatives(order: number) {
    if (openOrder === order) {
      setOpenOrder(null)
      setAlts(null)
      return
    }
    setOpenOrder(order)
    setAlts(null)
    setAltError(null)
    // 대안은 원래 코스의 장소를 기준으로 찾는다 (서버가 코스 번호로 찾음)
    api<AlternativeResponse[]>(`/courses/${id}/steps/${order}/alternatives`)
      .then(setAlts)
      .catch((e: Error) => setAltError(e.message))
  }

  function saveSwaps(next: Swaps) {
    setSwaps(next)
    writeJson(swapKey(id), Object.keys(next).length ? next : null)
  }

  function swap(order: number, alt: AlternativeResponse) {
    saveSwaps({ ...swaps, [order]: { place: alt.place, reasons: alt.reasons } })
    setOpenOrder(null)
    setAlts(null)
  }

  function restore(order?: number) {
    if (order == null) {
      saveSwaps({})
      return
    }
    const next = { ...swaps }
    delete next[order]
    saveSwaps(next)
  }

  if (error) {
    return (
      <section>
        <p className="notice">{error}</p>
        <Link to="/">처음으로</Link>
      </section>
    )
  }
  if (!course) return <p className="muted">불러오는 중…</p>

  return (
    <section className="course">
      <button type="button" className="back" onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}>
        ← 목록으로
      </button>
      <h1>{course.title}</h1>
      <p className="meta muted">
        {[course.sido, course.sigungu, totalMinutes > 0 && `약 ${formatMinutes(totalMinutes)}`, course.kind === 'auto' ? '자동으로 만든 코스' : '추천 코스']
          .filter(Boolean)
          .join(' · ')}
      </p>
      <div className="tags">
        {course.keywords.map((code) => (
          <span key={code} className="tag">
            {names[code] ?? code}
          </span>
        ))}
      </div>
      {changed && (
        <p className="notice-soft">
          내가 바꾼 코스예요. 바꾼 내용은 이 브라우저에만 저장돼요.{' '}
          <button type="button" className="link-button" onClick={() => restore()}>
            원래 코스로
          </button>
        </p>
      )}

      <ol className="steps">
        {steps.map((s) => (
          <Fragment key={s.order}>
            {s.walkMinutes != null && <li className="walk muted">🚶 걸어서 약 {s.walkMinutes}분</li>}
            <li className="step">
              <button type="button" className="step-pin" onClick={() => focus(s.place.lat, s.place.lng)} aria-label={`${s.place.name} 지도에서 보기`}>
                {s.order}
              </button>
              <div className="step-body">
                <p className="step-role muted">
                  {ROLE_LABEL[s.role]} · {CATEGORY_LABEL[s.place.category] ?? s.place.category}
                  {s.stayMinutes ? ` · ${formatMinutes(s.stayMinutes)}` : ''}
                </p>
                <h2>
                  {s.place.name}
                  {s.place.branch && <span className="muted small"> {s.place.branch}</span>}
                  {swaps[s.order] && <span className="swapped-tag">바꿈</span>}
                </h2>
                {s.reasons.length > 0 && (
                  <div className="reasons">
                    {s.reasons.map((r) => (
                      <span key={r} className="reason">
                        ✓ {r}
                      </span>
                    ))}
                  </div>
                )}
                {s.memo && !swaps[s.order] && <p>{s.memo}</p>}
                {s.place.imageUrl && <img src={s.place.imageUrl} alt="" loading="lazy" className="step-image" />}
                {s.place.description && <p className="description">{s.place.description}</p>}
                <p className="muted small">{s.place.roadAddress ?? s.place.lotAddress}</p>
                {s.place.phone && (
                  <p className="small">
                    <a href={`tel:${s.place.phone}`}>{s.place.phone}</a>
                  </p>
                )}
                <div className="step-actions">
                  <a href={s.place.naverMapUrl} target="_blank" rel="noreferrer" className="more">
                    네이버지도에서 보기 ↗
                  </a>
                  <button type="button" className="link-button" onClick={() => toggleAlternatives(s.order)} aria-expanded={openOrder === s.order}>
                    {openOrder === s.order ? '대안 닫기' : ALT_LABEL[s.role]}
                  </button>
                  {swaps[s.order] && (
                    <button type="button" className="link-button" onClick={() => restore(s.order)}>
                      원래 장소로
                    </button>
                  )}
                </div>

                {openOrder === s.order && (
                  <ul className="alternatives">
                    {altError && <li className="notice small">{altError}</li>}
                    {!alts && !altError && <li className="muted small">근처를 찾는 중…</li>}
                    {alts && alts.length === 0 && <li className="muted small">걸어서 10분 안에 바꿀 만한 곳이 없어요.</li>}
                    {alts?.map((a, i) => (
                      <li key={a.place.id} className="alternative">
                        <button type="button" className="alt-letter" onClick={() => focus(a.place.lat, a.place.lng)} aria-label={`${a.place.name} 지도에서 보기`}>
                          {LETTERS[i]}
                        </button>
                        <div className="step-body">
                          <h3>{a.place.name}</h3>
                          <p className="muted small">
                            {[CATEGORY_LABEL[a.place.category] ?? a.place.category, a.place.detailCategory && !/^[A-Z]{2}\d/.test(a.place.detailCategory) ? a.place.detailCategory : null, `원래 장소에서 ${formatDistance(a.distanceMeters)}`]
                              .filter(Boolean)
                              .join(' · ')}
                          </p>
                          {a.reasons.length > 0 && (
                            <div className="reasons">
                              {a.reasons.map((r) => (
                                <span key={r} className="reason">
                                  ✓ {r}
                                </span>
                              ))}
                            </div>
                          )}
                          <div className="step-actions">
                            <button type="button" className="swap-button" onClick={() => swap(s.order, a)}>
                              이걸로 바꾸기
                            </button>
                            <a href={a.place.naverMapUrl} target="_blank" rel="noreferrer" className="more">
                              네이버지도 ↗
                            </a>
                          </div>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </li>
          </Fragment>
        ))}
      </ol>
      <p className="muted small">
        장소는 리뷰·별점 없이 공공데이터(업종·지정 정보·출처)로 골랐어요. 걷는 시간은 직선 거리로 어림한 값이고, 영업 여부는
        네이버지도에서 꼭 확인해 주세요.
      </p>
    </section>
  )
}
