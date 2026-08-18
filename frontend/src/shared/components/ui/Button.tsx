import { type ButtonHTMLAttributes, forwardRef } from 'react';
import { clsx } from 'clsx';

type Variant = 'default' | 'ghost' | 'destructive' | 'outline';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

const variantClasses: Record<Variant, string> = {
  default: 'bg-primary text-primary-foreground hover:opacity-90',
  ghost: 'hover:bg-muted',
  destructive: 'bg-red-600 text-white hover:bg-red-700',
  outline: 'border border-border hover:bg-muted',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'default', ...props }, ref) => (
    <button
      ref={ref}
      className={clsx(
        'inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition-colors disabled:opacity-50 disabled:pointer-events-none',
        variantClasses[variant],
        className
      )}
      {...props}
    />
  )
);
Button.displayName = 'Button';
