import { clsx } from 'clsx';
import type { ExceptionStatus } from '@/features/exceptions/types';

const styles: Record<ExceptionStatus, string> = {
  OPEN: 'bg-red-500/10 text-red-500',
  ASSIGNED: 'bg-blue-500/10 text-blue-600',
  INVESTIGATING: 'bg-indigo-500/10 text-indigo-600',
  ACTION_REQUIRED: 'bg-amber-500/10 text-amber-600',
  RESOLVED: 'bg-green-500/10 text-green-600',
  CLOSED: 'bg-slate-500/10 text-slate-500',
};

export function ExceptionStatusBadge({ status }: { status: ExceptionStatus }) {
  return (
    <span className={clsx('rounded-full px-2.5 py-0.5 text-xs font-medium', styles[status])}>
      {status.replace('_', ' ')}
    </span>
  );
}
