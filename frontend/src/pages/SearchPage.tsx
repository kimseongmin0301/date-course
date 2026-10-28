import { useSearchParams } from 'react-router-dom'

// 검색 결과: 코스 순위 카드 + 지도 (아직 뼈대). 주소 예: /search?area=강남&kw=조용
export default function SearchPage() {
  const [params] = useSearchParams()

  return (
    <section>
      <h1>코스 순위</h1>
      <p className="muted">
        지역: {params.get('area') ?? '-'} · 키워드: {params.getAll('kw').join(', ') || '-'}
      </p>
    </section>
  )
}
