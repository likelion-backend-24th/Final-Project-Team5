import { expect, it, vi } from 'vitest'
import apiClient from './client'
import { createHelperAccount, resendHelperInvitation, revokeHelperAccount, convertLegacyHelper } from './hostFestivalApi'
vi.mock('./client', () => ({ default: { post: vi.fn(), delete: vi.fn() } }))
it('uses invitation API paths and email payloads', () => {
  createHelperAccount(7, 'person@example.com')
  expect(apiClient.post).toHaveBeenCalledWith('/api/host/festivals/7/helpers', { email: 'person@example.com' })
  resendHelperInvitation(7, 20)
  expect(apiClient.post).toHaveBeenCalledWith('/api/host/festivals/7/helpers/20/resend')
  revokeHelperAccount(7, 20)
  expect(apiClient.delete).toHaveBeenCalledWith('/api/host/festivals/7/helpers/20')
  convertLegacyHelper(7, 20, 'legacy@example.com')
  expect(apiClient.post).toHaveBeenCalledWith('/api/host/festivals/7/helpers/20/invitation', { email: 'legacy@example.com' })
})
