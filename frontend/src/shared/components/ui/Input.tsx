import { type InputHTMLAttributes, forwardRef } from 'react';
import { clsx } from 'clsx';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      className={clsx(
        'w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary',
        className
      )}
      {...props}
    />
  )
);
Input.displayName = 'Input';
