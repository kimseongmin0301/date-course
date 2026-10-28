import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import KeywordPicker from '../components/KeywordPicker'
import LocationSearch from '../components/LocationSearch'
import { AREAS } from '../lib/areas'
import { jumpTo, visibleRadius, zoomForRadius } from '../lib/naverMap'
import { useMap } from '../map/context'

// 홈: 지도를 옮기거나 인기 지역·내 주변으로 고르고, 키워드를 골라 코스 순위로 간다
export default function HomePage() {
  const { map, error } = useMap()
  const navigate = useNavigate()
  const [keywords, setKeywords] = useState<string[]>([])
  const [locating, setLocating] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  function moveTo(lat: number, lng: number, radius: number) {
    if (map) jumpTo(map, lat, lng, zoomForRadius(radius))
  }

  function nearMe() {
    if (!navigator.geolocation) {
      setNotice('이 브라우저는 위치를 알려 주지 않아요.')
      return
    }
    setLocating(true)
    setNotice(null)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false)
        moveTo(pos.coords.latitude, pos.coords.longitude, 2000)
      },
      () => {
        setLocating(false)
        setNotice('위치를 가져오지 못했어요. 지도를 직접 옮겨 주세요.')
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 600000 },
    )
  }

  function search() {
    if (!map) return
    const c = map.getCenter()
    const params = new URLSearchParams({
      lat: c.lat().toFixed(5),
      lng: c.lng().toFixed(5),
      // 명소·주소를 찾아 바짝 당겨 본 뒤라도 코스는 걸어 다닐 만한 넓이(1.5km 이상)에서 찾는다
      r: String(Math.max(1500, visibleRadius(map))),
    })
    if (keywords.length) params.set('kw', keywords.join(','))
    navigate(`/search?${params}`)
  }

  return (
    <section className="home">
      <h1>어디서, 어떤 데이트를 할까요?</h1>
      <p className="muted">지역을 찾거나 지도를 옮겨 고르고, 분위기를 고르면 데이트 코스를 순위대로 보여드려요.</p>

      <h2 className="section-title">지역</h2>
      <LocationSearch onPick={(l) => moveTo(l.lat, l.lng, l.radius)} />
      <div className="chips">
        <button type="button" className="chip" onClick={nearMe} disabled={!map || locating}>
          {locating ? '찾는 중…' : '📍 내 주변'}
        </button>
        {AREAS.map((a) => (
          <button key={a.name} type="button" className="chip" disabled={!map} onClick={() => moveTo(a.lat, a.lng, a.radius)}>
            {a.name}
          </button>
        ))}
      </div>
      {notice && <p className="notice">{notice}</p>}

      <h2 className="section-title">키워드</h2>
      <KeywordPicker value={keywords} onChange={setKeywords} />

      <div className="actions">
        <button type="button" className="primary" onClick={search} disabled={!map}>
          이 지도에서 코스 찾기
        </button>
        {error && <p className="notice">{error}</p>}
      </div>
    </section>
  )
}
