import { Route, Routes } from 'react-router-dom'
import ScrollToTop from './components/ScrollToTop'
import SiteFooter from './components/SiteFooter'
import SiteHeader from './components/SiteHeader'
import AdminFestivals from './pages/AdminFestivals'
import AdminHostApplications from './pages/AdminHostApplications'
import FestivalDetail from './pages/FestivalDetail'
import Festivals from './pages/Festivals'
import Home from './pages/Home'
import HostApplication from './pages/HostApplication'
import HostFestivalNew from './pages/HostFestivalNew'
import Login from './pages/Login'
import Placeholder from './pages/Placeholder'
import SignUp from './pages/SignUp'

/** 상단바·푸터는 모든 화면에 고정, 가운데만 라우팅으로 갈아끼운다. */
function App() {
  return (
    <>
      <ScrollToTop />
      <SiteHeader />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/festivals" element={<Festivals />} />
        <Route path="/festivals/:id" element={<FestivalDetail />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/reset-password" element={<Placeholder title="비밀번호 재설정" />} />
        <Route path="/reservations" element={<Placeholder title="내 예약" />} />
        <Route path="/host-application" element={<HostApplication />} />
        {/* SiteFooter/OrganizerCta는 여전히 /organizers/apply로 링크하므로 같은 화면을 연결해둔다. */}
        <Route path="/organizers/apply" element={<HostApplication />} />
        <Route path="/host/festivals/new" element={<HostFestivalNew />} />
        {/* SiteFooter/OrganizerCta는 여전히 /festivals/new로 링크하므로 같은 화면을 연결해둔다. */}
        <Route path="/festivals/new" element={<HostFestivalNew />} />
        <Route path="/admin/host-applications" element={<AdminHostApplications />} />
        <Route path="/admin/festivals" element={<AdminFestivals />} />
        <Route path="/terms" element={<Placeholder title="이용약관" />} />
        <Route path="/privacy" element={<Placeholder title="개인정보처리방침" />} />
        <Route
          path="*"
          element={
            <Placeholder
              title="페이지를 찾을 수 없습니다"
              description="주소가 잘못되었거나 삭제된 페이지입니다."
            />
          }
        />
      </Routes>
      <SiteFooter />
    </>
  )
}

export default App
