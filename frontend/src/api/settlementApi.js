import apiClient from './client'

function base(host) {
  return `/api/${host ? 'host' : 'admin'}/settlements`
}

// 조회 역할과 검색 조건에 맞는 정산 목록을 가져온다.
export function listSettlements(host, params, signal) {
  return apiClient.get(base(host), { params, signal })
}

// 목록과 동일한 조건으로 정산 요약을 가져온다.
export function settlementSummary(host, params, signal) {
  return apiClient.get(`${base(host)}/summary`, { params, signal })
}

// 역할별로 공개되는 정산 상세를 가져온다.
export function settlementDetail(host, id) {
  return apiClient.get(`${base(host)}/${id}`)
}

// 동일한 요청 키를 재사용해 관리자 명령의 중복 처리를 막는다.
export function settlementCommand(id, action, body, key) {
  return apiClient.post(`${base(false)}/${id}/${action}`, body, {
    headers: { 'Idempotency-Key': key },
  })
}
