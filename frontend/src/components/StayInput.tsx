import { useState } from 'react'

const STEP = 10

/**
 * 머무는 시간(분) 입력. 휴대폰에서 숫자를 다 지우고 새로 쓸 수 있게, 고치는 동안은 빈칸도 그대로 두고
 * 칸을 벗어나거나 엔터를 누를 때 확정한다(비었으면 원래 값으로). 맞는 숫자를 치는 동안에는 시간표에 바로 반영.
 * 옆의 −10 / +10 버튼으로 손가락 한 번에 고친다.
 */
export default function StayInput({ value, max, label, onChange }: { value: number; max: number; label: string; onChange: (minutes: number) => void }) {
  const [draft, setDraft] = useState<string | null>(null)
  const clamp = (n: number) => Math.min(max, Math.max(1, Math.round(n)))

  function commit(text: string) {
    const n = Number(text)
    if (text !== '' && Number.isFinite(n)) onChange(clamp(n))
    setDraft(null)
  }

  function step(delta: number) {
    setDraft(null)
    onChange(clamp(value + delta))
  }

  return (
    <span className="stay-input">
      <button type="button" onClick={() => step(-STEP)} disabled={value <= 1} aria-label={`${label} ${STEP}분 줄이기`}>
        −{STEP}
      </button>
      <input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        maxLength={3}
        value={draft ?? String(value)}
        onFocus={(e) => e.target.select()}
        onChange={(e) => {
          const text = e.target.value.replace(/[^0-9]/g, '').slice(0, 3)
          setDraft(text)
          const n = Number(text)
          if (text !== '' && n >= 1 && n <= max) onChange(n)
        }}
        onBlur={(e) => commit(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur()
        }}
        aria-label={label}
      />
      <button type="button" onClick={() => step(STEP)} disabled={value >= max} aria-label={`${label} ${STEP}분 늘리기`}>
        +{STEP}
      </button>
    </span>
  )
}
