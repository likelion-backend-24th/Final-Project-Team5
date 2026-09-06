function Badge({ variant = 'accent', className = '', children }) {
  const variants = {
    accent: 'inline-flex items-center rounded-full bg-blue-600/90 px-2.5 py-1 text-xs font-bold text-white',
    secondary: 'inline-block rounded-md bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600',
    danger: 'inline-flex items-center rounded-full bg-red-500 px-2.5 py-1 text-xs font-bold text-white',
  }

  return <span className={`${variants[variant]} ${className}`.trim()}>{children}</span>
}

export default Badge