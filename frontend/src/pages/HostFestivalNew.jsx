import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowLeftIcon,
  ArrowRightIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  CircleAlertIcon,
  CircleCheckIcon,
  CircleDotIcon,
  InfoIcon,
  LockIcon,
  PanelTopIcon,
  PlusIcon,
  SparklesIcon,
  Trash2Icon,
} from 'lucide-react'
import { FESTIVAL_CATEGORIES as CATEGORY_OPTIONS, FESTIVAL_REGIONS as REGION_OPTIONS } from '../api/festivalApi'
import { createFestival, uploadFestivalImages } from '../api/hostFestivalApi'
import { useAuth } from '../context/AuthContext.jsx'
import KakaoMap from '../components/KakaoMap'
import AiDraftModal from '../components/AiDraftModal'
import styles from './HostFestivalNew.module.css'

//AI 초안이 값을 안 주는 항목(가격·수량·판매기간)에 쓸 기본값 — Gemini가 근거 없이 숫자를 지어내지 않게
//하고, 대신 여기서 예측 가능한 값으로 채운다. 호스트가 생성 후 그대로 두든 고치든 자유롭게 조정한다.
const AI_DRAFT_DEFAULT_PRICE = '10000'
const AI_DRAFT_DEFAULT_QUANTITY = '50'

function nowDateTime() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(Math.floor(now.getMinutes() / 10) * 10).padStart(2, '0')
  return `${y}-${m}-${d}T${hh}:${mm}`
}

const MAX_DETAIL_IMAGE_COUNT = 2
const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

const IMAGE_ERROR_MESSAGES = {
  FORBIDDEN_ROLE: '주최자 권한이 없습니다.',
  INVALID_IMAGE_COUNT: '대표 이미지는 1개까지 업로드할 수 있어요.',
  INVALID_DETAIL_IMAGE_COUNT: '본문 이미지는 최대 2개까지 업로드할 수 있어요.',
  INVALID_IMAGE_SIZE: '이미지 용량은 파일당 10MB를 초과할 수 없어요.',
  INVALID_IMAGE_TYPE: '이미지 파일만 업로드할 수 있어요.',
  IMAGE_UPLOAD_FAILED: '이미지 업로드에 실패했어요. 잠시 후 다시 시도해주세요.',
}

const CREATE_FESTIVAL_ERROR_MESSAGES = {
  INVALID_PERIOD: '종료 일시는 시작 일시 이후여야 해요.',
  INVALID_OPERATING_HOURS: '운영 종료 시간은 시작 시간 이후여야 해요.',
  INVALID_TICKET_SALE_PERIOD: '티켓 판매 종료 일시는 판매 시작 일시 이후여야 해요.',
  INVALID_TICKET_DATE: '티켓 날짜는 페스티벌 개최 기간 내여야 해요.',
  INVALID_SEAT_LAYOUT: '좌석 배치 정보를 확인해주세요.',
}

//무대 배치 방식 — 페스티벌 전체에 하나. 구역(SEATED 티켓타입)을 격자로 배치할지(전면형),
//중앙 무대를 둘러싸는 방향으로 배치할지(원형) 결정한다.
const STAGE_LAYOUT_OPTIONS = [
  {
    value: 'FRONT_STAGE',
    label: '전면형',
    description: '무대가 앞에 고정되고, 구역이 격자로 나열돼요.',
    Icon: PanelTopIcon,
  },
  {
    value: 'CENTER_STAGE',
    label: '원형',
    description: '무대가 중앙에 있고, 구역이 둘러싸는 형태로 배치돼요.',
    Icon: CircleDotIcon,
  },
]

//CENTER_STAGE에서 구역 위치를 8방향 중 하나로 고르면 이 각도(정북 기준 시계방향)로 변환한다.
const DIRECTION_TO_ANGLE = { N: 0, NE: 45, E: 90, SE: 135, S: 180, SW: 225, W: 270, NW: 315 }

//방향 선택 패드를 3×3 그리드로 그리기 위한 배치 — 가운데(null)는 무대 자리라 선택할 수 없다.
const DIRECTION_GRID_CELLS = [
  { direction: 'NW', label: '왼위' },
  { direction: 'N', label: '위' },
  { direction: 'NE', label: '오른위' },
  { direction: 'W', label: '왼쪽' },
  { direction: null, label: null },
  { direction: 'E', label: '오른쪽' },
  { direction: 'SW', label: '왼아래' },
  { direction: 'S', label: '아래' },
  { direction: 'SE', label: '오른아래' },
]

//결번 좌석 번호 입력("5, 6")을 정수 배열로 파싱한다. 숫자가 아니거나 1 미만인 값이 섞여 있으면
//invalid를 표시하고, 중복 번호는 자동으로 제거한다.
function parseExcludedSeats(text) {
  const trimmed = text.trim()
  if (!trimmed) return { invalid: false, values: [] }
  const numbers = trimmed
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean)
    .map(Number)
  if (numbers.some((n) => !Number.isInteger(n) || n < 1)) {
    return { invalid: true, values: [] }
  }
  return { invalid: false, values: Array.from(new Set(numbers)) }
}

//행별 seatCount 합계에서 결번 개수를 뺀, 실제로 생성될 좌석 수(파싱 실패한 행은 0으로 취급).
function totalSeatCount(seatRows) {
  return seatRows.reduce((sum, row) => {
    const seatCount = Number(row.seatCount)
    if (!Number.isInteger(seatCount) || seatCount < 1) return sum
    const { invalid, values } = parseExcludedSeats(row.excludedSeats)
    if (invalid) return sum
    return sum + Math.max(0, seatCount - values.length)
  }, 0)
}

function validateThumbnail(file) {
  if (file && file.size > MAX_IMAGE_SIZE_BYTES) {
    return '이미지 용량은 10MB를 초과할 수 없어요.'
  }
  return ''
}

function validateDetailImages(files) {
  if (files.length > MAX_DETAIL_IMAGE_COUNT) {
    return '본문 이미지는 최대 2장까지 선택할 수 있어요.'
  }
  if (files.some((file) => file.size > MAX_IMAGE_SIZE_BYTES)) {
    return '이미지 용량은 파일당 10MB를 초과할 수 없어요.'
  }
  return ''
}

const HOURS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))
const MINUTES = ['00', '10', '20', '30', '40', '50']

