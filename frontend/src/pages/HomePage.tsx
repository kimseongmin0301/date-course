import { useEffect, useState } from 'react'
import { api } from '../lib/api'

// 홈: 지역 검색창, 내 주변, 누구와·키워드 칩, 인기 코스 (아직 뼈대)
export default function HomePage() {
  const [server, setServer] = useState('확인 중…')

  useEffect(() => {
    api<{ status: string }>('/health')
      .then((r) => setServer(r.status === 'ok' ? '연결됨' : r.status))
      .catch(() => setServer('연결 안 됨'))
  }, [])

  return (
    <section>
      <h1>어디서, 어떤 데이트를 할까요?</h1>
      <p>지역과 분위기를 고르면 데이트 코스를 순위대로 보여드려요.</p>
      <p className="muted">서버: {server}</p>
    </section>
  )
}
