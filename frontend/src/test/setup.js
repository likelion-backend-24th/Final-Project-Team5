import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import { clearQueryCache } from '../api/queryCache'
//모듈 전역 캐시가 테스트 사이로 새면 앞 테스트의 응답이 뒤 테스트에 보이므로 매번 비운다.
afterEach(() => { cleanup(); localStorage.clear(); clearQueryCache() })
