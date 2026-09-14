import apiClient from './client'
const base = (host) => `/api/${host ? 'host' : 'admin'}/settlements`
export const listSettlements = (host, params, signal) => apiClient.get(base(host), { params, signal })
export const settlementSummary = (host, params, signal) => apiClient.get(`${base(host)}/summary`, { params, signal })
export const settlementDetail = (host, id) => apiClient.get(`${base(host)}/${id}`)
export const settlementCommand = (id, action, body, key) => apiClient.post(`${base(false)}/${id}/${action}`, body, { headers: { 'Idempotency-Key': key } })
