import { CATEGORIES } from '../data/festivals'

function CategoryChips({ value, onChange, categories = CATEGORIES }) {
  return (
    <div className="flex flex-wrap gap-2" role="tablist" aria-label="카테고리 필터">
      {categories.map((category) => {
        const selected = category.id === value
        return (
          <button
            key={category.id}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(category.id)}
            className={
              'rounded-full px-5 py-2.5 text-sm font-semibold transition ' +
              (selected
                ? 'bg-blue-600 text-white'
                : 'border border-gray-200 bg-white text-gray-700 hover:bg-gray-50')
            }
          >
            {category.label}
          </button>
        )
      })}
    </div>
  )
}

export default CategoryChips