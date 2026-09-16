import { useLayoutEffect, useRef, useState } from 'react'
import { StoreIcon, Trash2Icon, XIcon } from 'lucide-react'
import { createBooth, uploadBoothImage } from '../api/boothApi'

const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

const SUBMIT_ERROR_MESSAGES = {
  FORBIDDEN_STOREHOST_ROLE: '부스 운영자 권한이 없습니다.',
  DUPLICATE_BOOTH_FOR_FESTIVAL: '이미 이 페스티벌에 개설된 부스가 있어요.',
  FESTIVAL_NOT_FOUND: '존재하지 않는 페스티벌이에요.',
  INVALID_IMAGE_SIZE: '이미지 용량은 10MB를 초과할 수 없어요.',
  INVALID_IMAGE_TYPE: '이미지 파일만 업로드할 수 있어요.',
  IMAGE_UPLOAD_FAILED: '이미지 업로드에 실패했어요. 잠시 후 다시 시도해주세요.',
}

/**
 * 페스티벌 상세에서 STOREHOST가 부스를 개설하는 모달.
 * 페스티벌당 부스는 1개만 허용되며(백엔드가 검증), 성공하면 onCreated로 새 부스 정보를 넘긴다.
 */
function BoothCreateModal({ festivalId, onClose, onCreated }) {
  const dialog = useRef(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [boothHostName, setBoothHostName] = useState('')
  const [image, setImage] = useState(null)
  const [imageError, setImageError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useLayoutEffect(() => {
    const element = dialog.current
    const previous = document.activeElement
    if (element.showModal) element.showModal()
    else element.setAttribute('open', '')
    return () => {
      element.close?.()
      previous?.focus?.()
    }
  }, [])

  function handleImageSelect(event) {
    const file = event.target.files?.[0] ?? null
    if (file && file.size > MAX_IMAGE_SIZE_BYTES) {
      setImageError('이미지 용량은 10MB를 초과할 수 없어요.')
      setImage(null)
      event.target.value = ''
      return
    }
    setImageError('')
    setImage(file)
  }

  function handleRemoveImage() {
    setImage(null)
  }

  async function handleSubmit(event) {
    event.preventDefault()

    const errors = {}
    if (!title.trim()) errors.title = '제목을 입력해주세요.'
    if (!boothHostName.trim()) errors.boothHostName = '부스 호스트명을 입력해주세요.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    setSubmitError('')

    try {
      let imageUrl = null
      if (image) {
        const uploadResponse = await uploadBoothImage(image)
        imageUrl = uploadResponse.data.data.imageUrl
      }

      const response = await createBooth({
        festivalId: Number(festivalId),
        title: title.trim(),
        description: description.trim() || null,
        boothHostName: boothHostName.trim(),
        imageUrl,
      })
      onCreated(response.data.data)
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      setSubmitError(SUBMIT_ERROR_MESSAGES[errorCode] || '부스 개설에 실패했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby="booth-create-title"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      className="fixed inset-0 m-auto max-h-[85dvh] w-[calc(100%_-_2rem)] max-w-md overflow-y-auto rounded-3xl border-0 bg-white p-0 text-gray-900 shadow-2xl backdrop:bg-slate-900/45"
    >
      <header className="sticky top-0 z-10 flex items-center justify-between gap-4 border-b border-gray-100 bg-white px-6 py-5">
        <h2 id="booth-create-title" className="flex items-center gap-2 text-lg font-extrabold tracking-tight">
          <StoreIcon size={20} aria-hidden="true" />
          부스 개설
        </h2>
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 focus-visible:outline-2 focus-visible:outline-blue-600"
        >
          <XIcon size={20} />
        </button>
      </header>

      <form className="space-y-4 p-6" onSubmit={handleSubmit} noValidate>
        {submitError && (
          <p className="rounded-xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-600" role="alert">
            {submitError}
          </p>
        )}

        <div>
          <label htmlFor="booth-title" className="mb-1.5 block text-sm font-bold text-gray-900">
            제목
          </label>
          <input
            id="booth-title"
            type="text"
            className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm outline-none focus:border-blue-500"
            placeholder="예: 수제 맥주 부스"
            value={title}
            onChange={(event) => {
              setTitle(event.target.value)
              setFieldErrors((prev) => ({ ...prev, title: undefined }))
            }}
            aria-invalid={Boolean(fieldErrors.title)}
          />
          {fieldErrors.title && <p className="mt-1 text-xs text-red-600">{fieldErrors.title}</p>}
        </div>

        <div>
          <label htmlFor="booth-host-name" className="mb-1.5 block text-sm font-bold text-gray-900">
            부스 호스트명
          </label>
          <input
            id="booth-host-name"
            type="text"
            className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm outline-none focus:border-blue-500"
            placeholder="예: 홉스터"
            value={boothHostName}
            onChange={(event) => {
              setBoothHostName(event.target.value)
              setFieldErrors((prev) => ({ ...prev, boothHostName: undefined }))
            }}
            aria-invalid={Boolean(fieldErrors.boothHostName)}
          />
          {fieldErrors.boothHostName && <p className="mt-1 text-xs text-red-600">{fieldErrors.boothHostName}</p>}
        </div>

        <div>
          <label htmlFor="booth-description" className="mb-1.5 block text-sm font-bold text-gray-900">
            소개 <span className="font-normal text-gray-400">(선택)</span>
          </label>
          <textarea
            id="booth-description"
            rows={3}
            className="w-full resize-none rounded-xl border border-gray-200 px-4 py-2.5 text-sm outline-none focus:border-blue-500"
            placeholder="부스를 소개해주세요."
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>

        <div>
          <label htmlFor="booth-image" className="mb-1.5 block text-sm font-bold text-gray-900">
            대표 이미지 <span className="font-normal text-gray-400">(선택, 1장·10MB 이하)</span>
          </label>
          <div className="flex items-center gap-2">
            <label
              htmlFor="booth-image"
              className="cursor-pointer rounded-xl border border-gray-200 px-4 py-2 text-sm font-bold text-gray-700 hover:bg-gray-50"
            >
              이미지 선택
            </label>
            <span className="text-sm text-gray-500">{image ? image.name : '선택된 파일 없음'}</span>
          </div>
          <input id="booth-image" type="file" accept="image/*" className="hidden" onChange={handleImageSelect} />
          {imageError && <p className="mt-1 text-xs text-red-600">{imageError}</p>}
          {image && (
            <button
              type="button"
              onClick={handleRemoveImage}
              className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-gray-500 hover:text-red-600"
            >
              <Trash2Icon size={12} aria-hidden="true" />
              제거
            </button>
          )}
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-xl bg-blue-600 py-3 text-sm font-bold text-white transition hover:bg-blue-700 disabled:opacity-60"
        >
          {submitting ? '개설 중…' : '부스 개설하기'}
        </button>
      </form>
    </dialog>
  )
}

export default BoothCreateModal
