import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import type { LocationResult } from '../lib/types'

const KIND_LABEL: Record<LocationResult['kind'], string> = {
  area: '지역',
  place: '명소',
  address: '주소',
}

/** 지역 검색창: 동네 이름·명소·주소를 찾아 고르면 onPick (엔터나 찾기를 누를 때만 부른다: Geocoding 이용 횟수 아끼기) */
export default function LocationSearch({ onPick }: { onPick: (location: LocationResult) => void }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<LocationResult[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    const q = query.trim()
    if (q.length < 2) {
      setError('두 글자 이상 적어 주세요.')
      return
    }
    setLoading(true)
    setError(null)
    api<LocationResult[]>(`/locations?q=${encodeURIComponent(q)}`)
      .then(setResults)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false))
  }

  function pick(location: LocationResult) {
    setResults(null)
    setQuery(location.name)
    onPick(location)
  }

  return (
    <div className="location-search">
      <form onSubmit={submit} role="search">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="동네, 명소, 주소 (예: 성수동, 경복궁)"
          aria-label="지역 검색"
          maxLength={40}
        />
        <button type="submit" disabled={loading}>
          {loading ? '찾는 중' : '찾기'}
        </button>
      </form>
      {error && <p className="notice small">{error}</p>}
      {results && (
        <ul className="location-results">
          {results.length === 0 && <li className="muted small">찾지 못했어요. 다른 이름이나 주소로 찾아 주세요.</li>}
          {results.map((r, i) => (
            <li key={`${r.kind}-${r.lat}-${r.lng}-${i}`}>
              <button type="button" onClick={() => pick(r)}>
                <span className="location-kind">{KIND_LABEL[r.kind]}</span>
                <span className="location-name">{r.name}</span>
                {r.detail && <span className="muted small">{r.detail}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
