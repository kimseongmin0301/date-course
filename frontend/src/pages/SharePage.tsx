import { Fragment, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { CATEGORY_LABEL, ROLE_LABEL } from '../lib/format'
import { jumpTo, stepIcon } from '../lib/naverMap'
import { clockText, fromSaved, saveDraft, timeline, type Plan, type PlanRole } from '../lib/plan'
import type { CourseDetail, PlanResponse } from '../lib/types'
import { useMap } from '../map/context'

const roleLabel = (r: PlanRole) => (r === 'mine' ? '내 장소' : ROLE_LABEL[r])

/** "2026-11-08" → 11월 8일 (토) */
function dateLabel(date: string | null): string | null {
  if (!date) return null
  const d = new Date(`${date}T00:00:00`)
  if (Number.isNaN(d.getTime())) return null
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${'일월화수목금토'[d.getDay()]})`
}

// 공유받은 일정 (/s/:token): 로그인 없이 읽기만. 마음에 들면 "내 일정으로 가져오기"로 내 코스 상세에 옮겨 고치고 저장한다
export default function SharePage() {
  const { token = '' } = useParams()
  const navigate = useNavigate()
  const { map } = useMap()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [course, setCourse] = useState<CourseDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api<PlanResponse>(`/shares/${encodeURIComponent(token)}`)
      .then((shared) =>
        api<CourseDetail>(`/courses/${shared.courseId}`).then((c) => {
          if (cancelled) return
          setCourse(c)
          setPlan(fromSaved(shared, c))
        }),
      )
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [token])

  const steps = useMemo(() => (plan ? timeline(plan) : []), [plan])

  // 단계 이름표·동선
  useEffect(() => {
    if (!map || steps.length === 0) return
    const path = steps.map((s) => new naver.maps.LatLng(s.place.lat, s.place.lng))
    const overlays: { setMap(m: naver.maps.Map | null): void }[] = [
      new naver.maps.Polyline({ map, path, strokeColor: '#e8546b', strokeWeight: 4, strokeOpacity: 0.85, strokeStyle: 'shortdash' }),
      ...steps.map(
        (s, i) =>
          new naver.maps.Marker({ map, position: path[i], icon: stepIcon(i + 1, roleLabel(s.role)), title: s.place.name, zIndex: 200 }),
      ),
    ]
    const bounds = path.slice(1).reduce((b, p) => b.extend(p), new naver.maps.LatLngBounds(path[0], path[0]))
    map.fitBounds(bounds, { top: 60, right: 60, bottom: 60, left: 60 })
    return () => overlays.forEach((o) => o.setMap(null))
  }, [map, steps])

  /** 내 일정으로: 이 브라우저의 그 코스 임시 일정으로 두고 코스 상세로 (거기서 고치고 저장) */
  function takeIt() {
    if (!plan || !course) return
    saveDraft(String(course.id), plan)
    navigate(`/courses/${course.id}`)
  }

  if (error) {
    return (
      <section>
        <h1>공유받은 일정</h1>
        <p className="notice">{error}</p>
        <Link to="/">데이트 코스 찾아보기</Link>
      </section>
    )
  }
  if (!plan || !course) return <p className="muted">불러오는 중…</p>

  const end = steps.length ? steps[steps.length - 1].depart : 0
  const when = dateLabel(plan.date)

  return (
    <section className="share-page">
      <p className="muted small">공유받은 일정</p>
      <h1>{course.title}</h1>
      <p className="muted">
        {[course.sido, course.sigungu, when, `${clockText(steps[0]?.arrive ?? 0)} ~ ${clockText(end)}`].filter(Boolean).join(' · ')}
      </p>

      <div className="actions">
        <button type="button" className="primary" onClick={takeIt}>
          내 일정으로 가져오기
        </button>
        <p className="muted small">가져오면 이 브라우저에서 장소·시간을 고치고 저장할 수 있어요.</p>
      </div>

      <ol className="steps">
        {steps.map((s, i) => (
          <Fragment key={s.key}>
            {s.walkMinutes != null && <li className="walk muted">🚶 걸어서 약 {s.walkMinutes}분</li>}
            <li className="step">
              <button
                type="button"
                className="step-pin"
                onClick={() => map && jumpTo(map, s.place.lat, s.place.lng, 17)}
                aria-label={`${s.place.name} 지도에서 보기`}
              >
                {i + 1}
              </button>
              <div className="step-body">
                <p className="step-time-text">
                  <strong>{clockText(s.arrive)}</strong> ~ {clockText(s.depart)} <span className="muted small">({s.stayMinutes}분)</span>
                </p>
                <p className="step-role muted">
                  {roleLabel(s.role)} · {CATEGORY_LABEL[s.place.category] ?? s.place.category}
                </p>
                <h2>
                  {s.place.name}
                  {s.place.branch && <span className="muted small"> {s.place.branch}</span>}
                </h2>
                {s.place.roadAddress && <p className="small muted">{s.place.roadAddress}</p>}
                <a href={s.place.naverMapUrl} target="_blank" rel="noreferrer" className="small">
                  네이버지도 ↗
                </a>
              </div>
            </li>
          </Fragment>
        ))}
      </ol>
      <p className="muted small">
        걷는 시간은 직선 거리로 어림한 값이고, 영업 여부와 시간은 네이버지도에서 꼭 확인해 주세요. 공유 링크는 만든 날부터 90일 동안 열려요.
      </p>
    </section>
  )
}
