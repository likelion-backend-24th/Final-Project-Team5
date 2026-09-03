import styles from './Badge.module.css'

/**
 * @param {{ variant?: 'accent' | 'secondary' | 'danger', className?: string, children: React.ReactNode }} props
 */
function Badge({ variant = 'accent', className = '', children }) {
  return (
    <span className={`${styles.badge} ${styles[variant]} ${className}`.trim()}>
      {children}
    </span>
  )
}

export default Badge
