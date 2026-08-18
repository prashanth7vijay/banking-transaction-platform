import { type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { clsx } from 'clsx';

interface MetricCardProps {
  label: string;
  value: ReactNode;
  sublabel?: ReactNode;
  to?: string;
  tone?: 'default' | 'warning' | 'danger' | 'success';
}

const toneStyles: Record<NonNullable<MetricCardProps['tone']>, string> = {
  default: '',
  warning: 'border-amber-500/30',
  danger: 'border-red-500/30',
  success: 'border-green-500/30',
};

export function MetricCard({ label, value, sublabel, to, tone = 'default' }: MetricCardProps) {
  const content = (
    <div className={clsx('border border-border rounded-lg p-4 space-y-1 h-full', toneStyles[tone], to && 'hover:bg-muted transition-colors')}>
      <p className="text-xs text-muted-foreground uppercase tracking-wide">{label}</p>
      <p className="text-2xl font-semibold">{value}</p>
      {sublabel && <p className="text-xs text-muted-foreground">{sublabel}</p>}
    </div>
  );

  return to ? <Link to={to}>{content}</Link> : content;
}