//등록 폼을 처음 열 때 일정을 비워두면 매번 날짜부터 골라야 해서 번거롭다는 피드백 — 내일 날짜에
//운영시간(9~18시)과 맞춘 기본값을 미리 채워두고, 호스트는 필요할 때만 바꾸면 되게 한다.
function defaultDateTime(hour) {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  const y = tomorrow.getFullYear()
  const m = String(tomorrow.getMonth() + 1).padStart(2, '0')
  const d = String(tomorrow.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}T${hour}:00`
}

//브라우저 기본 datetime-local의 시간 스피너가 끝없이 돌고 위/아래 스크롤 속도가 달라 쓰기 불편하다는 QA 피드백.
//날짜는 기본 달력, 시·분은 10분 단위 select로 받아 form에는 기존과 같은 'YYYY-MM-DDTHH:mm' 문자열로 넣는다.
function DateTimeFields({ id, value, onChange, invalid }) {
  const [date = '', time = ''] = value ? value.split('T') : ['', '']
  const [hour = '', minute = ''] = time ? time.split(':') : ['', '']

  function emit(nextDate, nextHour, nextMinute) {
    if (!nextDate) {
      onChange('')
      return
    }
    onChange(`${nextDate}T${nextHour || '00'}:${nextMinute || '00'}`)
  }

  return (
    <div className={styles.dateTimeRow}>
      <input
        id={`${id}-date`}
        type="date"
        className={styles.input}
        value={date}
        onChange={(event) => emit(event.target.value, hour, minute)}
        aria-invalid={invalid}
      />
      <select
        aria-label="시"
        className={styles.input}
        value={hour}
        onChange={(event) => emit(date, event.target.value, minute)}
        disabled={!date}
      >
        <option value="">시</option>
        {HOURS.map((h) => (
          <option key={h} value={h}>
            {h}시
          </option>
        ))}
      </select>
      <select
        aria-label="분"
        className={styles.input}
        value={minute}
        onChange={(event) => emit(date, hour, event.target.value)}
        disabled={!date}
      >
        <option value="">분</option>
        {MINUTES.map((m) => (
          <option key={m} value={m}>
            {m}분
          </option>
        ))}
      </select>
    </div>
  )
}

function createEmptyTicketType(key) {
  return {
    key,
    ticketMode: 'STANDING',
    name: '',
    description: '',
    price: '',
    quantity: '',
    zone: '',
    seatRows: [],
    //CENTER_STAGE에서만 쓰이는 8방향 선택 — 기본값은 "위"(정북)
    positionDirection: 'N',
    saleStartAt: '',
    saleEndAt: '',
    ticketDate: '',
  }
}

function validateTicketType(ticket) {
  const errors = {}

  if (!ticket.name.trim()) {
    errors.name = '이름을 입력해주세요.'
  }

  if (ticket.price.trim() === '') {
    errors.price = '가격을 입력해주세요.'
  } else if (Number.isNaN(Number(ticket.price)) || Number(ticket.price) < 0) {
    errors.price = '0 이상의 숫자를 입력해주세요.'
  }

  if (ticket.ticketMode === 'SEATED') {
    if (!ticket.zone.trim()) {
      errors.zone = '구역명을 입력해주세요.'
    }

    if (ticket.seatRows.length === 0) {
      errors.seatRows = '행을 최소 1개 이상 추가해주세요.'
    } else {
      const seatRowErrors = ticket.seatRows.map((row) => {
        const rowError = {}
        const seatCount = Number(row.seatCount)
        const validSeatCount = row.seatCount !== '' && Number.isInteger(seatCount) && seatCount >= 1
        if (!validSeatCount) {
          rowError.seatCount = '1 이상의 정수를 입력해주세요.'
        }

        const { invalid, values } = parseExcludedSeats(row.excludedSeats)
        if (invalid) {
          rowError.excludedSeats = '숫자를 콤마(,)로 구분해 입력해주세요.'
        } else if (validSeatCount && values.some((n) => n > seatCount)) {
          rowError.excludedSeats = `1~${seatCount} 사이의 번호만 입력할 수 있어요.`
        }

        return rowError
      })
      if (seatRowErrors.some((rowError) => Object.keys(rowError).length > 0)) {
        errors.seatRowsDetail = seatRowErrors
      }
    }
  } else if (ticket.quantity.trim() === '') {
    errors.quantity = '수량을 입력해주세요.'
  } else if (!Number.isInteger(Number(ticket.quantity)) || Number(ticket.quantity) < 1) {
    errors.quantity = '1 이상의 정수를 입력해주세요.'
  }

  if (!ticket.saleStartAt) {
    errors.saleStartAt = '판매 시작 일시를 입력해주세요.'
  }
  if (!ticket.saleEndAt) {
    errors.saleEndAt = '판매 종료 일시를 입력해주세요.'
  } else if (ticket.saleStartAt && ticket.saleEndAt <= ticket.saleStartAt) {
    errors.saleEndAt = '판매 종료 일시는 판매 시작 일시 이후여야 해요.'
  }

  if (ticket.description && ticket.description.length > 50) {
    errors.description = '설명은 50자 이내로 입력해주세요.'
  }

  return errors
}

function validate(form, thumbnail, detailImages) {
  const fieldErrors = {}

  const thumbnailMessage = validateThumbnail(thumbnail)
  if (thumbnailMessage) {
    fieldErrors.thumbnail = thumbnailMessage
  }
  const detailImagesMessage = validateDetailImages(detailImages)
  if (detailImagesMessage) {
    fieldErrors.detailImages = detailImagesMessage
  }

  if (!form.name.trim()) {
    fieldErrors.name = '페스티벌 이름을 입력해주세요.'
  }
  if (!form.startAt) {
    fieldErrors.startAt = '시작 일시를 입력해주세요.'
  } else if (new Date(form.startAt) <= new Date()) {
    fieldErrors.startAt = '시작 일시는 현재 이후여야 해요.'
  }
  if (!form.endAt) {
    fieldErrors.endAt = '종료 일시를 입력해주세요.'
  } else if (form.startAt && form.endAt <= form.startAt) {
    fieldErrors.endAt = '종료 일시는 시작 일시 이후여야 해요.'
  }
  if (!form.region) {
    fieldErrors.region = '지역을 선택해주세요.'
  }
  if (!form.locationDetail.trim()) {
    fieldErrors.locationDetail = '상세주소를 입력해주세요.'
  }
  if (!form.festivalCategory) {
    fieldErrors.festivalCategory = '카테고리를 선택해주세요.'
  }
  if (form.operatingStartTime && form.operatingEndTime && form.operatingEndTime <= form.operatingStartTime) {
    fieldErrors.operatingEndTime = '운영 종료 시간은 시작 시간 이후여야 해요.'
  }

  const ticketErrors = {}
  form.ticketTypes.forEach((ticket) => {
    const errors = validateTicketType(ticket)
    if (Object.keys(errors).length > 0) ticketErrors[ticket.key] = errors
  })

  if (form.ticketTypes.length === 0) {
    fieldErrors.ticketTypes = '티켓 종류를 최소 1개 이상 등록해주세요.'
  } else if (Object.keys(ticketErrors).length > 0) {
    fieldErrors.ticketTypes = '티켓 종류 정보를 확인해주세요.'
  }

  return { fieldErrors, ticketErrors }
}

//4단계 마법사 — 한 번에 다 보여주던 폼을 "기본 정보 → 운영 정보 → 분류·이미지 → 무대·티켓 설정"으로 나눈다.
//form state는 하나로 유지하고 화면만 갈아끼우므로, 단계를 오가도 입력값이 유실되지 않는다.
const TOTAL_STEPS = 4

const STEP_LABELS = {
  1: '기본 정보',
  2: '운영 정보',
  3: '분류·이미지',
  4: '무대·티켓 설정',
}

//"다음" 클릭 시 이 단계에 해당하는 필드만 막는다 — validate()는 항상 전체를 검사하지만,
//다른 단계의 에러(예: 아직 안 채운 4단계 티켓 종류)까지 1단계에서 막아버리면 안 되기 때문이다.
const STEP_FIELD_KEYS = {
  1: ['name', 'startAt', 'endAt', 'region', 'locationDetail'],
  2: ['operatingEndTime'],
  3: ['festivalCategory', 'thumbnail', 'detailImages'],
  4: ['ticketTypes'],
}

/** 백엔드 스펙(POST /api/host/festivals) 기준 주최자용 페스티벌 등록 화면. */
function HostFestivalNew() {
  const { user, isLoading: authLoading } = useAuth()
  const isHost = user?.role === 'HOST'
  const ticketKeySeq = useRef(0)

  const [form, setForm] = useState(() => ({
    name: '',
    description: '',
    startAt: defaultDateTime('09'),
    endAt: defaultDateTime('18'),
    region: '',
    locationDetail: '',
    latitude: null,
    longitude: null,
    festivalCategory: 'MUSIC',
    stageLayout: 'FRONT_STAGE',
    entryStartTime: '',
    operatingStartTime: '09:00',
    operatingEndTime: '18:00',
    ticketTypes: [createEmptyTicketType(0)],
  }))
  const [errors, setErrors] = useState({})
  const [ticketErrors, setTicketErrors] = useState({})
  const [thumbnail, setThumbnail] = useState(null)
  const [detailImages, setDetailImages] = useState([])
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(null)
  const [showAiDraftModal, setShowAiDraftModal] = useState(false)
  const [step, setStep] = useState(1)
  const [aiDraftNotice, setAiDraftNotice] = useState('')

  //AI 초안 적용 — 소개글은 있으면 덮어쓰고, 티켓 종류 제안이 있으면 기존 입력을 통째로 대체한다(비어있으면
  //기존 입력을 그대로 둔다). 가격·수량·판매기간처럼 AI가 안 준 값은 기본값으로 채우고, 전부 이후 폼에서
  //자유롭게 수정할 수 있다.
  function handleApplyAiDraft(draft) {
    setForm((prev) => {
      const suggestions = draft.ticketTypeSuggestions ?? []
      const ticketTypes = suggestions.length === 0
        ? prev.ticketTypes
        : suggestions.map((suggestion) => ({
            ...createEmptyTicketType(ticketKeySeq.current++),
            name: suggestion.name || '입장권',
            description: suggestion.description || '',
            price: suggestion.price != null ? String(suggestion.price) : AI_DRAFT_DEFAULT_PRICE,
            quantity: AI_DRAFT_DEFAULT_QUANTITY,
            ticketMode: suggestion.ticketMode === 'SEATED' ? 'SEATED' : 'STANDING',
            saleStartAt: nowDateTime(),
            saleEndAt: prev.startAt || defaultDateTime('09'),
          }))

      return {
        ...prev,
        description: draft.description || prev.description,
        ticketTypes,
      }
    })
    setErrors((prev) => ({ ...prev, description: undefined, ticketTypes: undefined }))
    setTicketErrors({})
    setShowAiDraftModal(false)
    setAiDraftNotice(
      (draft.ticketTypeSuggestions?.length ?? 0) > 0
        ? `AI 초안이 적용됐어요. 티켓 종류 ${draft.ticketTypeSuggestions.length}개는 4단계(무대·티켓 설정)에서 확인해주세요.`
        : 'AI 초안이 소개글에 적용됐어요.',
    )
  }

  //선택할 때마다 이전 선택을 교체한다 (1장만 허용)
  function handleThumbnailSelect(event) {
    const file = event.target.files?.[0] ?? null
    const message = validateThumbnail(file)
    setErrors((prev) => ({ ...prev, thumbnail: message || undefined }))
    setThumbnail(message ? null : file)
    if (message) event.target.value = ''
  }

  function handleRemoveThumbnail() {
    setThumbnail(null)
  }

  //선택할 때마다 이전 선택을 교체한다 (누적 선택은 지원하지 않음, 최대 2장)
  function handleDetailImagesSelect(event) {
    const files = Array.from(event.target.files ?? [])
    const message = validateDetailImages(files)
    setErrors((prev) => ({ ...prev, detailImages: message || undefined }))
    setDetailImages(message ? [] : files)
    if (message) event.target.value = ''
  }

  function handleRemoveDetailImage(index) {
    setDetailImages((prev) => prev.filter((_, i) => i !== index))
  }

  function handleChange(field) {
    return (event) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [field]: value }))
      setErrors((prev) => ({ ...prev, [field]: undefined }))
      setSubmitError('')
    }
  }

  function handleDateTimeChange(field) {
    return (value) => {
      setForm((prev) => ({ ...prev, [field]: value }))
      setErrors((prev) => ({ ...prev, [field]: undefined }))
      setSubmitError('')
    }
  }

  //카카오맵 클릭 선택 — 좌표는 그대로 저장하고, 매핑된 행정구역/상세주소가 있으면 같이 채운다(매핑 실패
  //시 region은 null로 와서 드롭다운을 건드리지 않는다 — 호스트가 직접 고르면 됨).
  function handleMapPick({ latitude, longitude, region, locationDetail }) {
    setForm((prev) => ({
      ...prev,
      latitude,
      longitude,
      region: region ?? prev.region,
      locationDetail: locationDetail || prev.locationDetail,
    }))
    setErrors((prev) => ({ ...prev, region: undefined, locationDetail: undefined }))
    setSubmitError('')
  }

  function handleCategorySelect(value) {
    setForm((prev) => ({ ...prev, festivalCategory: value }))
    setErrors((prev) => ({ ...prev, festivalCategory: undefined }))
  }

  function handleStageLayoutSelect(value) {
    setForm((prev) => ({ ...prev, stageLayout: value }))
  }

  function handleTicketChange(key, field) {
    return (event) => {
      const { value } = event.target
      setForm((prev) => ({
        ...prev,
        ticketTypes: prev.ticketTypes.map((ticket) =>
          ticket.key === key ? { ...ticket, [field]: value } : ticket,
        ),
      }))
      setTicketErrors((prev) => ({
        ...prev,
        [key]: { ...prev[key], [field]: undefined },
      }))
      setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
      setSubmitError('')
    }
  }

  //티켓별 판매 시작·종료 일시(DateTimeFields는 값을 문자열로 바로 넘겨준다, event 아님)
  function handleTicketDateTimeChange(key, field) {
    return (value) => {
      setForm((prev) => ({
        ...prev,
        ticketTypes: prev.ticketTypes.map((ticket) =>
          ticket.key === key ? { ...ticket, [field]: value } : ticket,
        ),
      }))
      setTicketErrors((prev) => ({
        ...prev,
        [key]: { ...prev[key], [field]: undefined },
      }))
      setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
      setSubmitError('')
    }
  }

  function handleTicketModeChange(key, mode) {
    setForm((prev) => ({
      ...prev,
      ticketTypes: prev.ticketTypes.map((ticket) =>
        ticket.key === key
          ? mode === 'STANDING'
            ? { ...ticket, ticketMode: mode, zone: '', seatRows: [] }
            : {
                ...ticket,
                ticketMode: mode,
                seatRows: ticket.seatRows.length > 0 ? ticket.seatRows : [{ seatCount: '', excludedSeats: '' }],
              }
          : ticket,
      ),
    }))
    setTicketErrors((prev) => ({ ...prev, [key]: {} }))
    setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
  }

  function handleAddSeatRow(key) {
    setForm((prev) => ({
      ...prev,
      ticketTypes: prev.ticketTypes.map((ticket) =>
        ticket.key === key
          ? { ...ticket, seatRows: [...ticket.seatRows, { seatCount: '', excludedSeats: '' }] }
          : ticket,
      ),
    }))
    setTicketErrors((prev) => ({
      ...prev,
      [key]: { ...prev[key], seatRows: undefined, seatRowsDetail: undefined },
    }))
    setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
  }

  function handleRemoveSeatRow(key, rowIndex) {
    setForm((prev) => ({
      ...prev,
      ticketTypes: prev.ticketTypes.map((ticket) =>
        ticket.key === key
          ? { ...ticket, seatRows: ticket.seatRows.filter((_, index) => index !== rowIndex) }
          : ticket,
      ),
    }))
    setTicketErrors((prev) => ({
      ...prev,
      [key]: {
        ...prev[key],
        seatRowsDetail: prev[key]?.seatRowsDetail?.filter((_, index) => index !== rowIndex),
      },
    }))
  }

  function handleSeatRowChange(key, rowIndex, field, value) {
    setForm((prev) => ({
      ...prev,
      ticketTypes: prev.ticketTypes.map((ticket) =>
        ticket.key === key
          ? {
              ...ticket,
              seatRows: ticket.seatRows.map((row, index) =>
                index === rowIndex ? { ...row, [field]: value } : row,
              ),
            }
          : ticket,
      ),
    }))
    setTicketErrors((prev) => ({
      ...prev,
      [key]: {
        ...prev[key],
        seatRows: undefined,
        seatRowsDetail: prev[key]?.seatRowsDetail?.map((rowError, index) =>
          index === rowIndex ? { ...rowError, [field]: undefined } : rowError,
        ),
      },
    }))
    setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
  }

  function handlePositionDirectionChange(key, direction) {
    setForm((prev) => ({
      ...prev,
      ticketTypes: prev.ticketTypes.map((ticket) =>
        ticket.key === key ? { ...ticket, positionDirection: direction } : ticket,
      ),
    }))
  }

  //전면형(FRONT_STAGE)에서 SEATED 구역의 표시 순서를 옮긴다. STANDING 티켓이 사이에 끼어 있어도
  //"SEATED끼리의 순서"만 한 칸 이동하도록, 인접한 SEATED 티켓이 있던 자리로 통째로 옮겨 넣는다
  //(화면에 보이는 카드 위치도 같이 바뀌어야 주최자가 순서가 바뀐 걸 바로 알 수 있다).
  function handleMoveTicket(key, direction) {
    setForm((prev) => {
      const seatedKeys = prev.ticketTypes.filter((t) => t.ticketMode === 'SEATED').map((t) => t.key)
      const seatedIndex = seatedKeys.indexOf(key)
      const targetSeatedIndex = direction === 'up' ? seatedIndex - 1 : seatedIndex + 1
      if (targetSeatedIndex < 0 || targetSeatedIndex >= seatedKeys.length) return prev

      const targetKey = seatedKeys[targetSeatedIndex]
      const list = [...prev.ticketTypes]
      const fromIndex = list.findIndex((t) => t.key === key)
      const [moved] = list.splice(fromIndex, 1)
      const targetIndex = list.findIndex((t) => t.key === targetKey)
      list.splice(direction === 'up' ? targetIndex : targetIndex + 1, 0, moved)
      return { ...prev, ticketTypes: list }
    })
  }

  function handleAddTicket() {
    ticketKeySeq.current += 1
    setForm((prev) => ({
      ...prev,
      ticketTypes: [...prev.ticketTypes, createEmptyTicketType(ticketKeySeq.current)],
    }))
    setErrors((prev) => ({ ...prev, ticketTypes: undefined }))
  }

  function handleRemoveTicket(key) {
    setForm((prev) => ({
      ...prev,
      ticketTypes: prev.ticketTypes.filter((ticket) => ticket.key !== key),
    }))
    setTicketErrors((prev) => {
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  //현재 단계에 해당하는 필드만 확인하고 다음 단계로 넘어간다. validate()는 항상 폼 전체를 검사하므로,
  //아직 채우지 않은 다른 단계의 에러 때문에 진행이 막히지 않도록 이 단계의 필드만 걸러서 본다.
  function goToNextStep() {
    const { fieldErrors, ticketErrors: nextTicketErrors } = validate(form, thumbnail, detailImages)
    setErrors(fieldErrors)
    setTicketErrors(nextTicketErrors)
    const hasBlockingError = STEP_FIELD_KEYS[step].some((key) => fieldErrors[key])
    if (hasBlockingError) return
    setStep((prev) => Math.min(prev + 1, TOTAL_STEPS))
  }

  function handleBack() {
    setStep((prev) => Math.max(prev - 1, 1))
  }

  //폼 전체를 감싸는 <form>의 제출 이벤트 — 마지막 단계가 아니면 "다음"과 같은 동작(다음 단계로),
  //마지막 단계면 실제 등록을 진행한다. 인풋에서 Enter를 눌러도 이 한 곳으로 모인다.
  function handleFormSubmit(event) {
    event.preventDefault()
    if (step < TOTAL_STEPS) {
      goToNextStep()
      return
    }
    submitFestival()
  }

  async function submitFestival() {
    const { fieldErrors, ticketErrors: nextTicketErrors } = validate(form, thumbnail, detailImages)
    setErrors(fieldErrors)
    setTicketErrors(nextTicketErrors)
    if (Object.keys(fieldErrors).length > 0) return

    setSubmitting(true)
    setSubmitError('')

    try {
      //이미지가 있으면 먼저 업로드해 URL만 받고, 그 URL을 등록 요청 body에 그대로 실어 보낸다.
      let thumbnailImageUrl = null
      let detailImageUrls = []
      if (thumbnail || detailImages.length > 0) {
        const uploadResponse = await uploadFestivalImages({ thumbnail, detailImages })
        thumbnailImageUrl = uploadResponse.data.data.thumbnailImageUrl
        detailImageUrls = uploadResponse.data.data.detailImageUrls
      }

      //전면형(FRONT_STAGE)은 SEATED끼리의 순서(줄 세운 순서)로 격자 위치를 매긴다 — 한 줄에 3개씩.
      const seatedKeysInOrder = form.ticketTypes
        .filter((ticket) => ticket.ticketMode === 'SEATED')
        .map((ticket) => ticket.key)

      const response = await createFestival({
        name: form.name.trim(),
        description: form.description.trim(),
        startAt: form.startAt,
        endAt: form.endAt,
        region: form.region,
        locationDetail: form.locationDetail.trim(),
        latitude: form.latitude,
        longitude: form.longitude,
        festivalCategory: form.festivalCategory,
        stageLayout: form.stageLayout,
        entryStartTime: form.entryStartTime || null,
        operatingStartTime: form.operatingStartTime || null,
        operatingEndTime: form.operatingEndTime || null,
        thumbnailImageUrl,
        detailImageUrls,
        ticketTypes: form.ticketTypes.map((ticket) => {
          const isSeated = ticket.ticketMode === 'SEATED'
          let positionRow = null
          let positionCol = null
          let positionAngle = null
          if (isSeated) {
            if (form.stageLayout === 'FRONT_STAGE') {
              const seatedIndex = seatedKeysInOrder.indexOf(ticket.key)
              positionRow = Math.floor(seatedIndex / 3) + 1
              positionCol = (seatedIndex % 3) + 1
            } else {
              positionAngle = DIRECTION_TO_ANGLE[ticket.positionDirection]
            }
          }

          return {
            name: ticket.name.trim(),
            description: ticket.description.trim() || null,
            price: Number(ticket.price),
            ticketMode: ticket.ticketMode,
            zone: isSeated ? ticket.zone.trim() : null,
            seatLayout: isSeated
              ? {
                  rows: ticket.seatRows.map((row) => ({
                    seatCount: Number(row.seatCount),
                    excludedSeats: parseExcludedSeats(row.excludedSeats).values,
                  })),
                }
              : null,
            positionRow,
            positionCol,
            positionAngle,
            quantity: isSeated ? null : Number(ticket.quantity),
            saleStartAt: ticket.saleStartAt,
            saleEndAt: ticket.saleEndAt,
            ticketDate: ticket.ticketDate || null,
          }
        }),
      })
      setSubmitted(response.data.data)
    } catch (error) {
      const errorCode = error.response?.data?.errorCode
      if (CREATE_FESTIVAL_ERROR_MESSAGES[errorCode]) {
        setSubmitError(CREATE_FESTIVAL_ERROR_MESSAGES[errorCode])
      } else if (IMAGE_ERROR_MESSAGES[errorCode]) {
        setSubmitError(IMAGE_ERROR_MESSAGES[errorCode])
      } else {
        setSubmitError('등록에 실패했어요. 잠시 후 다시 시도해주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (authLoading) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <p className={styles.loading}>불러오는 중…</p>
        </div>
      </main>
    )
  }

  if (!isHost) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <LockIcon size={40} aria-hidden="true" className={styles.infoIconMuted} />
            <h1 className={styles.infoTitle}>주최자만 이용 가능한 페이지입니다</h1>
            <p className={styles.infoDescription}>
              페스티벌을 등록하려면 먼저 주최자 신청 후 승인을 받아주세요.
            </p>
            <Link to="/host-application" className={styles.infoButton}>
              주최자 신청하러 가기
              <ArrowRightIcon size={16} aria-hidden="true" />
            </Link>
          </div>
        </div>
      </main>
    )
  }

  if (submitted) {
    return (
      <main className={styles.main}>
        <div className={styles.card}>
          <div className={styles.infoState}>
            <CircleCheckIcon size={40} aria-hidden="true" className={styles.infoIconSuccess} />
            <h1 className={styles.infoTitle}>등록이 완료되었습니다</h1>
            <p className={styles.infoDescription}>
              운영자 승인 후 공개됩니다. ({submitted.name})
            </p>
            <Link to="/" className={styles.infoLink}>
              홈으로 돌아가기
            </Link>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <div className={styles.stepMeta}>
          <h1 className={styles.title}>페스티벌 등록</h1>
          <span className={styles.stepCount}>{step} / {TOTAL_STEPS}</span>
        </div>
        <div className={styles.stepProgress}>
          {Array.from({ length: TOTAL_STEPS }, (_, index) => (
            <div
              key={index}
              className={`${styles.stepProgressSegment} ${index < step ? styles.stepProgressSegmentActive : ''}`}
            />
          ))}
        </div>
        <p className={styles.stepLabel}>{STEP_LABELS[step]}</p>

        {step === 1 && (
          <p className={styles.banner}>
            <InfoIcon size={16} aria-hidden="true" />
            등록 후 운영자 승인이 완료되어야 목록에 공개됩니다.
          </p>
        )}

        {step === 1 && (
          <>
            <button
              type="button"
              onClick={() => setShowAiDraftModal(true)}
              className="mb-4 flex items-center justify-center gap-2 rounded-xl border border-blue-200 bg-blue-50 py-2.5 text-sm font-bold text-blue-700 transition hover:bg-blue-100"
              style={{ width: '100%' }}
            >
              <SparklesIcon size={16} aria-hidden="true" />
              AI로 초안 채우기
            </button>
            {aiDraftNotice && (
              <p className={styles.banner} role="status">
                <SparklesIcon size={16} aria-hidden="true" />
                {aiDraftNotice}
              </p>
            )}
            {showAiDraftModal && (
              <AiDraftModal onClose={() => setShowAiDraftModal(false)} onApply={handleApplyAiDraft} />
            )}
          </>
        )}

        {submitError && (
          <p className={styles.submitError} role="alert">
            <CircleAlertIcon size={16} aria-hidden="true" />
            {submitError}
          </p>
        )}

        <form className={styles.form} onSubmit={handleFormSubmit} noValidate>
          {step === 1 && (
          <div className={styles.field}>
            <label htmlFor="name" className={styles.label}>
              페스티벌 이름
            </label>
            <input
              id="name"
              type="text"
              className={styles.input}
              placeholder="페스티벌 이름을 입력하세요"
              value={form.name}
              onChange={handleChange('name')}
              aria-invalid={Boolean(errors.name)}
            />
            {errors.name && <p className={styles.errorText}>{errors.name}</p>}
          </div>
          )}

          {step === 1 && (
          <div className={styles.field}>
            <label htmlFor="description" className={styles.label}>
              소개 <span className={styles.optional}>(선택)</span>
            </label>
            <textarea
              id="description"
              className={styles.textarea}
              placeholder="페스티벌을 소개해 주세요."
              value={form.description}
              onChange={handleChange('description')}
              rows={5}
            />
          </div>
          )}

          {step === 1 && (
          <div className={styles.row}>
            <div className={styles.field}>
              <label htmlFor="startAt-date" className={styles.label}>
                시작 일시
              </label>
              <DateTimeFields id="startAt" value={form.startAt} onChange={handleDateTimeChange('startAt')} invalid={Boolean(errors.startAt)} />
              {errors.startAt && <p className={styles.errorText}>{errors.startAt}</p>}
            </div>

            <div className={styles.field}>
              <label htmlFor="endAt-date" className={styles.label}>
                종료 일시
              </label>
              <DateTimeFields id="endAt" value={form.endAt} onChange={handleDateTimeChange('endAt')} invalid={Boolean(errors.endAt)} />
              {errors.endAt && <p className={styles.errorText}>{errors.endAt}</p>}
            </div>
          </div>
          )}

          {step === 1 && (
          <div className={styles.field}>
            <label className={styles.label}>
              위치 <span className={styles.optional}>(선택, 지도를 클릭하면 아래 지역·상세주소가 자동으로 채워져요)</span>
            </label>
            <KakaoMap mode="pick" latitude={form.latitude} longitude={form.longitude} onPick={handleMapPick} />
          </div>
          )}

          {step === 1 && (
          <div className={styles.row}>
            <div className={styles.field}>
              <label htmlFor="region" className={styles.label}>
                지역
              </label>
              <select
                id="region"
                className={styles.input}
                value={form.region}
                onChange={handleChange('region')}
                aria-invalid={Boolean(errors.region)}
              >
                <option value="">선택해주세요</option>
                {REGION_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {errors.region && <p className={styles.errorText}>{errors.region}</p>}
            </div>

            <div className={styles.field}>
              <label htmlFor="locationDetail" className={styles.label}>
                상세주소
              </label>
              <input
                id="locationDetail"
                type="text"
                className={styles.input}
                placeholder="예: 잠실동 올림픽주경기장"
                value={form.locationDetail}
                onChange={handleChange('locationDetail')}
                aria-invalid={Boolean(errors.locationDetail)}
              />
              {errors.locationDetail && <p className={styles.errorText}>{errors.locationDetail}</p>}
            </div>
          </div>
          )}

          {step === 2 && (
          <div className={styles.field}>
            <label htmlFor="entryStartTime" className={styles.label}>
              입장 시작 시간 <span className={styles.optional}>(선택, 구매자에게 안내되는 참고용 정보)</span>
            </label>
            <input
              id="entryStartTime"
              type="time"
              className={styles.input}
              value={form.entryStartTime}
              onChange={handleChange('entryStartTime')}
            />
          </div>
          )}

          {step === 2 && (
          <div className={styles.row}>
            <div className={styles.field}>
              <label htmlFor="operatingStartTime" className={styles.label}>
                운영 시작 시간 <span className={styles.optional}>(선택)</span>
              </label>
              <input
                id="operatingStartTime"
                type="time"
                className={styles.input}
                value={form.operatingStartTime}
                onChange={handleChange('operatingStartTime')}
              />
            </div>

            <div className={styles.field}>
              <label htmlFor="operatingEndTime" className={styles.label}>
                운영 종료 시간 <span className={styles.optional}>(선택)</span>
              </label>
              <input
                id="operatingEndTime"
                type="time"
                className={styles.input}
                value={form.operatingEndTime}
                onChange={handleChange('operatingEndTime')}
                aria-invalid={Boolean(errors.operatingEndTime)}
              />
              {errors.operatingEndTime && <p className={styles.errorText}>{errors.operatingEndTime}</p>}
            </div>
          </div>
          )}

          {step === 3 && (
          <>
          <div className={styles.field}>
            <span className={styles.label}>카테고리</span>
            <div className={styles.categoryGroup} role="radiogroup" aria-label="카테고리">
              {CATEGORY_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={form.festivalCategory === option.value}
                  className={`${styles.categoryOption} ${
                    form.festivalCategory === option.value ? styles.categoryOptionActive : ''
                  }`}
                  onClick={() => handleCategorySelect(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
            {errors.festivalCategory && <p className={styles.errorText}>{errors.festivalCategory}</p>}
          </div>

          <div className={styles.field}>
            <label htmlFor="thumbnail" className={styles.label}>
              대표 이미지(썸네일) <span className={styles.optional}>(선택, 1장·10MB 이하)</span>
            </label>
            {/* 브라우저 기본 파일 입력은 "파일 선택"과 "선택된 파일 없음"이 한 칸에 붙어 헷갈린다는 QA 피드백 — 버튼과 상태 문구를 분리한다. */}
            <div className={styles.filePicker}>
              <label htmlFor="thumbnail" className={styles.fileButton}>
                이미지 선택
              </label>
              <span className={styles.fileStatus}>{thumbnail ? '1장 선택됨' : '선택된 파일 없음'}</span>
            </div>
            <input
              id="thumbnail"
              type="file"
              accept="image/*"
              className={styles.fileInputHidden}
              onChange={handleThumbnailSelect}
              aria-invalid={Boolean(errors.thumbnail)}
            />
            {errors.thumbnail && <p className={styles.errorText}>{errors.thumbnail}</p>}

            {thumbnail && (
              <div className={styles.ticketList}>
                <div className={styles.ticketRowHeader}>
                  <span className={styles.ticketRowTitle}>
                    {thumbnail.name} ({(thumbnail.size / (1024 * 1024)).toFixed(1)}MB)
                  </span>
                  <button type="button" className={styles.ticketRemove} onClick={handleRemoveThumbnail}>
                    <Trash2Icon size={14} aria-hidden="true" />
                    제거
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className={styles.field}>
            <label htmlFor="detailImages" className={styles.label}>
              본문 이미지 <span className={styles.optional}>(선택, 최대 2장·장당 10MB)</span>
            </label>
            <div className={styles.filePicker}>
              <label htmlFor="detailImages" className={styles.fileButton}>
                이미지 선택
              </label>
              <span className={styles.fileStatus}>
                {detailImages.length > 0 ? `${detailImages.length}장 선택됨` : '선택된 파일 없음'}
              </span>
            </div>
            <input
              id="detailImages"
              type="file"
              accept="image/*"
              multiple
              className={styles.fileInputHidden}
              onChange={handleDetailImagesSelect}
              aria-invalid={Boolean(errors.detailImages)}
            />
            {errors.detailImages && <p className={styles.errorText}>{errors.detailImages}</p>}

            {detailImages.length > 0 && (
              <div className={styles.ticketList}>
                {detailImages.map((file, index) => (
                  <div className={styles.ticketRowHeader} key={`${file.name}-${index}`}>
                    <span className={styles.ticketRowTitle}>
                      {file.name} ({(file.size / (1024 * 1024)).toFixed(1)}MB)
                    </span>
                    <button
                      type="button"
                      className={styles.ticketRemove}
                      onClick={() => handleRemoveDetailImage(index)}
                    >
                      <Trash2Icon size={14} aria-hidden="true" />
                      제거
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
          </>
          )}

          {step === 4 && (
          <>
          <div className={styles.field}>
            <span className={styles.label}>무대 배치 방식</span>
            <div className={styles.stageLayoutGroup} role="radiogroup" aria-label="무대 배치 방식">
              {STAGE_LAYOUT_OPTIONS.map(({ value, label, description, Icon }) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={form.stageLayout === value}
                  className={`${styles.stageLayoutOption} ${
                    form.stageLayout === value ? styles.stageLayoutOptionActive : ''
                  }`}
                  onClick={() => handleStageLayoutSelect(value)}
                >
                  <Icon size={28} aria-hidden="true" className={styles.stageLayoutIcon} />
                  <span className={styles.stageLayoutTitle}>{label}</span>
                  <span className={styles.stageLayoutDesc}>{description}</span>
                </button>
              ))}
            </div>
          </div>

          <div className={styles.field}>
            <div className={styles.ticketHeader}>
              <span className={styles.label}>티켓 종류</span>
              <span className={styles.ticketHint}>최소 1개 이상 등록해야 합니다.</span>
            </div>

            {errors.ticketTypes && <p className={styles.errorText}>{errors.ticketTypes}</p>}

            <div className={styles.ticketList}>
              {(() => {
                //위/아래 이동 버튼의 비활성 여부 판단용 — SEATED끼리의 순서에서 맨 앞/맨 뒤인지 확인한다.
                const seatedKeys = form.ticketTypes
                  .filter((t) => t.ticketMode === 'SEATED')
                  .map((t) => t.key)

                return form.ticketTypes.map((ticket, index) => {
                const rowErrors = ticketErrors[ticket.key] ?? {}
                const seatedIndex = seatedKeys.indexOf(ticket.key)
                const showOrderButtons = ticket.ticketMode === 'SEATED' && form.stageLayout === 'FRONT_STAGE'
                return (
                  <div className={styles.ticketRow} key={ticket.key}>
                    <div className={styles.ticketRowHeader}>
                      <span className={styles.ticketRowTitle}>티켓 {index + 1}</span>
                      <div className={styles.ticketRowActions}>
                        {showOrderButtons && (
                          <div className={styles.orderButtons}>
                            <button
                              type="button"
                              className={styles.orderButton}
                              disabled={seatedIndex <= 0}
                              onClick={() => handleMoveTicket(ticket.key, 'up')}
                              aria-label={`티켓 ${index + 1} 순서 위로 이동`}
                            >
                              <ChevronUpIcon size={14} aria-hidden="true" />
                            </button>
                            <button
                              type="button"
                              className={styles.orderButton}
                              disabled={seatedIndex < 0 || seatedIndex >= seatedKeys.length - 1}
                              onClick={() => handleMoveTicket(ticket.key, 'down')}
                              aria-label={`티켓 ${index + 1} 순서 아래로 이동`}
                            >
                              <ChevronDownIcon size={14} aria-hidden="true" />
                            </button>
                          </div>
                        )}
                        <button
                          type="button"
                          className={styles.ticketRemove}
                          onClick={() => handleRemoveTicket(ticket.key)}
                        >
                          <Trash2Icon size={14} aria-hidden="true" />
                          삭제
                        </button>
                      </div>
                    </div>
                    <div className={styles.categoryGroup} role="radiogroup" aria-label={`티켓 ${index + 1} 판매 방식`}>
                      <button
                        type="button"
                        role="radio"
                        aria-checked={ticket.ticketMode === 'STANDING'}
                        className={`${styles.categoryOption} ${
                          ticket.ticketMode === 'STANDING' ? styles.categoryOptionActive : ''
                        }`}
                        onClick={() => handleTicketModeChange(ticket.key, 'STANDING')}
                      >
                        스탠딩
                      </button>
                      <button
                        type="button"
                        role="radio"
                        aria-checked={ticket.ticketMode === 'SEATED'}
                        className={`${styles.categoryOption} ${
                          ticket.ticketMode === 'SEATED' ? styles.categoryOptionActive : ''
                        }`}
                        onClick={() => handleTicketModeChange(ticket.key, 'SEATED')}
                      >
                        좌석 선택형
                      </button>
                    </div>
                    <div className={styles.ticketFields}>
                      <div className={styles.ticketField}>
                        <input
                          type="text"
                          className={styles.input}
                          placeholder="이름 (예: 일반)"
                          value={ticket.name}
                          onChange={handleTicketChange(ticket.key, 'name')}
                          aria-invalid={Boolean(rowErrors.name)}
                          aria-label={`티켓 ${index + 1} 이름`}
                        />
                        {rowErrors.name && <p className={styles.errorText}>{rowErrors.name}</p>}
                      </div>
                      <div className={styles.ticketField}>
                        <input
                          type="number"
                          min="0"
                          className={styles.input}
                          placeholder="가격 (원)"
                          value={ticket.price}
                          onChange={handleTicketChange(ticket.key, 'price')}
                          aria-invalid={Boolean(rowErrors.price)}
                          aria-label={`티켓 ${index + 1} 가격`}
                        />
                        {rowErrors.price && <p className={styles.errorText}>{rowErrors.price}</p>}
                      </div>
                      {ticket.ticketMode === 'SEATED' ? (
                        <div className={styles.ticketField}>
                          <input
                            type="text"
                            className={styles.input}
                            placeholder="구역명 (예: VIP)"
                            value={ticket.zone}
                            onChange={handleTicketChange(ticket.key, 'zone')}
                            aria-invalid={Boolean(rowErrors.zone)}
                            aria-label={`티켓 ${index + 1} 구역명`}
                          />
                          {rowErrors.zone && <p className={styles.errorText}>{rowErrors.zone}</p>}
                        </div>
                      ) : (
                        <div className={styles.ticketField}>
                          <input
                            type="number"
                            min="1"
                            className={styles.input}
                            placeholder="수량"
                            value={ticket.quantity}
                            onChange={handleTicketChange(ticket.key, 'quantity')}
                            aria-invalid={Boolean(rowErrors.quantity)}
                            aria-label={`티켓 ${index + 1} 수량`}
                          />
                          {rowErrors.quantity && <p className={styles.errorText}>{rowErrors.quantity}</p>}
                        </div>
                      )}
                    </div>

                    {ticket.ticketMode === 'SEATED' && (
                      <div className={styles.seatLayoutSection}>
                        <div className={styles.seatLayoutHeader}>
                          <span className={styles.seatLayoutLabel}>좌석 배치</span>
                          <span className={styles.seatLayoutTotal}>
                            총 좌석 수: {totalSeatCount(ticket.seatRows)}석
                          </span>
                        </div>
                        {rowErrors.seatRows && <p className={styles.errorText}>{rowErrors.seatRows}</p>}

                        <div className={styles.seatRowList}>
                          {ticket.seatRows.map((row, rowIndex) => {
                            const rowError = rowErrors.seatRowsDetail?.[rowIndex] ?? {}
                            return (
                              <div className={styles.seatRowItem} key={rowIndex}>
                                <span className={styles.seatRowIndex}>{rowIndex + 1}열</span>
                                <div className={styles.seatRowField}>
                                  <input
                                    type="number"
                                    min="1"
                                    className={styles.input}
                                    placeholder="좌석 수"
                                    value={row.seatCount}
                                    onChange={(event) =>
                                      handleSeatRowChange(ticket.key, rowIndex, 'seatCount', event.target.value)
                                    }
                                    aria-invalid={Boolean(rowError.seatCount)}
                                    aria-label={`티켓 ${index + 1} ${rowIndex + 1}열 좌석 수`}
                                  />
                                  {rowError.seatCount && <p className={styles.errorText}>{rowError.seatCount}</p>}
                                </div>
                                <div className={styles.seatRowField}>
                                  <input
                                    type="text"
                                    className={styles.input}
                                    placeholder="결번 좌석 번호 (예: 5,6)"
                                    value={row.excludedSeats}
                                    onChange={(event) =>
                                      handleSeatRowChange(ticket.key, rowIndex, 'excludedSeats', event.target.value)
                                    }
                                    aria-invalid={Boolean(rowError.excludedSeats)}
                                    aria-label={`티켓 ${index + 1} ${rowIndex + 1}열 결번 좌석 번호`}
                                  />
                                  {rowError.excludedSeats && (
                                    <p className={styles.errorText}>{rowError.excludedSeats}</p>
                                  )}
                                </div>
                                <button
                                  type="button"
                                  className={styles.seatRowRemove}
                                  onClick={() => handleRemoveSeatRow(ticket.key, rowIndex)}
                                  aria-label={`티켓 ${index + 1} ${rowIndex + 1}열 삭제`}
                                >
                                  <Trash2Icon size={14} aria-hidden="true" />
                                </button>
                              </div>
                            )
                          })}
                        </div>

                        <button
                          type="button"
                          className={styles.addSeatRow}
                          onClick={() => handleAddSeatRow(ticket.key)}
                        >
                          <PlusIcon size={14} aria-hidden="true" />행 추가
                        </button>

                        {form.stageLayout === 'CENTER_STAGE' && (
                          <div className={styles.directionPicker}>
                            <span className={styles.seatLayoutLabel}>구역 위치 (무대 기준 방향)</span>
                            <div
                              className={styles.directionGrid}
                              role="radiogroup"
                              aria-label={`티켓 ${index + 1} 구역 위치`}
                            >
                              {DIRECTION_GRID_CELLS.map((cell, cellIndex) =>
                                cell.direction ? (
                                  <button
                                    key={cell.direction}
                                    type="button"
                                    role="radio"
                                    aria-checked={ticket.positionDirection === cell.direction}
                                    className={`${styles.directionCell} ${
                                      ticket.positionDirection === cell.direction ? styles.directionCellActive : ''
                                    }`}
                                    onClick={() => handlePositionDirectionChange(ticket.key, cell.direction)}
                                  >
                                    {cell.label}
                                  </button>
                                ) : (
                                  <span key={`stage-${cellIndex}`} className={styles.directionStageCell}>
                                    STAGE
                                  </span>
                                ),
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    )}

                    <div className={styles.ticketField} style={{ marginTop: 10 }}>
                      <input
                        type="text"
                        maxLength={50}
                        className={styles.input}
                        placeholder="설명 (선택, 구매 화면에서 티켓 이름 아래 표시돼요)"
                        value={ticket.description}
                        onChange={handleTicketChange(ticket.key, 'description')}
                        aria-invalid={Boolean(rowErrors.description)}
                        aria-label={`티켓 ${index + 1} 설명`}
                      />
                      {rowErrors.description && <p className={styles.errorText}>{rowErrors.description}</p>}
                    </div>

                    <div className={styles.row} style={{ marginTop: 10 }}>
                      <div className={styles.ticketField}>
                        <span style={{ fontSize: 12, color: 'var(--fgColor-muted)', fontWeight: 700 }}>
                          판매 시작 일시
                        </span>
                        <DateTimeFields
                          id={`ticket-${ticket.key}-saleStartAt`}
                          value={ticket.saleStartAt}
                          onChange={handleTicketDateTimeChange(ticket.key, 'saleStartAt')}
                          invalid={Boolean(rowErrors.saleStartAt)}
                        />
                        {rowErrors.saleStartAt && <p className={styles.errorText}>{rowErrors.saleStartAt}</p>}
                      </div>
                      <div className={styles.ticketField}>
                        <span style={{ fontSize: 12, color: 'var(--fgColor-muted)', fontWeight: 700 }}>
                          판매 종료 일시
                        </span>
                        <DateTimeFields
                          id={`ticket-${ticket.key}-saleEndAt`}
                          value={ticket.saleEndAt}
                          onChange={handleTicketDateTimeChange(ticket.key, 'saleEndAt')}
                          invalid={Boolean(rowErrors.saleEndAt)}
                        />
                        {rowErrors.saleEndAt && <p className={styles.errorText}>{rowErrors.saleEndAt}</p>}
                      </div>
                    </div>

                    <div className={styles.ticketField} style={{ marginTop: 10 }}>
                      <span style={{ fontSize: 12, color: 'var(--fgColor-muted)', fontWeight: 700 }}>
                        날짜 지정 <span className={styles.optional}>(선택, 이틀 이상 지속되는 페스티벌에서 특정 날짜 전용 티켓일 때만)</span>
                      </span>
                      <input
                        type="date"
                        className={styles.input}
                        value={ticket.ticketDate}
                        onChange={handleTicketChange(ticket.key, 'ticketDate')}
                        aria-label={`티켓 ${index + 1} 날짜`}
                      />
                    </div>
                  </div>
                )
              })
              })()}
            </div>

            <button type="button" className={styles.addTicket} onClick={handleAddTicket}>
              <PlusIcon size={16} aria-hidden="true" />
              티켓 종류 추가
            </button>
          </div>
          </>
          )}

          <div className={step === 1 ? styles.stepNav : `${styles.stepNav} ${styles.stepNavTwo}`}>
            {step > 1 && (
              <button type="button" className={styles.stepBack} onClick={handleBack}>
                <ArrowLeftIcon size={16} aria-hidden="true" />
                이전
              </button>
            )}
            <button type="submit" className={styles.submit} disabled={submitting}>
              {step < TOTAL_STEPS ? '다음' : submitting ? '등록 중…' : '등록하기'}
              <ArrowRightIcon size={16} aria-hidden="true" />
            </button>
          </div>
        </form>
      </div>
    </main>
  )
}

export default HostFestivalNew
