import { useEffect } from 'react'
import { XIcon } from 'lucide-react'

/** 어드민 상세보기 공용 모달. 오버레이/카드/제목/닫기 버튼 클래스는 SuspendUserModal.jsx, RefundModal.jsx와 동일하다. */
function DetailModal({ open, onClose, title, children }) {
  useEffect(() => {
    if (!open) return
    function handleKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-6"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div className="w-full max-w-sm rounded-3xl bg-white p-6 shadow-lg" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-extrabold text-gray-900">{title}</h3>
          <button type="button" onClick={onClose} aria-label="닫기" className="text-gray-400 hover:text-gray-600">
            <XIcon className="h-5 w-5" />
          </button>
        </div>
        <div className="mt-4 max-h-[70vh] overflow-y-auto">{children}</div>
      </div>
    </div>
  )
}

export default DetailModal
