import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getCommandCenterSummary } from '@/features/dashboard/api/dashboardApi';
import { getReconciliationCheck } from '@/features/admin/api/adminApi';
import { MetricCard } from '@/features/dashboard/components/MetricCard';
import { Button } from '@/shared/components/ui/Button';
import { formatCurrency } from '@/shared/lib/format';
import { useAuth } from '@/features/auth/AuthContext';

function formatPercent(value: number | null): string {
  return value == null ? '—' : `${Math.round(value * 100)}%`;
}

function formatMinutes(value: number | null): string {
  if (value == null) return '—';
  if (value < 60) return `${Math.round(value)}m`;
  return `${(value / 60).toFixed(1)}h`;
}

function ReconciliationPanel() {
  const [result, setResult] = useState<Awaited<ReturnType<typeof getReconciliationCheck>> | null>(null);
  const [loading, setLoading] = useState(false);

  const runCheck = async () => {
    setLoading(true);
    try {
      setResult(await getReconciliationCheck());
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="border border-border rounded-lg p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
          Ledger Reconciliation (Admin)
        </h2>
        <Button variant="outline" onClick={runCheck} disabled={loading}>
          {loading ? 'Checking...' : 'Run Check'}
        </Button>
      </div>
      {result && (
        <div className="text-sm space-y-2">
          <p className="text-muted-foreground">
            {result.accountsChecked} accounts checked ·{' '}
            {result.mismatches.length === 0 ? (
              <span className="text-green-600 font-medium">no mismatches</span>
            ) : (
              <span className="text-red-500 font-medium">{result.mismatches.length} mismatch(es)</span>
            )}
          </p>
          {result.mismatches.map((m) => (
            <div key={m.accountId} className="text-xs border-t border-border pt-2">
              Account {m.accountId.slice(0, 8)}… · ledger {formatCurrency(m.ledgerBalance)} vs. actual{' '}
              {formatCurrency(m.actualBalance)} (Δ {formatCurrency(m.difference)})
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function CommandCenterPage() {
  const { user } = useAuth();
  const isAdmin = user?.roles.includes('ADMIN');

  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', 'command-center'],
    queryFn: getCommandCenterSummary,
    refetchInterval: 30_000,
  });

  if (isLoading) return <p className="text-muted-foreground">Loading...</p>;
  if (!data) return <p className="text-red-500 text-sm">Could not load the command center.</p>;

  const { transactions: tx, exceptions: exc } = data;

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-semibold">Operations Command Center</h1>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Transaction Health — Today</h2>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
          <MetricCard label="Total Transactions" value={tx.totalToday} to="/transactions/by-status?status=SUBMITTED" />
          <MetricCard label="Total Value" value={formatCurrency(tx.totalValueToday)} />
          <MetricCard
            label="Completed"
            value={tx.completedToday}
            to="/transactions/by-status?status=COMPLETED"
            tone="success"
          />
          <MetricCard
            label="Failed"
            value={tx.failedToday}
            to="/transactions/by-status?status=FAILED"
            tone={tx.failedToday > 0 ? 'danger' : 'default'}
          />
          <MetricCard label="Success Rate" value={formatPercent(tx.successRateToday)} />
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Work Requiring Attention</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <MetricCard label="Pending Approvals" value={tx.pendingApprovalCount} to="/approvals" />
          <MetricCard
            label="High-Risk Pending"
            value={tx.highRiskPendingCount}
            to="/approvals"
            tone={tx.highRiskPendingCount > 0 ? 'warning' : 'default'}
          />
          <MetricCard label="Open Exceptions" value={exc.openCount} to="/exceptions" />
          <MetricCard
            label="Approval SLA Breached"
            value={tx.slaBreachedCount}
            to="/approvals"
            tone={tx.slaBreachedCount > 0 ? 'danger' : 'default'}
          />
          <MetricCard
            label="Approval SLA At Risk"
            value={tx.slaAtRiskCount}
            to="/approvals"
            tone={tx.slaAtRiskCount > 0 ? 'warning' : 'default'}
          />
          <MetricCard
            label="Exception SLA Breached"
            value={exc.slaBreachedCount}
            to="/exceptions"
            tone={exc.slaBreachedCount > 0 ? 'danger' : 'default'}
          />
          <MetricCard
            label="Exception SLA At Risk"
            value={exc.slaAtRiskCount}
            to="/exceptions"
            tone={exc.slaAtRiskCount > 0 ? 'warning' : 'default'}
          />
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Operational Performance</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
          <MetricCard label="Avg. Approval Time" value={formatMinutes(tx.averageApprovalMinutes)} />
          <MetricCard label="Avg. Exception Resolution" value={formatMinutes(exc.averageResolutionMinutes)} />
          <MetricCard label="Approval SLA Compliance" value={formatPercent(tx.slaComplianceRate)} />
        </div>
      </section>

      {isAdmin && <ReconciliationPanel />}
    </div>
  );
}
