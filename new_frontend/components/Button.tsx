'use client'

interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost';
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
}

export function Button({ variant = 'primary', children, onClick, className = '' }: ButtonProps) {
  const baseClasses = 'button px-6 py-3 text-sm rounded-2xl flex items-center justify-center gap-2 shadow-sm font-medium transition-all duration-200';
  
  const variantClasses = {
    primary: 'bg-[var(--color-primary)] text-white hover:bg-[#2b5f8e] active:scale-95',
    secondary: 'bg-[var(--color-accent)] text-[var(--color-dark)] hover:bg-[#d4e0f0] active:scale-95',
    ghost: 'hover:bg-[var(--color-accent)] text-[var(--color-text)]'
  };

  return (
    <button 
      className={`${baseClasses} ${variantClasses[variant]} ${className}`}
      onClick={onClick}
    >
      {children}
    </button>
  );
}