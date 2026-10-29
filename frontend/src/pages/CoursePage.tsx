import { Fragment, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { CATEGORY_LABEL, formatDistance, formatMinutes, ROLE_LABEL } from '../lib/format'
import { loadKeywords } from '../lib/keywords'
import { altIcon, jumpTo, stepIcon } from '../lib/naverMap'
import {
  clockText,
  defaultStay,
  initialPlan,
  isChanged,
  loadPlan,
  savePlan,
  timeline,
  toIcs,
  type Plan,
  type PlanRole,
  type PlanStep,
} from '../lib/plan'
import type { AlternativeResponse, CourseDetail } from '../lib/types'
import { useMap } from '../map/context'

const LETTERS = 'ABCDEFGHIJ'
const STAY_CHOICES = [20, 30, 45, 60, 75, 90, 120, 150, 180]
const ALT_LABEL: Record<PlanRole, string> = { spot: '다른 구경거리 보기', cafe: '다른 카페 보기', meal: '다른 식당 보기', mine: '' }

const roleLabel = (r: PlanRole) => (r === 'mine' ? '내 장소' : ROLE_LABEL[r])

/** 지도·목록에 같이 보이는 후보 목록: 근처 대안(그 단계를 바꿈) 또는 장소 찾기(그 자리에 더함) */
type Picker =
  | { kind: 'alt'; index: number; items: AlternativeResponse[] | null; error: string | null }
  | { kind: 'add'; index: number; items: AlternativeResponse[] | null; error: string | null; query: string }

// 코스 상세 = 내 일정: 지도에 단계 이름표와 동선, 왼쪽에 시간표.
// 장소 바꾸기(근처 대안)·더하기(이름으로 찾기)·빼기·순서 바꾸기, 출발 시간·머무는 시간 → 도착·출발 시간, 캘린더 저장
export default function CoursePage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { map } = useMap()
  const [course, setCourse] = useState<CourseDetail | null>(null)
  const [plan, setPlan] = useState<Plan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [names, setNames] = useState<Record<string, string>>({})
  const [picker, setPicker] = useState<Picker | null>(null)

  useEffect(() => {
    loadKeywords()
      .then((list) => setNames(Object.fromEntries(list.map((k) => [k.code, k.name]))))
      .catch(() => {})
  }, [])

  useEffect(() => {
    let cancelled = false
    setCourse(null)
    setPlan(null)
    setError(null)
    setPicker(null)
    api<CourseDetail>(`/courses/${id}`)
      .then((c) => {
        if (cancelled) return
        setCourse(c)
        setPlan(loadPlan(id, c))
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [id])

  const steps = useMemo(() => (plan ? timeline(plan) : []), [plan])
  const changed = plan && course ? isChanged(plan, course) : false

  // 단계 이름표·동선 (+ 열어 둔 후보 A~)
  useEffect(() => {
    if (!map || steps.length === 0) return
    const path = steps.map((s) => new naver.maps.LatLng(s.place.lat, s.place.lng))
    const overlays: { setMap(m: naver.maps.Map | null): void }[] = [
      new naver.maps.Polyline({ map, path, strokeColor: '#e8546b', strokeWeight: 4, strokeOpacity: 0.85, strokeStyle: 'shortdash' }),
      ...steps.map(
        (s, i) =>
          new naver.maps.Marker({
            map,
            position: new naver.maps.LatLng(s.place.lat, s.place.lng),
            icon: stepIcon(i + 1, roleLabel(s.role)),
            title: s.place.name,
            zIndex: 200,
          }),
      ),
    ]
    let bounds = path.slice(1).reduce((b, p) => b.extend(p), new naver.maps.LatLngBounds(path[0], path[0]))
    ;(picker?.items ?? []).forEach((a, i) => {
      const position = new naver.maps.LatLng(a.place.lat, a.place.lng)
      overlays.push(new naver.maps.Marker({ map, position, icon: altIcon(LETTERS[i]), title: a.place.name, zIndex: 100 }))
      bounds = bounds.extend(position)
    })
    map.fitBounds(bounds, { top: 60, right: 60, bottom: 60, left: 60 })
    return () => overlays.forEach((o) => o.setMap(null))
  }, [map, steps, picker?.items])

  function update(next: Plan) {
    setPlan(next)
    savePlan(id, next)
  }

  function updateSteps(fn: (list: PlanStep[]) => PlanStep[]) {
    if (plan) update({ ...plan, steps: fn(plan.steps) })
  }

  function focus(lat: number, lng: number) {
    if (map) jumpTo(map, lat, lng, 17)
  }

  function openAlternatives(index: number) {
    const step = plan?.steps[index]
    if (!step || step.courseOrder == null) return
    if (picker?.kind === 'alt' && picker.index === index) {
      setPicker(null)
      return
    }
    setPicker({ kind: 'alt', index, items: null, error: null })
    // 대안은 원래 코스의 그 단계 장소를 기준으로 서버가 찾는다
    api<AlternativeResponse[]>(`/courses/${id}/steps/${step.courseOrder}/alternatives`)
      .then((items) => setPicker((p) => (p?.kind === 'alt' && p.index === index ? { ...p, items } : p)))
      .catch((e: Error) => setPicker((p) => (p?.kind === 'alt' && p.index === index ? { ...p, error: e.message } : p)))
  }

  function openAdd(index: number) {
    setPicker((p) => (p?.kind === 'add' && p.index === index ? null : { kind: 'add', index, items: null, error: null, query: '' }))
  }

  function searchToAdd(e: FormEvent, index: number, query: string) {
    e.preventDefault()
    if (!plan) return
    const q = query.trim()
    if (!q) return
    // 넣을 자리 바로 앞 장소 근처에서 찾는다 (맨 앞이면 첫 장소 근처)
    const near = plan.steps[Math.max(0, index - 1)].place
    setPicker({ kind: 'add', index, items: null, error: null, query })
    api<AlternativeResponse[]>(`/places/nearby?lat=${near.lat}&lng=${near.lng}&q=${encodeURIComponent(q)}`)
      .then((items) => setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, items } : p)))
      .catch((err: Error) => setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, error: err.message } : p)))
  }

  function swap(index: number, alt: AlternativeResponse) {
    updateSteps((list) =>
      list.map((s, i) =>
        i === index ? { ...s, place: alt.place, reasons: alt.reasons, memo: null, origin: s.origin === 'added' ? 'added' : 'swap' } : s,
      ),
    )
    setPicker(null)
  }

  function add(index: number, found: AlternativeResponse) {
    const step: PlanStep = {
      key: `a${Date.now()}`,
      role: 'mine',
      place: found.place,
      reasons: found.reasons,
      stayMinutes: defaultStay(found.place.category),
      memo: null,
      courseOrder: null,
      origin: 'added',
    }
    updateSteps((list) => [...list.slice(0, index), step, ...list.slice(index)])
    setPicker(null)
  }

  function remove(index: number) {
    updateSteps((list) => list.filter((_, i) => i !== index))
    setPicker(null)
  }

  function move(index: number, delta: number) {
    updateSteps((list) => {
      const next = [...list]
      const [item] = next.splice(index, 1)
      next.splice(index + delta, 0, item)
      return next
    })
    setPicker(null)
  }

  function setStay(index: number, minutes: number) {
    updateSteps((list) => list.map((s, i) => (i === index ? { ...s, stayMinutes: minutes } : s)))
  }

  function reset() {
    if (!course || !plan) return
    // 날짜·출발 시간은 그대로 두고 장소·순서·머무는 시간만 되돌린다
    update({ ...initialPlan(course), date: plan.date, start: plan.start })
    setPicker(null)
  }

  function downloadIcs() {
    if (!course || !plan) return
    const blob = new Blob([toIcs(course.title, plan, steps, roleLabel)], { type: 'text/calendar;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${course.title}.ics`
    a.click()
    URL.revokeObjectURL(url)
  }

  if (error) {
    return (
      <section>
        <p className="notice">{error}</p>
        <Link to="/">처음으로</Link>
      </section>
    )
  }
  if (!course || !plan) return <p className="muted">불러오는 중…</p>

  const end = steps.length ? steps[steps.length - 1].depart : 0

  function pickerList(index: number, kind: Picker['kind']) {
    if (!picker || picker.index !== index || picker.kind !== kind) return null
    return (
      <ul className="alternatives">
        {picker.error && <li className="notice small">{picker.error}</li>}
        {kind === 'alt' && !picker.items && !picker.error && <li className="muted small">근처를 찾는 중…</li>}
        {picker.items && picker.items.length === 0 && (
          <li className="muted small">{kind === 'alt' ? '걸어서 10분 안에 바꿀 만한 곳이 없어요.' : '근처 2km 안에서 찾지 못했어요. 다른 이름으로 찾아 주세요.'}</li>
        )}
        {picker.items?.map((a, i) => (
          <li key={a.place.id} className="alternative">
            <button type="button" className="alt-letter" onClick={() => focus(a.place.lat, a.place.lng)} aria-label={`${a.place.name} 지도에서 보기`}>
              {LETTERS[i]}
            </button>
            <div className="step-body">
              <h3>
                {a.place.name}
                {a.place.branch && <span className="muted small"> {a.place.branch}</span>}
              </h3>
              <p className="muted small">
                {[
                  CATEGORY_LABEL[a.place.category] ?? a.place.category,
                  a.place.detailCategory && !/^[A-Z]{2}\d/.test(a.place.detailCategory) ? a.place.detailCategory : null,
                  kind === 'alt' ? `원래 장소에서 ${formatDistance(a.distanceMeters)}` : `${formatDistance(a.distanceMeters)} 거리`,
                ]
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
                <button type="button" className="swap-button" onClick={() => (kind === 'alt' ? swap(index, a) : add(index, a))}>
                  {kind === 'alt' ? '이걸로 바꾸기' : '여기에 넣기'}
                </button>
                <a href={a.place.naverMapUrl} target="_blank" rel="noreferrer" className="more">
                  네이버지도 ↗
                </a>
              </div>
            </div>
          </li>
        ))}
      </ul>
    )
  }

  function addSlot(index: number) {
    const open = picker?.kind === 'add' && picker.index === index
    return (
      <li className="add-slot">
        <button type="button" className="link-button" onClick={() => openAdd(index)} aria-expanded={open}>
          {open ? '닫기' : '+ 여기에 가고 싶은 곳 넣기'}
        </button>
        {open && picker?.kind === 'add' && (
          <>
            <form className="add-search" onSubmit={(e) => searchToAdd(e, index, picker.query)}>
              <input
                type="search"
                value={picker.query}
                autoFocus
                maxLength={30}
                placeholder="가게·장소 이름 (예: 블루보틀, 서점)"
                aria-label="넣을 장소 이름"
                onChange={(e) => setPicker({ ...picker, query: e.target.value })}
              />
              <button type="submit">찾기</button>
            </form>
            {pickerList(index, 'add')}
          </>
        )}
      </li>
    )
  }

  return (
    <section className="course">
      <button type="button" className="back" onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}>
        ← 목록으로
      </button>
      <h1>{course.title}</h1>
      <p className="meta muted">
        {[course.sido, course.sigungu, `약 ${formatMinutes(end - steps[0]?.arrive || 0)}`, course.kind === 'auto' ? '자동으로 만든 코스' : '추천 코스']
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

      <div className="schedule-bar">
        <label>
          <span>날짜</span>
          <input type="date" value={plan.date} onChange={(e) => update({ ...plan, date: e.target.value })} />
        </label>
        <label>
          <span>출발</span>
          <input type="time" value={plan.start} step={300} onChange={(e) => e.target.value && update({ ...plan, start: e.target.value })} />
        </label>
        <p className="schedule-end">
          끝 <strong>{clockText(end)}</strong>
        </p>
        <button type="button" className="secondary small-button" onClick={downloadIcs}>
          📅 캘린더에 저장
        </button>
      </div>

      {changed && (
        <p className="notice-soft">
          내가 바꾼 코스예요. 바꾼 내용은 이 브라우저에만 저장돼요.{' '}
          <button type="button" className="link-button" onClick={reset}>
            원래 코스로
          </button>
        </p>
      )}

      <ol className="steps">
        {addSlot(0)}
        {steps.map((s, i) => (
          <Fragment key={s.key}>
            {s.walkMinutes != null && <li className="walk muted">🚶 걸어서 약 {s.walkMinutes}분</li>}
            <li className="step">
              <button type="button" className="step-pin" onClick={() => focus(s.place.lat, s.place.lng)} aria-label={`${s.place.name} 지도에서 보기`}>
                {i + 1}
              </button>
              <div className="step-body">
                <p className="step-time">
                  <strong>
                    {clockText(s.arrive)} – {clockText(s.depart)}
                  </strong>
                  <select value={s.stayMinutes} onChange={(e) => setStay(i, Number(e.target.value))} aria-label={`${s.place.name}에 머무는 시간`}>
                    {[...new Set([...STAY_CHOICES, s.stayMinutes])]
                      .sort((a, b) => a - b)
                      .map((m) => (
                        <option key={m} value={m}>
                          {formatMinutes(m)} 머물기
                        </option>
                      ))}
                  </select>
                </p>
                <p className="step-role muted">
                  {roleLabel(s.role)} · {CATEGORY_LABEL[s.place.category] ?? s.place.category}
                </p>
                <h2>
                  {s.place.name}
                  {s.place.branch && <span className="muted small"> {s.place.branch}</span>}
                  {s.origin === 'swap' && <span className="swapped-tag">바꿈</span>}
                  {s.origin === 'added' && <span className="swapped-tag">내가 넣음</span>}
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
                {s.memo && <p>{s.memo}</p>}
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
                    네이버지도 ↗
                  </a>
                  {s.courseOrder != null && (
                    <button type="button" className="link-button" onClick={() => openAlternatives(i)} aria-expanded={picker?.kind === 'alt' && picker.index === i}>
                      {picker?.kind === 'alt' && picker.index === i ? '대안 닫기' : ALT_LABEL[s.role]}
                    </button>
                  )}
                  <span className="step-tools">
                    <button type="button" disabled={i === 0} onClick={() => move(i, -1)} aria-label="앞으로">
                      ▲
                    </button>
                    <button type="button" disabled={i === steps.length - 1} onClick={() => move(i, 1)} aria-label="뒤로">
                      ▼
                    </button>
                    <button type="button" disabled={steps.length <= 1} onClick={() => remove(i)}>
                      빼기
                    </button>
                  </span>
                </div>
                {pickerList(i, 'alt')}
              </div>
            </li>
            {addSlot(i + 1)}
          </Fragment>
        ))}
      </ol>
      <p className="muted small">
        장소는 리뷰·별점 없이 공공데이터(업종·지정 정보·출처)로 골랐어요. 걷는 시간은 직선 거리로 어림한 값이고, 영업 여부와 시간은
        네이버지도에서 꼭 확인해 주세요.
      </p>
    </section>
  )
}
