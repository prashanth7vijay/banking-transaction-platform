import { clsx } from 'clsx';
import type { ExceptionPriority } from '@/features/exceptions/types';

const styles: Record<ExceptionPriority, string> = {
  LOW: 'bg-slate-500/10 text-slate-600',
  MEDIUM: 'bg-amber-500/10 text-amber-600',
  HIGH: 'bg-orange-500/10 text-orange-600',
  CRITICAL: 'bg-red-600/15 text-red-600',
};

export function PriorityBadge({ priority }: { priority: ExceptionPriority }) {
  return (
    <span className={clsx('rounded-full px-2.5 py-0.5 text-xs font-medium', styles[priority])}>
      {priority}
    </span>
  );
}
