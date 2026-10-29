import { Link, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage'
import SearchPage from './pages/SearchPage'
import CoursePage from './pages/CoursePage'
import SavedPage from './pages/SavedPage'
import NotFoundPage from './pages/NotFoundPage'
import PrivacyPage from './pages/PrivacyPage'
import MapLayout from './map/MapLayout'

// 화면 주소는 기획안의 "화면 목록"을 따른다. 지도는 MapLayout 이 하나만 만들어 모든 화면이 함께 쓴다
export default function App() {
  return (
    <MapLayout>
      <header className="site-header">
        <Link to="/" className="logo">데이트코스</Link>
        <nav>
          <Link to="/saved">내 저장</Link>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/courses/:id" element={<CoursePage />} />
          <Route path="/saved" element={<SavedPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        <footer className="site-footer">
          <Link to="/privacy">개인정보처리방침</Link>
          <span aria-hidden="true"> · </span>
          <a href="https://github.com/kimseongmin0301/date-course" target="_blank" rel="noreferrer">
            GitHub
          </a>
        </footer>
      </main>
    </MapLayout>
  )
}
