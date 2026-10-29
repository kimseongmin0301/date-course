import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AccountBox from '../components/AccountBox'
import { api } from '../lib/api'
import type { PlanSummary } from '../lib/types'

// 내 저장: 이 브라우저에서 저장한 일정 (로그인 없이 쿠키의 방문자 id 로). 최근 저장 순
export default function SavedPage() {
  const [plans, setPlans] = useState<PlanSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    api<PlanSummary[]>('/plans')
      .then(setPlans)
      .catch((e: Error) => setError(e.message))
  }

  useEffect(load, [])

  async function remove(courseId: number) {
    if (!window.confirm('이 일정을 지울까요?')) return
    try {
      await api<void>(`/plans/${courseId}`, { method: 'DELETE' })
      setPlans((list) => list?.filter((p) => p.courseId !== courseId) ?? null)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section>
      <h1>내 저장</h1>
      <p className="muted small">
        회원가입 없이 이 브라우저(쿠키)에 저장돼요. 브라우저를 바꾸거나 쿠키를 지우면 보이지 않으니, 다른 기기에서도 보려면 아래에서 코드를 만들어 주세요.
      </p>
      <AccountBox onChanged={load} />
      {error && <p className="notice">{error}</p>}
      {!plans && !error && <p className="muted">불러오는 중…</p>}
      {plans && plans.length === 0 && (
        <p className="empty muted">
          아직 저장한 일정이 없어요. 코스를 열어 시간을 정하고 <strong>💾 저장</strong>을 눌러 보세요.{' '}
          <Link to="/">코스 찾기</Link>
        </p>
      )}
      <ul className="saved-list">
        {plans?.map((p) => (
          <li key={p.courseId} className="saved-item">
            <h2>
              <Link to={`/courses/${p.courseId}`}>{p.title}</Link>
            </h2>
            <p className="muted small">
              {[p.sigungu ?? p.sido, p.date ? dateLabel(p.date) : null, `${p.start} – ${p.end}`].filter(Boolean).join(' · ')}
            </p>
            <p className="steps-line">{p.stepNames.join(' → ')}</p>
            <div className="step-actions">
              <Link to={`/courses/${p.courseId}`} className="more">
                일정 열기 →
              </Link>
              <button type="button" className="link-button" onClick={() => remove(p.courseId)}>
                지우기
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** "2026-11-01" → "11월 1일 (일)" */
function dateLabel(date: string): string {
  const d = new Date(`${date}T00:00:00`)
  if (Number.isNaN(d.getTime())) return date
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${'일월화수목금토'[d.getDay()]})`
}
