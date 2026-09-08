const cardClass = 'rounded-3xl border border-gray-200 bg-white p-6 shadow-sm md:p-8'

function MyPageInfoTab({ user }) {
  const rows = [
    { label: '닉네임', value: user.nickname },
    { label: '이메일 (username)', value: user.username },
    { label: '이름', value: user.name },
    { label: '가입일', value: user.createdAt?.slice(0, 10) },
  ]

  return (
    <section className={cardClass}>
      <h2 className="text-lg font-extrabold text-gray-900">내 정보</h2>
      <dl className="mt-5 divide-y divide-gray-100">
        {rows.map((r) => (
          <div key={r.label} className="flex items-center justify-between py-4">
            <dt className="text-sm font-bold text-gray-500">{r.label}</dt>
            <dd className="text-[15px] font-medium text-gray-900">{r.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

export default MyPageInfoTab