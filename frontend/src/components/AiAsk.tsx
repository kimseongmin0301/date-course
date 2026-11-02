import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import type { LocationResult } from '../lib/types'

export interface AiParse {
  area: LocationResult | null
  keywords: string[]
  aiUsed: boolean
  understood: string
  /** 시도 범위 ("부산" → 부산광역시, "전라도" → 둘). area 가 없을 때 시도 전체에서 찾는다 */
  sidos: string[]
  /** 풍경: sea(바다)·mountain(산·숲)·water(강·호수) */
  scene: string | null
  rainy: boolean
}

/**
 * 말로 찾기 (실험 기능): "비 오는 날 성수동 조용한 데이트, 5만원" → 지역·키워드 칩.
 * 규칙이 먼저 알아듣고 모자라면 서버 안 AI 가 거든다(그때는 20~40초). 알아들으면 바로 코스 순위로 가고, 거기서 지역·키워드 칩으로 고친다.
 */
export default function AiAsk({ onParsed }: { onParsed: (q: string, result: AiParse) => void }) {
  const [q, setQ] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<AiParse | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const text = q.trim()
    if (text.length < 2) return
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      const res = await api<AiParse>('/ai/parse', { method: 'POST', body: JSON.stringify({ q: text }) })
      setResult(res)
      onParsed(text, res)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="ai-ask">
      <p className="ai-label">
        말로 찾기 <span className="ai-badge">실험 기능</span>
      </p>
      <form className="add-search" onSubmit={submit}>
        <input
          type="search"
          value={q}
          maxLength={100}
          onChange={(e) => setQ(e.target.value)}
          placeholder="예: 비 오는 날 성수동에서 조용한 데이트, 5만원"
          aria-label="원하는 데이트를 말로 적기"
        />
        <button type="submit" disabled={busy || q.trim().length < 2}>
          {busy ? '알아듣는 중…' : '알아듣기'}
        </button>
      </form>
      {busy && <p className="muted small">규칙으로 모자라면 서버 안 AI 가 거들어요. 20~40초 걸릴 수 있어요.</p>}
      {error && <p className="notice small">{error}</p>}
      {result && (
        <p className="small ai-understood">
          이렇게 이해했어요: <strong>{result.understood}</strong>
          {result.aiUsed && <span className="muted"> (AI 가 일부를 거들었어요)</span>}
          <br />
          <span className="muted">이 조건으로 코스 순위를 바로 보여드려요. 틀렸으면 거기서 고쳐 주세요.</span>
        </p>
      )}
    </div>
  )
}
