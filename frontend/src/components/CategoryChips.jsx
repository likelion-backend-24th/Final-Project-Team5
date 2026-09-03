import { CATEGORIES } from '../data/festivals'
import styles from './CategoryChips.module.css'

/**
 * 카테고리 필터 칩. 가로로 넘치면 스크롤된다.
 * @param {{ value: string, onChange: (id: string) => void, categories?: typeof CATEGORIES }} props
 */
function CategoryChips({ value, onChange, categories = CATEGORIES }) {
  return (
    <div className={styles.chips} role="tablist" aria-label="카테고리 필터">
      {categories.map((category) => {
        const selected = category.id === value
        return (
          <button
            key={category.id}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(category.id)}
            className={`${styles.chip} ${selected ? styles.chipSelected : ''}`}
          >
            {category.label}
          </button>
        )
      })}
    </div>
  )
}

export default CategoryChips
