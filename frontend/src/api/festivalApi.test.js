import { describe, expect, it } from 'vitest'
import { festivalVisibleStatusLabel } from './festivalApi'

describe('festivalVisibleStatusLabel', () => {
  const now = new Date('2026-09-21T12:00:00')

  it('labels a future published festival as upcoming', () => {
    expect(festivalVisibleStatusLabel({ festivalStatus: 'PUBLISHED', startAt: '2026-10-01T10:00:00' }, now))
      .toBe('진행 예정')
  })

  it('keeps the active and closed labels', () => {
    expect(festivalVisibleStatusLabel({ festivalStatus: 'PUBLISHED', startAt: '2026-09-21T12:00:00' }, now))
      .toBe('진행중')
    expect(festivalVisibleStatusLabel({ festivalStatus: 'CLOSED', startAt: '2026-09-01T10:00:00' }, now))
      .toBe('종료됨')
  })
})
