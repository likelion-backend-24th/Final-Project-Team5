import { useEffect, useRef, useState } from 'react'
import jsQR from 'jsqr'
import { CameraOffIcon } from 'lucide-react'

//매 프레임(60fps) 디코딩하면 현장에서 쓰는 휴대폰이 뜨거워지고 화면이 버벅인다.
//사람이 QR을 카메라에 갖다 대는 동작 기준으로 0.2초 간격이면 체감상 즉시 인식된다.
const SCAN_INTERVAL_MS = 200

function toCameraErrorMessage(error) {
  if (error?.name === 'NotAllowedError') {
    return '카메라 권한이 거부되었어요. 브라우저 주소창의 권한 설정에서 카메라를 허용해주세요.'
  }
  if (error?.name === 'NotFoundError' || error?.name === 'OverconstrainedError') {
    return '사용할 수 있는 카메라를 찾지 못했어요.'
  }
  return '카메라를 켤 수 없어요. 아래 입장 코드 입력을 이용해주세요.'
}

/**
 * 후면 카메라 영상을 띄우고 QR이 잡히면 원문 문자열을 onScan으로 넘긴다.
 * 카메라를 못 쓰는 환경(권한 거부·HTTPS 아님·카메라 없음)에서는 안내만 보여주고,
 * 입장 코드 수동 입력이 대체 수단으로 남는다.
 */
function QrScanner({ onScan, paused = false }) {
  const videoRef = useRef(null)
  const canvasRef = useRef(null)
  //카메라 스트림을 한 번만 열기 위해 effect 의존성에서 제외하고 ref로 최신 값을 읽는다.
  const onScanRef = useRef(onScan)
  const pausedRef = useRef(paused)
  const [cameraError, setCameraError] = useState('')
  const [ready, setReady] = useState(false)

  useEffect(() => {
    onScanRef.current = onScan
  }, [onScan])

  useEffect(() => {
    pausedRef.current = paused
  }, [paused])

  useEffect(() => {
    let stream = null
    let timer = null
    let cancelled = false

    function decodeFrame() {
      if (pausedRef.current) return

      const video = videoRef.current
      const canvas = canvasRef.current
      if (!video || !canvas || video.readyState !== video.HAVE_ENOUGH_DATA) return

      const width = video.videoWidth
      const height = video.videoHeight
      if (!width || !height) return

      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d', { willReadFrequently: true })
      context.drawImage(video, 0, 0, width, height)

      const image = context.getImageData(0, 0, width, height)
      //dontInvert: 흰 바탕 QR만 찾는다. 반전 시도까지 켜면 프레임당 비용이 2배가 되는데
      //우리가 발급하는 QR은 항상 흰 바탕이라 이득이 없다.
      const result = jsQR(image.data, width, height, { inversionAttempts: 'dontInvert' })
      if (result?.data) {
        onScanRef.current(result.data)
      }
    }

    async function start() {
      try {
        //facingMode environment = 후면 카메라. 노트북처럼 후면이 없으면 브라우저가 알아서 전면을 준다.
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
      } catch (error) {
        if (!cancelled) setCameraError(toCameraErrorMessage(error))
        return
      }

      if (cancelled) {
        stream.getTracks().forEach((track) => track.stop())
        return
      }

      const video = videoRef.current
      if (!video) return
      video.srcObject = stream
      try {
        await video.play()
      } catch {
        //자동재생이 막히는 경우가 있어도 사용자가 화면을 터치하면 재생되므로 치명적이지 않다.
      }
      if (cancelled) return
      setReady(true)
      timer = setInterval(decodeFrame, SCAN_INTERVAL_MS)
    }

    if (navigator.mediaDevices?.getUserMedia) {
      start()
    } else {
      //https가 아닌 주소(사내 IP 등)에서는 브라우저가 mediaDevices 자체를 막는다.
      setCameraError('이 브라우저·주소에서는 카메라를 쓸 수 없어요(HTTPS 필요). 아래 입장 코드 입력을 이용해주세요.')
    }

    return () => {
      cancelled = true
      if (timer) clearInterval(timer)
      if (stream) stream.getTracks().forEach((track) => track.stop())
    }
  }, [])

  if (cameraError) {
    return (
      <div className="flex aspect-square w-full flex-col items-center justify-center gap-3 rounded-3xl bg-gray-100 px-6 text-center">
        <CameraOffIcon className="h-10 w-10 text-gray-400" />
        <p className="text-sm font-semibold text-gray-600">{cameraError}</p>
      </div>
    )
  }

  return (
    <div className="relative aspect-square w-full overflow-hidden rounded-3xl bg-black">
      <video ref={videoRef} playsInline muted className="h-full w-full object-cover" />
      <canvas ref={canvasRef} className="hidden" />

      {/* 조준 가이드 — 어디에 QR을 맞춰야 하는지 알려준다 */}
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <div
          className={`h-3/5 w-3/5 rounded-2xl border-4 transition ${
            paused ? 'border-white/30' : 'border-white/80'
          }`}
        />
      </div>

      {!ready && (
        <p className="absolute inset-x-0 bottom-4 text-center text-sm font-semibold text-white/80">
          카메라를 켜는 중…
        </p>
      )}
    </div>
  )
}

export default QrScanner
