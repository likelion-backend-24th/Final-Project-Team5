import { expect, it } from 'vitest'
import { ADMIN_STATUS_OPTIONS, isValidAdminStatus, parseAdminPath } from './adminNav'
import { ACCOUNT_FILTERS } from './OrganizerManagement'
import { OPERATION_FILTERS } from './FestivalOperations'
import { CANCELLATION_FILTERS } from './CancellationRequests'

it('ADMIN_STATUS_OPTIONS는 각 서브탭의 실제 필터 버튼 키와 정확히 같다(한쪽만 바뀌면 실패)', () => {
  expect(ADMIN_STATUS_OPTIONS['hosts/list']).toEqual(ACCOUNT_FILTERS.map((f) => f.key))
  expect(ADMIN_STATUS_OPTIONS['festivals/operations']).toEqual(OPERATION_FILTERS.map((f) => f.key))
  expect(ADMIN_STATUS_OPTIONS['festivals/cancellations']).toEqual(CANCELLATION_FILTERS.map((f) => f.key))
})

it('/admin은 서브탭 없이 대시보드로 파싱된다', () => {
  expect(parseAdminPath('/admin')).toEqual({ status: 'ok', tab: 'dashboard', sub: null })
  expect(parseAdminPath('/admin/')).toEqual({ status: 'ok', tab: 'dashboard', sub: null })
})

it('서브탭이 없는 탭(members/settlements)은 sub:null로 파싱된다', () => {
  expect(parseAdminPath('/admin/members')).toEqual({ status: 'ok', tab: 'members', sub: null })
  expect(parseAdminPath('/admin/settlements')).toEqual({ status: 'ok', tab: 'settlements', sub: null })
})

it('서브탭이 있는 탭은 유효한 서브탭 세그먼트를 그대로 반환한다', () => {
  expect(parseAdminPath('/admin/hosts/list')).toEqual({ status: 'ok', tab: 'hosts', sub: 'list' })
  expect(parseAdminPath('/admin/festivals/operations')).toEqual({ status: 'ok', tab: 'festivals', sub: 'operations' })
})

it('서브탭이 있는 탭인데 서브탭 세그먼트가 없으면 기본 서브탭으로 리다이렉트하라고 알려준다', () => {
  expect(parseAdminPath('/admin/hosts')).toEqual({ status: 'needs-default-sub', redirectTo: '/admin/hosts/applications' })
  expect(parseAdminPath('/admin/festivals')).toEqual({ status: 'needs-default-sub', redirectTo: '/admin/festivals/submissions' })
})

it('존재하지 않는 최상위 탭·서브탭·불필요한 추가 세그먼트는 모두 invalid다', () => {
  expect(parseAdminPath('/admin/abc')).toEqual({ status: 'invalid' })
  expect(parseAdminPath('/admin/festivals/abc')).toEqual({ status: 'invalid' })
  expect(parseAdminPath('/admin/members/abc')).toEqual({ status: 'invalid' })
  expect(parseAdminPath('/admin/festivals/operations/abc')).toEqual({ status: 'invalid' })
})

it('isValidAdminStatus는 해당 탭/서브탭에 정의된 값만 유효하다고 본다', () => {
  expect(isValidAdminStatus('hosts', 'list', 'ACTIVE')).toBe(true)
  expect(isValidAdminStatus('hosts', 'list', 'NOT_A_STATUS')).toBe(false)
  expect(isValidAdminStatus('hosts', 'applications', 'ACTIVE')).toBe(false)
  expect(isValidAdminStatus('festivals', 'operations', 'ONGOING')).toBe(true)
  expect(isValidAdminStatus('festivals', 'cancellations', 'REFUNDING')).toBe(true)
  expect(isValidAdminStatus('festivals', 'cancellations', '')).toBe(false)
  expect(isValidAdminStatus('festivals', 'cancellations', null)).toBe(false)
})
