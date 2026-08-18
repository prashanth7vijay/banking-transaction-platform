import { clsx } from 'clsx';
import type { SlaStatus } from '@/features/transactions/types';

const styles: Record<SlaStatus, string> = {
  WITHIN: 'bg-green-500/10 text-green-600',
  AT_RISK: 'bg-amber-500/10 text-amber-600',
  BREACHED: 'bg-red-500/10 text-red-500',
};

const labels: Record<SlaStatus, string> = {
  WITHIN: 'Within SLA',
  AT_RISK: 'At Risk',
  BREACHED: 'Breached',
};

function formatRemaining(slaDueAt: string): string {
  const diffMs = new Date(slaDueAt).getTime() - Date.now();
  const overdue = diffMs < 0;
  const abs = Math.abs(diffMs);
  const hours = Math.floor(abs / (1000 * 60 * 60));
  const minutes = Math.floor((abs % (1000 * 60 * 60)) / (1000 * 60));
  const label = hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
  return overdue ? `${label} overdue` : `${label} remaining`;
}

export function SlaBadge({ status, slaDueAt }: { status: SlaStatus; slaDueAt: string | null }) {
  return (
    <span className={clsx('rounded-full px-2.5 py-0.5 text-xs font-medium', styles[status])}>
      {labels[status]}
      {slaDueAt && <> &middot; {formatRemaining(slaDueAt)}</>}
    </span>
  );
}
