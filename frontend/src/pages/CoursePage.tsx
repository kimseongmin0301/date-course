import { Fragment, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { CATEGORY_LABEL, formatDistance, formatMinutes, ROLE_LABEL } from '../lib/format'
import { loadKeywords } from '../lib/keywords'
import { altIcon, distanceMeters, jumpTo, stepIcon } from '../lib/naverMap'
import {
  clockText,
  CUSTOM_NAME_MAX,
  customPlace,
  defaultStay,
  fromSaved,
  initialPlan,
  isChanged,
  loadDraft,
  loadSaved,
  samePlan,
  saveDraft,
  saveToServer,
  STAY_MAX,
  timeline,
  timeValue,
  toMinutes,
  type Plan,
  type PlanRole,
  type PlanStep,
} from '../lib/plan'
import type { AddressResult, AlternativeResponse, CourseDetail, NaverImage, Place } from '../lib/types'
import StayInput from '../components/StayInput'
import { useMap } from '../map/context'

const LETTERS = 'ABCDEFGHIJ'
const ALT_LABEL: Record<PlanRole, string> = { spot: '다른 구경거리 보기', cafe: '다른 카페 보기', meal: '다른 식당 보기', mine: '' }

const roleLabel = (r: PlanRole) => (r === 'mine' ? '내 장소' : ROLE_LABEL[r])

/** 저장 시각 → "10월 29일 14:02" */
function savedLabel(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '방금'
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 데이터에 없는 가게를 이름·주소로 직접 넣는 양식 */
interface CustomForm {
  name: string
  address: string
  results: AddressResult[] | null
  error: string | null
  loading: boolean
}

/** 지도·목록에 같이 보이는 후보 목록: 근처 대안(그 단계를 바꿈) 또는 장소 찾기(그 자리에 더함) */
type Picker =
  | { kind: 'alt'; index: number; items: AlternativeResponse[] | null; error: string | null }
  | {
      kind: 'add'
      index: number
      items: AlternativeResponse[] | null
      error: string | null
      query: string
      custom: CustomForm | null
      /** ours: 우리 장소 데이터, naver: 네이버 지역 검색 (우리 데이터에 없을 때) */
      source: 'ours' | 'naver'
    }

/** 사진이 없는 장소의 "네이버 이미지 보기" (단계 key 마다) */
interface Photos {
  items: NaverImage[] | null
  error: string | null
}

/** 이미지 검색어: "가게 이름 시도 시군구" (시도는 앞 두 글자: 울산광역시 → 울산) */
function imageQuery(place: Place): string {
  return [place.name, place.branch, place.sido?.slice(0, 2), place.sigungu].filter(Boolean).join(' ')
}

/** 네이버 오픈 API 를 쓴 곳에 붙이는 링크 (NAVER API HUB 상표·BI 가이드) */
const NAVER_API_HUB = 'https://www.ncloud.com/product/applicationService/NAVERAPIHUB'

// 코스 상세 = 내 일정: 지도에 단계 이름표와 동선, 왼쪽에 시간표.
// 장소 바꾸기(근처 대안)·더하기(이름으로 찾기)·빼기·순서 바꾸기, 출발 시간·머무는 시간(분)·도착 시간 고정 → 시간표.
// "저장"하면 서버에 둔다 (쿠키의 방문자 id 로, 다음에 와도 그대로). 저장 전 고친 내용은 브라우저에 임시로 둔다
export default function CoursePage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { map } = useMap()
  const [course, setCourse] = useState<CourseDetail | null>(null)
  const [plan, setPlan] = useState<Plan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [names, setNames] = useState<Record<string, string>>({})
  const [picker, setPicker] = useState<Picker | null>(null)
  /** 서버에 저장한 일정 (저장 안 했으면 null) */
  const [saved, setSaved] = useState<{ plan: Plan; at: string } | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [photos, setPhotos] = useState<Record<string, Photos>>({})

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
    setSaved(null)
    setSaveError(null)
    Promise.all([api<CourseDetail>(`/courses/${id}`), loadSaved(id)])
      .then(([c, savedPlan]) => {
        if (cancelled) return
        setCourse(c)
        const fromServer = savedPlan ? fromSaved(savedPlan, c) : null
        if (fromServer && savedPlan) setSaved({ plan: fromServer, at: savedPlan.savedAt })
        // 저장 안 한 고친 내용 → 저장한 일정 → 원래 코스 순서로
        setPlan(loadDraft(id, c) ?? fromServer ?? initialPlan(c))
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [id])

  const steps = useMemo(() => (plan ? timeline(plan) : []), [plan])
  const changed = plan && course ? isChanged(plan, course) : false
  const unsaved = plan && course ? (saved ? !samePlan(plan, saved.plan) : isChanged(plan, course)) : false
  const customList = picker?.kind === 'add' ? picker.custom?.results : null
  const customResults = useMemo(
    () => customList?.map((r) => ({ lat: r.lat, lng: r.lng, title: r.roadAddress || r.jibunAddress })) ?? null,
    [customList],
  )

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
    // 후보 A~: 근처 대안·찾은 장소, 직접 넣기면 찾은 주소
    const marks = customResults ?? (picker?.items ?? []).map((a) => ({ lat: a.place.lat, lng: a.place.lng, title: a.place.name }))
    marks.forEach((m, i) => {
      const position = new naver.maps.LatLng(m.lat, m.lng)
      overlays.push(new naver.maps.Marker({ map, position, icon: altIcon(LETTERS[i]), title: m.title, zIndex: 100 }))
      bounds = bounds.extend(position)
    })
    map.fitBounds(bounds, { top: 60, right: 60, bottom: 60, left: 60 })
    return () => overlays.forEach((o) => o.setMap(null))
  }, [map, steps, picker?.items, customResults])

  function update(next: Plan) {
    setPlan(next)
    saveDraft(id, next)
  }

  async function save() {
    if (!plan) return
    setSaving(true)
    setSaveError(null)
    try {
      const res = await saveToServer(id, plan)
      setSaved({ plan, at: res.savedAt })
      saveDraft(id, null)
    } catch (e) {
      setSaveError((e as Error).message)
    } finally {
      setSaving(false)
    }
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
    setPicker((p) =>
      p?.kind === 'add' && p.index === index ? null : { kind: 'add', index, items: null, error: null, query: '', custom: null, source: 'ours' },
    )
  }

  function searchToAdd(e: FormEvent, index: number, query: string) {
    e.preventDefault()
    if (!plan) return
    const q = query.trim()
    if (!q) return
    // 넣을 자리 바로 앞 장소 근처에서 찾는다 (맨 앞이면 첫 장소 근처)
    const near = plan.steps[Math.max(0, index - 1)].place
    setPicker({ kind: 'add', index, items: null, error: null, query, custom: null, source: 'ours' })
    api<AlternativeResponse[]>(`/places/nearby?lat=${near.lat}&lng=${near.lng}&q=${encodeURIComponent(q)}`)
      .then((items) => {
        // 우리 데이터에 없으면 바로 네이버 지역 검색으로
        if (items.length === 0) searchNaver(index, q)
        else setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, items } : p))
      })
      .catch((err: Error) => setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, error: err.message } : p)))
  }

  /** 네이버 지역 검색 (검색어 앞에 앞 장소의 동네를 붙여 서버가 찾는다) */
  function searchNaver(index: number, query: string) {
    if (!plan) return
    const q = query.trim()
    if (!q) return
    const near = plan.steps[Math.max(0, index - 1)].place
    const area = [near.sido, near.sigungu].filter(Boolean).join(' ')
    setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, items: null, error: null, custom: null, source: 'naver' } : p))
    api<AlternativeResponse[]>(`/places/naver?lat=${near.lat}&lng=${near.lng}&area=${encodeURIComponent(area)}&q=${encodeURIComponent(q)}`)
      .then((items) => setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, items } : p)))
      .catch((err: Error) => setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, items: [], error: err.message } : p)))
  }

  /** 직접 넣기 양식 고치기 (열려 있는 그 자리 것만) */
  function setCustom(index: number, fn: (c: CustomForm) => CustomForm) {
    setPicker((p) => (p?.kind === 'add' && p.index === index && p.custom ? { ...p, custom: fn(p.custom) } : p))
  }

  function openCustom(index: number, name: string) {
    setPicker((p) =>
      p?.kind === 'add' && p.index === index
        ? { ...p, items: null, custom: { name: name.trim().slice(0, CUSTOM_NAME_MAX), address: '', results: null, error: null, loading: false } }
        : p,
    )
  }

  function findAddress(e: FormEvent, index: number, address: string) {
    e.preventDefault()
    const q = address.trim()
    if (q.length < 2) {
      setCustom(index, (c) => ({ ...c, error: '주소를 두 글자 이상 적어 주세요.' }))
      return
    }
    setCustom(index, (c) => ({ ...c, loading: true, error: null, results: null }))
    api<AddressResult[]>(`/addresses?q=${encodeURIComponent(q)}`)
      .then((results) => setCustom(index, (c) => ({ ...c, loading: false, results })))
      .catch((err: Error) => setCustom(index, (c) => ({ ...c, loading: false, error: err.message })))
  }

  function addCustom(index: number, name: string, address: AddressResult) {
    const place = customPlace(name, address)
    add(index, { place, reasons: ['직접 넣은 곳'], distanceMeters: 0 })
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
      fixedArrive: null,
      memo: null,
      courseOrder: null,
      origin: 'added',
    }
    updateSteps((list) => [...list.slice(0, index), step, ...list.slice(index)])
    setPicker(null)
  }

  /** 네이버 이미지 검색 (누를 때만 부른다: 지역 검색과 하루 한도를 나눠 써서) */
  function loadPhotos(key: string, place: Place) {
    if (photos[key]) {
      setPhotos((p) => {
        const next = { ...p }
        delete next[key]
        return next
      })
      return
    }
    setPhotos((p) => ({ ...p, [key]: { items: null, error: null } }))
    api<NaverImage[]>(`/places/images?q=${encodeURIComponent(imageQuery(place))}`)
      .then((items) => setPhotos((p) => ({ ...p, [key]: { items, error: null } })))
      .catch((err: Error) => setPhotos((p) => ({ ...p, [key]: { items: [], error: err.message } })))
  }

  function photoStrip(key: string, place: Place) {
    const p = photos[key]
    if (!p) return null
    const more = `https://search.naver.com/search.naver?where=image&query=${encodeURIComponent(imageQuery(place))}`
    return (
      <div className="naver-photos">
        {p.error && <p className="notice small">{p.error}</p>}
        {!p.items && !p.error && <p className="muted small">네이버에서 이미지를 찾는 중…</p>}
        {p.items && p.items.length === 0 && !p.error && <p className="muted small">네이버 이미지 검색 결과가 없어요.</p>}
        {p.items && p.items.length > 0 && (
          <>
            <div className="photo-row">
              {p.items.map((img) => (
                <a key={img.thumbnail} href={more} target="_blank" rel="noreferrer" title={img.title}>
                  <img src={img.thumbnail} alt={img.title} referrerPolicy="no-referrer" />
                </a>
              ))}
            </div>
            <p className="muted small">
              네이버 이미지 검색 결과예요. 이 가게 사진이 아닐 수도 있어요 ·{' '}
              <a href={more} target="_blank" rel="noreferrer">
                더 보기 ↗
              </a>{' '}
              ·{' '}
              <a href={NAVER_API_HUB} target="_blank" rel="noreferrer">
                NAVER 오픈 API
              </a>
            </p>
          </>
        )}
      </div>
    )
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
    if (!Number.isFinite(minutes)) return
    const m = Math.min(STAY_MAX, Math.max(1, Math.round(minutes)))
    updateSteps((list) => list.map((s, i) => (i === index ? { ...s, stayMinutes: m } : s)))
  }

  /** 도착 시간 고정 (예약 등). 출발 시간보다 이르면 다음날로 본다. null 이면 앞 일정에 이어서 */
  function setArrive(index: number, value: string | null) {
    if (!plan) return
    let fixed: number | null = null
    if (value) {
      fixed = toMinutes(value)
      if (fixed < toMinutes(plan.start)) fixed += 1440
    }
    updateSteps((list) => list.map((s, i) => (i === index ? { ...s, fixedArrive: fixed } : s)))
  }

  function reset() {
    if (!course || !plan) return
    // 날짜·출발 시간은 그대로 두고 장소·순서·머무는 시간·고정 시간만 되돌린다
    update({ ...initialPlan(course), date: plan.date, start: plan.start })
    setPicker(null)
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
        {picker.kind === 'add' && picker.source === 'naver' && !picker.items && <li className="muted small">우리 데이터에 없어 네이버에서 찾는 중…</li>}
        {picker.kind === 'add' && picker.source === 'naver' && picker.items && picker.items.length > 0 && (
          <li className="muted small">
            우리 데이터에 없어 네이버 지역 검색에서 찾았어요 ·{' '}
            <a href={NAVER_API_HUB} target="_blank" rel="noreferrer">
              NAVER 오픈 API
            </a>
          </li>
        )}
        {picker.items && picker.items.length === 0 && (kind === 'alt' || (picker.kind === 'add' && picker.source === 'naver')) && (
          <li className="muted small">
            {kind === 'alt' ? (
              '걸어서 10분 안에 바꿀 만한 곳이 없어요.'
            ) : (
              <>
                {!picker.error && '근처에서 찾지 못했어요. 새로 생겼거나 다른 이름으로 등록된 가게일 수 있어요.'}
                <button type="button" className="swap-button not-found-add" onClick={() => picker.kind === 'add' && openCustom(index, picker.query)}>
                  이름과 주소로 직접 넣기
                </button>
              </>
            )}
          </li>
        )}
        {picker.items?.map((a, i) => (
          <li key={`${a.place.id}-${a.place.lat}-${a.place.lng}`} className="alternative">
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

  function customForm(index: number, c: CustomForm) {
    if (!plan) return null
    const near = plan.steps[Math.max(0, index - 1)].place
    const name = c.name.trim()
    return (
      <div className="custom-place">
        <p className="muted small">우리 데이터에 없는 가게는 이름과 주소로 넣어요. 이 일정에만 보여요.</p>
        <input
          type="text"
          value={c.name}
          maxLength={CUSTOM_NAME_MAX}
          placeholder="가게 이름 (예: 카페 높)"
          aria-label="직접 넣을 가게 이름"
          onChange={(e) => setCustom(index, (f) => ({ ...f, name: e.target.value }))}
        />
        <form className="add-search" onSubmit={(e) => findAddress(e, index, c.address)}>
          <input
            type="search"
            value={c.address}
            maxLength={60}
            placeholder="주소 (예: 울산 북구 정자1길 111)"
            aria-label="직접 넣을 가게 주소"
            onChange={(e) => setCustom(index, (f) => ({ ...f, address: e.target.value }))}
          />
          <button type="submit" disabled={c.loading}>
            {c.loading ? '찾는 중…' : '주소 찾기'}
          </button>
        </form>
        {(c.error || c.results) && (
          <ul className="alternatives">
            {c.error && <li className="notice small">{c.error}</li>}
            {c.results?.length === 0 && <li className="muted small">주소를 찾지 못했어요. 도로명 주소(예: 정자1길 111)로 적어 주세요.</li>}
            {c.results?.map((r, i) => (
              <li key={`${r.lat},${r.lng},${i}`} className="alternative">
                <button type="button" className="alt-letter" onClick={() => focus(r.lat, r.lng)} aria-label={`${r.roadAddress || r.jibunAddress} 지도에서 보기`}>
                  {LETTERS[i]}
                </button>
                <div className="step-body">
                  <h3>{r.roadAddress || r.jibunAddress}</h3>
                  <p className="muted small">
                    {[r.roadAddress ? r.jibunAddress : null, `${formatDistance(Math.round(distanceMeters(near.lat, near.lng, r.lat, r.lng)))} 거리`]
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                  <div className="step-actions">
                    <button type="button" className="swap-button" disabled={!name} onClick={() => addCustom(index, name, r)}>
                      {name ? `"${name}" 여기에 넣기` : '가게 이름을 먼저 적어 주세요'}
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
        <button type="button" className="link-button small" onClick={() => setPicker((p) => (p?.kind === 'add' && p.index === index ? { ...p, custom: null } : p))}>
          ← 이름으로 다시 찾기
        </button>
      </div>
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
            {!picker.custom && pickerList(index, 'add')}
            {picker.custom ? customForm(index, picker.custom) : picker.items?.length === 0 ? null : (
              <p className="small find-more">
                찾는 곳이 없나요?{' '}
                {picker.source === 'ours' && picker.items && picker.query.trim() && (
                  <>
                    <button type="button" className="link-button" onClick={() => searchNaver(index, picker.query)}>
                      네이버에서 찾기
                    </button>
                    {' · '}
                  </>
                )}
                <button type="button" className="link-button" onClick={() => openCustom(index, picker.query)}>
                  주소로 직접 넣기
                </button>
              </p>
            )}
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
          <input type="time" value={plan.start} step={60} onChange={(e) => e.target.value && update({ ...plan, start: e.target.value })} />
        </label>
        <p className="schedule-end">
          끝 <strong>{clockText(end)}</strong>
        </p>
        <button type="button" className="primary save-button" onClick={save} disabled={saving || (!!saved && !unsaved)}>
          {saving ? '저장 중…' : saved && !unsaved ? '✓ 저장됨' : '💾 저장'}
        </button>
        <p className="save-state muted small">
          {saveError ? (
            <span className="notice">{saveError}</span>
          ) : saved ? (
            unsaved ? (
              '저장한 뒤 고친 내용이 있어요. 다시 저장해 주세요.'
            ) : (
              <>
                {savedLabel(saved.at)}에 저장했어요. <Link to="/saved">내 저장</Link>에서 다시 볼 수 있어요.
              </>
            )
          ) : (
            <>
              저장하면 이 브라우저에서 다음에 와도 그대로 열려요. 다른 기기에서도 보려면 <Link to="/saved">내 저장</Link>에서 코드를 만들어 주세요.
            </>
          )}
        </p>
      </div>

      {changed && (
        <p className="notice-soft">
          원래 코스에서 바꾼 일정이에요.{' '}
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
                <div className="step-time">
                  <label className={s.fixedArrive != null ? 'fixed' : undefined}>
                    <span>도착</span>
                    <input
                      type="time"
                      step={60}
                      value={timeValue(s.arrive)}
                      onChange={(e) => setArrive(i, e.target.value || null)}
                      aria-label={`${s.place.name} 도착 시간`}
                    />
                  </label>
                  <span className="stay">
                    <StayInput value={s.stayMinutes} max={STAY_MAX} label={`${s.place.name}에 머무는 시간(분)`} onChange={(m) => setStay(i, m)} />
                    <span>분 머물기</span>
                  </span>
                  <span className="depart">→ {clockText(s.depart)} 출발</span>
                  {s.fixedArrive != null && (
                    <button type="button" className="link-button" onClick={() => setArrive(i, null)}>
                      도착 고정 풀기
                    </button>
                  )}
                </div>
                {s.late && (
                  <p className="notice small">앞 일정이 늦게 끝나 {clockText(s.fixedArrive ?? 0)}보다 늦게 도착해요.</p>
                )}
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
                {(s.place.category === 'custom' || s.place.category === 'naver') && (
                  <p className="muted small">우리 데이터에 없는 곳이라 영업 정보가 없어요. 네이버지도에서 확인해 주세요.</p>
                )}
                {s.place.phone && (
                  <p className="small">
                    <a href={`tel:${s.place.phone}`}>{s.place.phone}</a>
                  </p>
                )}
                <div className="step-actions">
                  <a href={s.place.naverMapUrl} target="_blank" rel="noreferrer" className="more">
                    네이버지도 ↗
                  </a>
                  {!s.place.imageUrl && (
                    <button type="button" className="link-button" onClick={() => loadPhotos(s.key, s.place)} aria-expanded={!!photos[s.key]}>
                      {photos[s.key] ? '이미지 닫기' : '📷 네이버 이미지 보기'}
                    </button>
                  )}
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
                {!s.place.imageUrl && photoStrip(s.key, s.place)}
                {pickerList(i, 'alt')}
              </div>
            </li>
            {addSlot(i + 1)}
          </Fragment>
        ))}
      </ol>
      <p className="muted small">
        도착 시간을 직접 고치면 그 시간으로 고정돼요(예약 등). 장소는 리뷰·별점 없이 공공데이터(업종·지정 정보·출처)로 골랐어요. 걷는 시간은 직선 거리로 어림한 값이고, 영업 여부와 시간은
        네이버지도에서 꼭 확인해 주세요.
      </p>
    </section>
  )
}
