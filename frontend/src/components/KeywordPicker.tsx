import { useEffect, useState } from 'react'
import { KIND_LABEL, loadKeywords, MAX_KEYWORDS, SINGLE_KINDS } from '../lib/keywords'
import type { Keyword, KeywordKind } from '../lib/types'

interface Props {
  value: string[]
  onChange: (codes: string[]) => void
  /** 한 줄로 접어서 보여줄지 (검색 결과 화면 위) */
  compact?: boolean
}

/** 키워드 칩: 모두 합쳐 3개까지, 실내외·예산은 하나만 */
export default function KeywordPicker({ value, onChange, compact }: Props) {
  const [keywords, setKeywords] = useState<Keyword[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadKeywords().then(setKeywords).catch((e: Error) => setError(e.message))
  }, [])

  function toggle(k: Keyword) {
    if (value.includes(k.code)) {
      onChange(value.filter((c) => c !== k.code))
      return
    }
    let next = value
    if (SINGLE_KINDS.includes(k.kind)) {
      const sameKind = keywords.filter((o) => o.kind === k.kind).map((o) => o.code)
      next = next.filter((c) => !sameKind.includes(c))
    }
    if (next.length >= MAX_KEYWORDS) {
      return
    }
    onChange([...next, k.code])
  }

  if (error) return <p className="muted">{error}</p>

  const kinds = [...new Set(keywords.map((k) => k.kind))] as KeywordKind[]
  const full = value.length >= MAX_KEYWORDS

  return (
    <div className={compact ? 'keywords compact' : 'keywords'}>
      {kinds.map((kind) => (
        <div key={kind} className="keyword-group">
          {!compact && <span className="keyword-kind">{KIND_LABEL[kind]}</span>}
          <div className="chips">
            {keywords
              .filter((k) => k.kind === kind)
              .map((k) => {
                const on = value.includes(k.code)
                // 꽉 찼어도 실내외·예산은 같은 분류 안에서 바꿀 수 있다
                const swappable = SINGLE_KINDS.includes(k.kind) && keywords.some((o) => o.kind === k.kind && value.includes(o.code))
                const blocked = !on && full && !swappable
                return (
                  <button
                    key={k.code}
                    type="button"
                    className={on ? 'chip on' : 'chip'}
                    aria-pressed={on}
                    disabled={blocked}
                    onClick={() => toggle(k)}
                  >
                    {k.name}
                  </button>
                )
              })}
          </div>
        </div>
      ))}
      {!compact && <p className="hint muted">{value.length}/{MAX_KEYWORDS}개 골랐어요 · 안 골라도 돼요</p>}
    </div>
  )
}
