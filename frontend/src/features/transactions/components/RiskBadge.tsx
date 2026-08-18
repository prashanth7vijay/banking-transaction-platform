import { clsx } from 'clsx';
import type { RiskLevel } from '@/features/transactions/types';

const styles: Record<RiskLevel, string> = {
  LOW: 'bg-green-500/10 text-green-600',
  MEDIUM: 'bg-amber-500/10 text-amber-600',
  HIGH: 'bg-red-500/10 text-red-500',
  CRITICAL: 'bg-red-600/15 text-red-600',
};

export function RiskBadge({ level }: { level: RiskLevel | null }) {
  if (!level) return null;
  return (
    <span className={clsx('rounded-full px-2.5 py-0.5 text-xs font-medium', styles[level])}>
      {level} risk
    </span>
  );
}
