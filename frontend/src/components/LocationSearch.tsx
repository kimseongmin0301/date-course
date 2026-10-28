import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { pinIcon } from '../lib/naverMap'
import type { LocationResult } from '../lib/types'
import { useMap } from '../map/context'

const KIND_LABEL: Record<LocationResult['kind'], string> = {
  area: '지역',
  place: '명소',
  address: '주소',
}

/**
 * 지역 검색창: 동네 이름·명소·주소를 찾으면 첫 결과로 바로 onPick 하고(지도가 그곳으로), 고른 곳에 핀을 꽂는다.
 * 다른 결과는 아래에 남겨 두어 바꿔 고를 수 있다. 엔터나 찾기를 누를 때만 부른다 (Geocoding 이용 횟수 아끼기)
 */
export default function LocationSearch({ onPick }: { onPick: (location: LocationResult) => void }) {
  const { map } = useMap()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<LocationResult[] | null>(null)
  const [picked, setPicked] = useState<LocationResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 고른 곳 핀 (검색창이 화면에 있는 동안)
  useEffect(() => {
    if (!map || !picked) return
    const marker = new naver.maps.Marker({
      map,
      position: new naver.maps.LatLng(picked.lat, picked.lng),
      icon: pinIcon('●', 'target'),
      title: picked.name,
      zIndex: 300,
    })
    return () => marker.setMap(null)
  }, [map, picked])

  function pick(location: LocationResult) {
    setPicked(location)
    onPick(location)
  }

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
      .then((list) => {
        setResults(list)
        if (list.length > 0) pick(list[0])
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false))
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
      {results && results.length === 0 && <p className="muted small">찾지 못했어요. 다른 이름이나 주소로 찾아 주세요.</p>}
      {results && results.length > 1 && (
        <details className="location-more">
          <summary>다른 곳이었나요? ({results.length - 1}곳 더)</summary>
          <ul className="location-results">
            {results.map((r, i) => (
              <li key={`${r.kind}-${r.lat}-${r.lng}-${i}`}>
                <button type="button" className={r === picked ? 'on' : undefined} onClick={() => pick(r)}>
                  <span className="location-kind">{KIND_LABEL[r.kind]}</span>
                  <span className="location-name">{r.name}</span>
                  {r.detail && <span className="muted small">{r.detail}</span>}
                </button>
              </li>
            ))}
          </ul>
        </details>
      )}
      {picked && (
        <p className="location-picked small">
          📍 <strong>{picked.name}</strong> {picked.detail && <span className="muted">{picked.detail}</span>}
        </p>
      )}
    </div>
  )
}
