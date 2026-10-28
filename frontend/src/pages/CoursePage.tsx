import { Fragment, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { CATEGORY_LABEL, formatMinutes, ROLE_LABEL } from '../lib/format'
import { loadKeywords } from '../lib/keywords'
import { pinIcon } from '../lib/naverMap'
import type { CourseDetail } from '../lib/types'
import { useMap } from '../map/context'

// 코스 상세: 지도에 단계 번호 핀과 동선, 왼쪽에 단계별 장소 카드 (근처 대안은 다음에)
export default function CoursePage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { map } = useMap()
  const [course, setCourse] = useState<CourseDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [names, setNames] = useState<Record<string, string>>({})

  useEffect(() => {
    loadKeywords()
      .then((list) => setNames(Object.fromEntries(list.map((k) => [k.code, k.name]))))
      .catch(() => {})
  }, [])

  useEffect(() => {
    let cancelled = false
    setCourse(null)
    setError(null)
    api<CourseDetail>(`/courses/${id}`)
      .then((c) => !cancelled && setCourse(c))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [id])

  useEffect(() => {
    if (!map || !course || course.steps.length === 0) return
    const path = course.steps.map((s) => new naver.maps.LatLng(s.place.lat, s.place.lng))
    const overlays: { setMap(m: naver.maps.Map | null): void }[] = [
      new naver.maps.Polyline({ map, path, strokeColor: '#e8546b', strokeWeight: 4, strokeOpacity: 0.85, strokeStyle: 'shortdash' }),
      ...course.steps.map(
        (s) => new naver.maps.Marker({ map, position: new naver.maps.LatLng(s.place.lat, s.place.lng), icon: pinIcon(s.order, 'step'), title: s.place.name }),
      ),
    ]
    const bounds = path.slice(1).reduce((b, p) => b.extend(p), new naver.maps.LatLngBounds(path[0], path[0]))
    map.fitBounds(bounds, { top: 60, right: 60, bottom: 60, left: 60 })
    return () => overlays.forEach((o) => o.setMap(null))
  }, [map, course])

  function focus(lat: number, lng: number) {
    map?.morph(new naver.maps.LatLng(lat, lng), 17)
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
        {[course.sido, course.sigungu, formatMinutes(course.totalMinutes) && `약 ${formatMinutes(course.totalMinutes)}`, course.kind === 'auto' ? '자동으로 만든 코스' : '추천 코스']
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

      <ol className="steps">
        {course.steps.map((s) => (
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
                </h2>
                {s.memo && <p>{s.memo}</p>}
                {s.place.imageUrl && <img src={s.place.imageUrl} alt="" loading="lazy" className="step-image" />}
                {s.place.description && <p className="description">{s.place.description}</p>}
                <p className="muted small">{s.place.roadAddress ?? s.place.lotAddress}</p>
                {s.place.phone && (
                  <p className="small">
                    <a href={`tel:${s.place.phone}`}>{s.place.phone}</a>
                  </p>
                )}
                <a href={s.place.naverMapUrl} target="_blank" rel="noreferrer" className="more">
                  네이버지도에서 보기 ↗
                </a>
              </div>
            </li>
          </Fragment>
        ))}
      </ol>
      <p className="muted small">
        걷는 시간은 직선 거리로 어림한 값이에요. 영업 여부는 네이버지도에서 꼭 확인해 주세요.
      </p>
    </section>
  )
}
