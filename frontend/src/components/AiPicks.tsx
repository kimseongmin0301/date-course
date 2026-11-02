import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { formatMinutes } from '../lib/format'

interface Pick {
  courseId: number
  title: string
  sigungu: string | null
  totalMinutes: number | null
  stepNames: string[]
  reason: string | null
  inRanking: boolean
}

interface AiRecommend {
  ai: boolean
  message: string | null
  candidates: number
  picks: Pick[]
}

/**
 * AI 추천 (실험 기능, RAG): AI 검색 문장(q)에 맞는 코스를 지금 순위·비슷한 코스 후보 안에서만 고른다.
 * 누를 때만 부르고(서버 GPU 를 포트폴리오 챗봇과 나눠 씀), "다시 추천"은 본 코스를 빼고 다시 고른다.
 */
export default function AiPicks({ q, lat, lng, radius, keywords, sido, scene, food, shop, onShow }: {
  q: string
  /** 지점 반경 (시도 전체·전국이면 null) */
  lat: number | null
  lng: number | null
  radius: number | null
  keywords: string[]
  sido: string[]
  scene: string | null
  food: string | null
  /** 가게 이름으로 찾았으면 그 가게 id (그 가게가 든 코스가 후보 위로) */
  shop: number | null
  onShow: (courseId: number) => boolean
}) {
  const [result, setResult] = useState<AiRecommend | null>(null)
  const [seen, setSeen] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function ask(exclude: number[]) {
    setBusy(true)
    setError(null)
    try {
      const res = await api<AiRecommend>('/ai/recommend', {
        method: 'POST',
        body: JSON.stringify({ q, lat, lng, radius, kw: keywords, exclude, sido, scene, food, shop }),
      })
      setResult(res)
      setSeen([...exclude, ...res.picks.map((p) => p.courseId)])
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="ai-picks">
      <p className="ai-label">
        ✨ AI 추천 <span className="ai-badge">실험 기능</span>
      </p>
      <p className="muted small">"{q}"에 맞는 코스를 지금 목록의 후보 안에서만 골라요. 서버 안 AI 라 20~40초 걸려요.</p>
      {!result && (
        <button type="button" className="secondary small-button" onClick={() => ask([])} disabled={busy}>
          {busy ? 'AI 가 고르는 중…' : '이 요청에 맞는 코스 고르기'}
        </button>
      )}
      {error && <p className="notice small">{error}</p>}
      {result && (
        <>
          {result.message && <p className="notice-soft small">{result.message}</p>}
          <ol className="ai-pick-list">
            {result.picks.map((p) => (
              <li key={p.courseId}>
                <strong>{p.title}</strong>
                <span className="muted small"> · {[p.sigungu, p.totalMinutes ? formatMinutes(p.totalMinutes) : null].filter(Boolean).join(' · ')}</span>
                <p className="small steps-line">{p.stepNames.join(' → ')}</p>
                {p.reason && <p className="small ai-reason">💬 {p.reason}</p>}
                <div className="step-actions">
                  {p.inRanking && (
                    <button type="button" className="link-button small" onClick={() => onShow(p.courseId)}>
                      지도에서 보기
                    </button>
                  )}
                  <Link to={`/courses/${p.courseId}`} className="more">
                    코스 자세히 보기 →
                  </Link>
                </div>
              </li>
            ))}
          </ol>
          <p className="muted small">
            후보 {result.candidates}개 중에서 {result.ai ? 'AI 가 골랐어요. 이유는 AI 가 쓴 것이라 틀릴 수 있어요.' : '순위대로 골랐어요.'}
          </p>
          <button type="button" className="link-button small" onClick={() => ask(seen)} disabled={busy}>
            {busy ? '다시 고르는 중…' : '마음에 안 들어요, 다시 추천'}
          </button>
        </>
      )}
    </div>
  )
}
