import { useCallback, useState } from 'react'

/**
 * 로드가 끝나면 부드럽게 나타나는 <img>. 부모가 깔아둔 배경색(placeholder)이 먼저 보이다가 이미지로 바뀐다.
 * - 기본으로 loading="lazy" + decoding="async". 첫 화면 이미지는 loading="eager" fetchPriority="high"로 넘긴다.
 * - 이미 브라우저 캐시에 있어 마운트 시점에 로드가 끝난 이미지는 페이드 없이 바로 보인다(재방문마다 깜빡이지 않게).
 * - 로드 실패 시에도 alt 텍스트가 보이도록 숨김을 풀어준다.
 * 페이드는 transition이 아니라 애니메이션(animate-fade-in)이라 호출부의 hover transition과 겹치지 않는다.
 */
function FadeImage({ src, className = '', loading = 'lazy', onLoad, onError, ...rest }) {
  //src가 바뀌면 다시 처음부터(숨김) 시작하도록 어떤 src가 준비됐는지를 함께 기억한다.
  const [ready, setReady] = useState({ src: null, instant: false })
  const visible = ready.src === src
  const animate = visible && !ready.instant

  const handleRef = useCallback(
    (node) => {
      if (node && node.complete && node.naturalWidth > 0) {
        setReady((prev) => (prev.src === src ? prev : { src, instant: true }))
      }
    },
    [src],
  )

  return (
    <img
      {...rest}
      ref={handleRef}
      src={src}
      loading={loading}
      decoding="async"
      className={`${className} ${visible ? '' : 'opacity-0'} ${animate ? 'animate-fade-in motion-reduce:animate-none' : ''}`}
      onLoad={(event) => {
        setReady({ src, instant: false })
        onLoad?.(event)
      }}
      onError={(event) => {
        setReady({ src, instant: true })
        onError?.(event)
      }}
    />
  )
}

export default FadeImage
