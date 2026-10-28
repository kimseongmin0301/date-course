import { useParams } from 'react-router-dom'

// 코스 상세: 동선 지도, 단계별 장소, 근처 대안 (아직 뼈대)
export default function CoursePage() {
  const { id } = useParams()

  return (
    <section>
      <h1>코스 {id}</h1>
    </section>
  )
}
