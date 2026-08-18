import { clsx } from 'clsx';
import type { TransactionStatus } from '@/features/transactions/types';

const styles: Record<TransactionStatus, string> = {
  DRAFT: 'bg-slate-500/10 text-slate-600',
  SUBMITTED: 'bg-blue-500/10 text-blue-600',
  PENDING_APPROVAL: 'bg-yellow-500/10 text-yellow-600',
  APPROVED: 'bg-sky-500/10 text-sky-600',
  PROCESSING: 'bg-indigo-500/10 text-indigo-600',
  COMPLETED: 'bg-green-500/10 text-green-600',
  REJECTED: 'bg-red-500/10 text-red-500',
  FAILED: 'bg-red-500/10 text-red-500',
  CANCELLED: 'bg-slate-500/10 text-slate-500',
};

export function StatusBadge({ status }: { status: TransactionStatus }) {
  return (
    <span className={clsx('rounded-full px-2.5 py-0.5 text-xs font-medium', styles[status])}>
      {status.replace('_', ' ')}
    </span>
  );
}
