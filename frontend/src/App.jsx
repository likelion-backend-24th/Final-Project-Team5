import { Route, Routes } from 'react-router-dom'
import SiteFooter from './components/SiteFooter'
import SiteHeader from './components/SiteHeader'
import Festivals from './pages/Festivals'
import Home from './pages/Home'
import Login from './pages/Login'
import Placeholder from './pages/Placeholder'
import SignUp from './pages/SignUp'

/** 상단바·푸터는 모든 화면에 고정, 가운데만 라우팅으로 갈아끼운다. */
function App() {
  return (
    <>
      <SiteHeader />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/festivals" element={<Festivals />} />
        <Route path="/festivals/:id" element={<Placeholder title="페스티벌 상세" />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/reset-password" element={<Placeholder title="비밀번호 재설정" />} />
        <Route path="/reservations" element={<Placeholder title="내 예약" />} />
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
