import { type ReactNode } from 'react';
import { clsx } from 'clsx';

interface AlertProps {
  variant?: 'error' | 'success';
  children: ReactNode;
}

export function Alert({ variant = 'error', children }: AlertProps) {
  return (
    <div
      className={clsx(
        'rounded-md px-3 py-2 text-sm mb-4',
        variant === 'error' && 'bg-red-500/10 text-red-500 border border-red-500/20',
        variant === 'success' && 'bg-green-500/10 text-green-500 border border-green-500/20'
      )}
    >
      {children}
    </div>
  );
}
