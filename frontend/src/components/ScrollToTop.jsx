import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

/** 라우트(pathname)가 바뀔 때마다 스크롤을 맨 위로 되돌린다. */
function ScrollToTop() {
  const { pathname } = useLocation()

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])

  return null
}

export default ScrollToTop
