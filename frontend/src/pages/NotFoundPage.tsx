import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <section>
      <h1>없는 페이지예요</h1>
      <Link to="/">처음으로</Link>
    </section>
  )
}
