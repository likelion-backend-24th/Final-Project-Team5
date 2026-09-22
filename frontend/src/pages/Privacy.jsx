const SECTIONS = [
  {
    heading: '1. 수집하는 개인정보 항목',
    body: [
      { type: 'p', text: '회사는 서비스 제공을 위해 다음과 같은 개인정보를 수집합니다.' },
      {
        type: 'ul',
        items: [
          '필수: 이름, 이메일(아이디), 비밀번호(암호화 저장), 닉네임',
          '주최자 신청 시: 주최자명, 소개, 연락처',
          '페스티벌 등록 시: 페스티벌명, 일정, 장소, 카테고리, 티켓 종류·가격·수량 등 등록 정보',
          '예매·결제 이용 시: 예매 내역 및 이용 기간, 결제 식별자·수단·금액·상태, 취소·환불 내역',
          '서비스 이용 과정에서 자동 생성되는 정보: 접속 로그, 주최자 신청·페스티벌 등록 심사 이력',
        ],
      },
    ],
  },
  {
    heading: '2. 개인정보의 수집 및 이용 목적',
    body: [
      {
        type: 'ul',
        items: [
          '회원 가입 의사 확인, 본인 인증, 회원 관리',
          '페스티벌 정보 제공, 주최자 신청 및 페스티벌 등록 심사, 예매 등 서비스 제공 및 운영',
          '예매 신청·결제·취소·환불 처리 및 관련 안내',
          '부정 이용 방지, 심사 처리 등 서비스 안정성 확보',
        ],
      },
    ],
  },
  {
    heading: '3. 개인정보의 보유 및 이용 기간',
    body: [
      {
        type: 'p',
        text: '회사는 원칙적으로 회원 탈퇴 시 지체 없이 개인정보를 파기합니다. 다만 주최자가 등록한 페스티벌 정보와 회원의 예매 이력은 탈퇴 이후에도 서비스 운영(신고 처리, 관리자 조회 등) 목적으로 일정 기간 보관될 수 있으며, 이 경우 작성자 정보는 "탈퇴한 사용자"로 대체 표기됩니다.',
      },
      { type: 'p', text: '관계 법령에 따라 보존이 필요한 정보는 해당 법령이 정한 기간 동안 보관합니다.' },
    ],
  },
  {
    heading: '4. 개인정보의 제3자 제공',
    body: [
      {
        type: 'p',
        text: '회사는 회원의 동의 없이 개인정보를 제3자에게 제공하지 않습니다. 다만 법령에 특별한 규정이 있는 경우는 예외로 합니다.',
      },
    ],
  },
  {
    heading: '5. 개인정보 처리 위탁',
    body: [
      {
        type: 'p',
        text: '회사는 이메일 인증코드 발송 등 일부 업무를 외부 이메일 발송 서비스에 위탁할 수 있으며, 위탁 계약 체결 시 관계 법령에 따라 개인정보가 안전하게 관리되도록 필요한 사항을 규정합니다.',
      },
    ],
  },
  {
    heading: '6. 정보주체의 권리와 행사 방법',
    body: [
      {
        type: 'p',
        text: '회원은 마이페이지에서 언제든지 본인의 닉네임·비밀번호 등 개인정보를 조회·수정할 수 있으며, 회원 탈퇴를 통해 개인정보 처리 정지 및 삭제를 요청할 수 있습니다.',
      },
    ],
  },
  {
    heading: '7. 개인정보의 파기',
    body: [
      {
        type: 'p',
        text: '보유 기간이 경과하거나 처리 목적이 달성된 개인정보는 지체 없이 파기합니다. 전자적 파일 형태의 정보는 복구할 수 없는 방법으로 영구 삭제합니다.',
      },
    ],
  },
  {
    heading: '8. 개인정보 보호책임자',
    body: [
      {
        type: 'p',
        text: '회사는 개인정보 처리에 관한 문의, 불만 처리 등을 위해 개인정보 보호책임자를 지정하여 운영합니다. 문의는 서비스 내 고객센터 채널을 통해 접수해주세요.',
      },
    ],
  },
]

/** 개인정보처리방침 전문. 정식 시행 전이라 법률 검토 전 초안임을 상단에 안내한다. */
function Privacy() {
  return (
    <main className="mx-auto max-w-[1440px] px-6 pt-10 pb-20 sm:pt-14 sm:pb-28">
      <div className="mx-auto max-w-3xl">
        <div className="mb-8 rounded-lg border border-amber-200 bg-amber-50 px-5 py-4 text-sm leading-relaxed text-amber-800">
          이 문서는 서비스 준비 과정에서 작성된 초안이며, 정식 시행 전 법률 검토를 거쳐 내용이 변경될 수 있습니다.
        </div>

        <h1 className="text-3xl font-bold text-gray-900">개인정보처리방침</h1>
        <p className="mt-2 text-sm text-gray-500">시행일 2026-09-21</p>

        <div className="mt-10 space-y-10">
          {SECTIONS.map((section) => (
            <section key={section.heading}>
              <h2 className="font-bold text-gray-900">{section.heading}</h2>
              <div className="mt-3 space-y-2">
                {section.body.map((block) =>
                  block.type === 'ul' ? (
                    <ul key={block.items.join('|')} className="list-disc space-y-1 pl-5">
                      {block.items.map((item) => (
                        <li key={item} className="text-sm leading-relaxed text-gray-700">
                          {item}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p key={block.text} className="text-sm leading-relaxed text-gray-700">
                      {block.text}
                    </p>
                  ),
                )}
              </div>
            </section>
          ))}
        </div>
      </div>
    </main>
  )
}

export default Privacy
