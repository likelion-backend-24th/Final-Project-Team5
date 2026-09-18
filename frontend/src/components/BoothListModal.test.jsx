import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import BoothListModal from './BoothListModal'
import { useAuth } from '../context/AuthContext.jsx'
import { fetchBoothsForFestival, fetchMyBoothWaitlist, requestBoothWaitlist } from '../api/boothApi'

vi.mock('../context/AuthContext.jsx')
vi.mock('../api/boothApi')

//jsdom은 <dialog>의 showModal/close를 구현하지 않는다. open 속성을 직접 세팅해줘야
//testing-library가 대화상자 내용을 "숨김 상태"로 취급해 role 쿼리에서 놓치는 일이 없다.
beforeEach(() => {
  vi.clearAllMocks()
  HTMLDialogElement.prototype.showModal = vi.fn(function open() {
    this.setAttribute('open', '')
  })
  HTMLDialogElement.prototype.close = vi.fn(function close() {
    this.removeAttribute('open')
  })
  useAuth.mockReturnValue({ user: { id: 1, role: 'USER' } })
})

function renderModal(festivalId = 10) {
  return render(
    <MemoryRouter>
      <BoothListModal festivalId={festivalId} onClose={() => {}} />
    </MemoryRouter>,
  )
}

describe('BoothListModal', () => {
  it('shows my existing queue number right away, without needing to click 대기 신청 first', async () => {
    fetchBoothsForFestival.mockResolvedValue({
      data: { data: [{ id: 1, title: '떡볶이 부스', boothHostName: '분식왕', boothStatus: 'OPEN' }] },
    })
    fetchMyBoothWaitlist.mockResolvedValue({ data: { data: { queueNumber: 5 } } })

    renderModal()

    await screen.findByText('내 대기번호 5번')
    expect(fetchMyBoothWaitlist).toHaveBeenCalledWith(1)
    expect(screen.queryByRole('button', { name: '대기 신청' })).toBeNull()
    expect(requestBoothWaitlist).not.toHaveBeenCalled()
  })

  it('shows the 대기 신청 button when the booth has no existing waitlist entry', async () => {
    fetchBoothsForFestival.mockResolvedValue({
      data: { data: [{ id: 2, title: '수제 맥주 부스', boothHostName: '브루어', boothStatus: 'OPEN' }] },
    })
    fetchMyBoothWaitlist.mockRejectedValue({ response: { status: 404, data: { errorCode: 'WAITLIST_NOT_FOUND' } } })

    renderModal()

    await screen.findByRole('button', { name: '대기 신청' })
    expect(screen.queryByText(/내 대기번호/)).toBeNull()
  })

  it('does not query waitlist status for closed booths or when logged out', async () => {
    useAuth.mockReturnValue({ user: null })
    fetchBoothsForFestival.mockResolvedValue({
      data: { data: [{ id: 3, title: '마감된 부스', boothHostName: '호스트', boothStatus: 'CLOSED' }] },
    })

    renderModal()

    await screen.findByText('마감입니다')
    expect(fetchMyBoothWaitlist).not.toHaveBeenCalled()
  })

  it('still lets a fresh request go through and shows the returned queue number', async () => {
    fetchBoothsForFestival.mockResolvedValue({
      data: { data: [{ id: 4, title: '타코 부스', boothHostName: '타코왕', boothStatus: 'OPEN' }] },
    })
    fetchMyBoothWaitlist.mockRejectedValue({ response: { status: 404, data: { errorCode: 'WAITLIST_NOT_FOUND' } } })
    requestBoothWaitlist.mockResolvedValue({ data: { data: { queueNumber: 7 } } })
    const user = userEvent.setup()

    renderModal()

    await user.click(await screen.findByRole('button', { name: '대기 신청' }))
    await waitFor(() => expect(screen.getByText('내 대기번호 7번')).toBeTruthy())
  })
})
