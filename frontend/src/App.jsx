import { Navigate, Route, Routes } from 'react-router-dom'
import ScrollToTop from './components/ScrollToTop'
import SiteFooter from './components/SiteFooter'
import SiteHeader from './components/SiteHeader'
import { useAuth } from './context/AuthContext.jsx'
import AdminFestivals from './pages/AdminFestivals'
import AdminHostApplications from './pages/AdminHostApplications'
import CheckIn from './pages/CheckIn'
import FestivalDetail from './pages/FestivalDetail'
import HelperHome from './pages/HelperHome'
import Festivals from './pages/Festivals'
import Home from './pages/Home'
import HostApplication from './pages/HostApplication'
import HostFestivalDetail from './pages/HostFestivalDetail'
import HostFestivalNew from './pages/HostFestivalNew'
import HostFestivals from './pages/HostFestivals'
import Login from './pages/Login'
import MyPage from './pages/Mypage'
import Placeholder from './pages/Placeholder'
import RequireAuth from './components/RequireAuth'
import ReservationCheckout from './pages/ReservationCheckout'
import ResetPassword from './pages/ResetPassword'
import SignUp from './pages/SignUp'

/** 상단바·푸터는 모든 화면에 고정, 가운데만 라우팅으로 갈아끼운다. */
function App() {
  const { user, isLoading } = useAuth()

  //도우미(HELPER)는 배정된 행사 하나에서 현장 입장 검증만 담당하는 계정이라, 일반 회원 화면 대신
  //전용 메인(담당 행사 정보 / 현장 입장 검사)을 보여준다. 페스티벌 목록·예매·주최자·운영자 화면은
  //들어가도 할 수 있는 일이 없어(Gateway가 API를 막는다) 메인으로 되돌린다.
  if (!isLoading && user?.role === 'HELPER') {
    return (
      <>
        <ScrollToTop />
        <SiteHeader />
        <Routes>
          <Route path="/" element={<HelperHome />} />
          <Route path="/check-in" element={<CheckIn />} />
          {/* 메인의 '행사 정보' 버튼이 담당 행사 상세로 보낸다(공개 조회 API라 도우미도 볼 수 있다). */}
          <Route path="/festivals/:id" element={<FestivalDetail />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/terms" element={<Placeholder title="이용약관" />} />
          <Route path="/privacy" element={<Placeholder title="개인정보처리방침" />} />
          <Route path="*" element={<Navigate to="/" replace />} />
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
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route
          path="/mypage"
          element={
            <RequireAuth>
              <MyPage />
            </RequireAuth>
          }
        />
        <Route
          path="/reservations"
          element={
            <RequireAuth>
              <MyPage initialTab="reservations" />
            </RequireAuth>
          }
        />
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
