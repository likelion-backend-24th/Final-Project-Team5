import { Navigate, Route, Routes } from 'react-router-dom'
import ScrollToTop from './components/ScrollToTop'
import SiteFooter from './components/SiteFooter'
import SiteHeader from './components/SiteHeader'
import { useAuth } from './context/AuthContext.jsx'
import AdminFestivals from './pages/AdminFestivals'
import AdminHostApplications from './pages/AdminHostApplications'
import CheckIn from './pages/CheckIn'
import FestivalDetail from './pages/FestivalDetail'
import Festivals from './pages/Festivals'
import Home from './pages/Home'
import HostApplication from './pages/HostApplication'
import HostFestivalDetail from './pages/HostFestivalDetail'
import HostFestivalNew from './pages/HostFestivalNew'
import HostFestivals from './pages/HostFestivals'
import Login from './pages/Login'
import MyPage from './pages/Mypage'
import Placeholder from './pages/Placeholder'
import ReservationCheckout from './pages/ReservationCheckout'
import SignUp from './pages/SignUp'

/** 상단바·푸터는 모든 화면에 고정, 가운데만 라우팅으로 갈아끼운다. */
function App() {
  const { user, isLoading } = useAuth()

  //도우미(HELPER)는 주최자가 현장 검증만 맡기려고 발급한 임시 계정이라 Gateway가 나머지 API를 전부 막는다.
  //일반 화면으로 들어가면 대부분의 요청이 403이 되므로, 아예 현장 검증 화면만 열어준다.
  if (!isLoading && user?.role === 'HELPER') {
    return (
      <>
        <ScrollToTop />
        <SiteHeader />
        <Routes>
          <Route path="/check-in" element={<CheckIn />} />
          <Route path="*" element={<Navigate to="/check-in" replace />} />
        </Routes>
        <SiteFooter />
      </>
    )
  }

  return (
    <>
      <ScrollToTop />
      <SiteHeader />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/festivals" element={<Festivals />} />
        <Route path="/festivals/:id" element={<FestivalDetail />} />
        <Route path="/festivals/:id/reserve" element={<ReservationCheckout />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/reset-password" element={<Placeholder title="비밀번호 재설정" />} />
        <Route path="/mypage" element={<MyPage />} />
        <Route path="/reservations" element={<MyPage initialTab="reservations" />} />
        <Route path="/host-application" element={<HostApplication />} />
        {/* SiteFooter/OrganizerCta는 여전히 /organizers/apply로 링크하므로 같은 화면을 연결해둔다. */}
        <Route path="/organizers/apply" element={<HostApplication />} />
        <Route path="/host/festivals/new" element={<HostFestivalNew />} />
        {/* SiteFooter/OrganizerCta는 여전히 /festivals/new로 링크하므로 같은 화면을 연결해둔다. */}
        <Route path="/festivals/new" element={<HostFestivalNew />} />
        <Route path="/host/festivals" element={<HostFestivals />} />
        <Route path="/host/festivals/:id" element={<HostFestivalDetail />} />
        {/* 현장 입장 검증 — 주최자는 페스티벌을 지정해서, 도우미는 배정된 페스티벌로 /check-in에서 들어온다. */}
        <Route path="/host/festivals/:id/check-in" element={<CheckIn />} />
        <Route path="/check-in" element={<CheckIn />} />
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
